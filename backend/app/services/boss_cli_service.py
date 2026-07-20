"""Boss CLI service — wraps @joohw/boss-cli as subprocess.

All commands run via subprocess.run(['boss', ...]) with timeouts.
Output is parsed into structured dicts. Times out after configurable seconds
(default: 120s) because browser automation is inherently slow.

Design:
    - Every public function returns {"ok": True/False, ...}
    - All commands and results are logged
    - boss-cli must be installed globally and `boss login` completed beforehand
    - This is NOT a Python module import — it's a Node.js CLI, called via subprocess
"""

import json
import logging
import os
import shutil
import subprocess
import time
from typing import Optional

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration helpers
# ---------------------------------------------------------------------------

def _get_timeout() -> int:
    try:
        from config import Config
        return Config.BOSS_CLI_TIMEOUT
    except Exception:
        return 120


def _get_enabled() -> bool:
    try:
        from config import Config
        return Config.BOSS_CLI_ENABLED
    except Exception:
        return True


# ---------------------------------------------------------------------------
# Availability check
# ---------------------------------------------------------------------------

def is_available() -> bool:
    """Check if the boss CLI is installed and executable."""
    if not _get_enabled():
        log.info("boss-cli disabled via BOSS_CLI_ENABLED config")
        return False
    path = shutil.which("boss")
    if path and path.lower().endswith('.cmd'):
        return True
    return path is not None


def login() -> dict:
    """Open the BOSS login page in a browser via ``boss login``.

    Returns immediately after launching the browser; does not wait for
    the user to complete login.  Check ``GET /api/boss/status`` afterwards.
    """
    if not is_available():
        return {"ok": False, "error": "boss-cli 未安装或未启用，请先执行 npm i -g @joohw/boss-cli"}
    try:
        result = _run(["login"], timeout=30)
        return {
            "ok": True,
            "login_url": "https://www.zhipin.com/web/user/",
            "output": (result.stdout or "").strip(),
        }
    except subprocess.TimeoutExpired:
        # login opens the browser and hangs; timeout is expected
        return {
            "ok": True,
            "login_url": "https://www.zhipin.com/web/user/",
            "output": "browser launched (timeout expected)",
        }
    except FileNotFoundError:
        return {"ok": False, "error": "boss-cli 未安装。请执行: npm i -g @joohw/boss-cli"}
    except Exception as e:
        return {"ok": False, "error": str(e)}

def is_logged_in() -> dict:
    """Quick check: can boss-cli list positions? If yes, login is active."""
    if not is_available():
        return {"ok": False, "logged_in": False, "error": "unavailable"}
    try:
        # boss list is a lightweight read — if it works, we're logged in
        result = _run(["list"], timeout=15)
        return {"ok": True, "logged_in": result.returncode == 0, "output": (result.stdout or "")[:200]}
    except Exception:
        return {"ok": False, "logged_in": False, "error": "check failed"}


# ---------------------------------------------------------------------------
# Core subprocess runner
# ---------------------------------------------------------------------------

def _run(args: list, timeout: int = None, check: bool = False) -> subprocess.CompletedProcess:
    """Run a boss CLI command and return the CompletedProcess.

    Args:
        args: Command args after 'boss', e.g. ['positions'].
        timeout: Seconds before kill (default: BOSS_CLI_TIMEOUT, 120s).
        check: If True, raise CalledProcessError on non-zero exit.

    Returns:
        subprocess.CompletedProcess with .stdout, .stderr, .returncode.
    """
    if timeout is None:
        timeout = _get_timeout()

    cmd = ["boss"] + args
    cmd_str = " ".join(cmd)
    log.info("boss-cli executing: %s (timeout=%ds)", cmd_str, timeout)

    t0 = time.time()
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            cwd=os.getcwd(),
            shell=True,  # Windows: .CMD files need shell execution
        )
        elapsed = time.time() - t0
        log.info(
            "boss-cli completed: %s rc=%d elapsed=%.1fs stdout=%d stderr=%d",
            cmd_str,
            result.returncode,
            elapsed,
            len(result.stdout or ""),
            len(result.stderr or ""),
        )
        if check and result.returncode != 0:
            raise subprocess.CalledProcessError(
                result.returncode, cmd, output=result.stdout, stderr=result.stderr
            )
        return result
    except subprocess.TimeoutExpired:
        elapsed = time.time() - t0
        log.error("boss-cli timed out after %.1fs: %s", elapsed, cmd_str)
        raise
    except FileNotFoundError:
        log.error("boss-cli not found on PATH. Install: npm i -g @joohw/boss-cli")
        raise
    except Exception as exc:
        log.warning("boss-cli unexpected error: %s", cmd_str, exc_info=True)
        raise


def _run_json(args: list, timeout: int = None) -> dict:
    """Run a boss CLI command and return {"ok": True, "data": ..., ...}."""
    if not is_available():
        return {"ok": False, "error": "boss-cli 未安装或未启用，请先执行 npm i -g @joohw/boss-cli && boss login"}
    try:
        result = _run(args, timeout=timeout)
        stdout = (result.stdout or "").strip()
        return {
            "ok": True,
            "data": _parse_stdout(stdout),
            "_raw": stdout,
            "_returncode": result.returncode,
            "_stderr": (result.stderr or "").strip(),
        }
    except FileNotFoundError:
        return {"ok": False, "error": "boss-cli 未安装。请执行: npm i -g @joohw/boss-cli"}
    except subprocess.TimeoutExpired:
        return {"ok": False, "error": f"boss-cli 命令超时 (>{_get_timeout()}s)，浏览器自动化操作较慢，请重试"}
    except subprocess.CalledProcessError as e:
        return {"ok": False, "error": f"boss-cli 执行失败 (exit={e.returncode}): {e.stderr or e.stdout}"}
    except Exception as e:
        log.exception("boss-cli command failed")
        return {"ok": False, "error": str(e)}


def _parse_stdout(stdout: str):
    """Best-effort: try JSON, then return raw text."""
    if not stdout:
        return ""
    try:
        return json.loads(stdout)
    except (json.JSONDecodeError, TypeError):
        return stdout


# ---------------------------------------------------------------------------
# Position management
# ---------------------------------------------------------------------------

def get_positions() -> list:
    """Get position list from BOSS.

    Returns:
        If ok: {"ok": True, "data": [...]}
        If not: {"ok": False, "error": "..."}
    """
    result = _run_json(["positions"])
    if result["ok"] and isinstance(result.get("data"), str):
        # boss positions outputs a table-like text; parse lines
        parsed = _parse_positions_table(result["data"])
        result["data"] = parsed
    return result


def _parse_positions_table(text: str) -> list:
    """Parse `boss positions` text output into structured list."""
    if not text:
        return []
    lines = text.strip().split("\n")
    positions = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        # boss positions outputs lines like "序号 名称  状态"
        # Parse: extract name and status
        parts = line.split()
        if len(parts) >= 2:
            # Try to identify status keywords
            status = "unknown"
            name_parts = []
            for part in parts:
                if part in ("开放", "待开放", "已关闭", "open", "pending", "closed"):
                    status = part
                elif part.isdigit() and len(part) <= 2:
                    continue  # skip index numbers
                else:
                    name_parts.append(part)
            name = " ".join(name_parts)
            if name:
                positions.append({"name": name, "status": status, "_raw": line})
    return positions


def get_position_detail(name: str) -> dict:
    """Fetch position JD detail and cache as .md file.

    Returns:
        {"ok": True, "data": {"name": ..., "jd_text": ..., "cache_file": ...}}
    """
    return _run_json(["jd", name])


# ---------------------------------------------------------------------------
# Candidate search
# ---------------------------------------------------------------------------

def search_candidates(
    keyword: str = None,
    job: str = None,
    core: list = None,
    bonus: list = None,
    match: bool = False,
) -> dict:
    """Search candidates via boss search or boss deep-search.

    If any of job/core/bonus/match is provided, uses deep-search.
    Otherwise, uses basic search.

    Returns:
        {"ok": True, "data": [...]}
    """
    has_deep = job or core or bonus or match
    if has_deep:
        args = ["deep-search"]
        if keyword:
            args.append(keyword)
        if job:
            args.extend(["--job", job])
        if core:
            for c in core:
                args.extend(["--core", c])
        if bonus:
            for b in bonus:
                args.extend(["--bonus", b])
        if match:
            args.append("--match")
        return _run_json(args)
    else:
        args = ["search"]
        if keyword:
            args.append(keyword)
        return _run_json(args)


# ---------------------------------------------------------------------------
# Chat management
# ---------------------------------------------------------------------------

def get_chat_list(unread_only: bool = False) -> list:
    """Get chat list from BOSS.

    Returns:
        {"ok": True, "data": [{"name": ..., "unread": ...}, ...]}
    """
    args = ["list"]
    if unread_only:
        args.append("--unread")
    result = _run_json(args)
    if result["ok"] and isinstance(result.get("data"), str):
        parsed = _parse_chat_list(result["data"])
        result["data"] = parsed
    return result


def _parse_chat_list(text: str) -> list:
    """Parse `boss list` text output into structured list."""
    if not text:
        return []
    lines = text.strip().split("\n")
    chats = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        # Very simple parse: extract name, any number might be unread count
        chats.append({"name": line, "unread": 0, "_raw": line})
    return chats


def open_chat(name: str, index: int = None, unread: bool = False, strict: bool = False) -> dict:
    """Open a conversation with a candidate.

    Args:
        name: Candidate name to search for.
        index: Optional 1-based index from `boss list`.
        unread: If True, index refers to unread-only list.
        strict: If True, exact name match.

    Returns:
        {"ok": True, "data": {...}}
    """
    args = ["chat", name]
    if index is not None:
        args.extend(["--index", str(index)])
    if unread:
        args.append("--unread")
    if strict:
        args.append("--strict")
    return _run_json(args)


def send_message(text: str, request_resume: bool = False) -> dict:
    """Send a text message in the current chat.

    Args:
        text: Message content to send.
        request_resume: If True, also request resume attachment after sending.

    Returns:
        {"ok": True, "data": {...}}
    """
    args = ["send", "--text", text]
    if request_resume:
        args.append("--request-resume")
    return _run_json(args)


# ---------------------------------------------------------------------------
# Actions
# ---------------------------------------------------------------------------

VALID_ACTIONS = {
    "resume", "not-fit", "remark", "agree-resume",
    "request-attachment-resume", "history", "wechat",
}


def do_action(action: str, remark: str = None) -> dict:
    """Execute an action on the current candidate.

    Must be on a chat page with candidate detail open.

    Args:
        action: One of resume|not-fit|remark|agree-resume|
                request-attachment-resume|history|wechat
        remark: Required remark text when action='remark'.

    Returns:
        {"ok": True, "data": {...}}
    """
    if action not in VALID_ACTIONS:
        return {
            "ok": False,
            "error": f"无效操作: {action}。支持: {', '.join(sorted(VALID_ACTIONS))}",
        }
    args = ["action", action]
    if remark:
        if action != "remark":
            log.warning("--remark provided but action is %s, ignoring", action)
        else:
            args.extend(["--remark", remark])
    return _run_json(args)


# ---------------------------------------------------------------------------
# Resume preview
# ---------------------------------------------------------------------------

def preview_resume(name: str) -> dict:
    """Preview a candidate's online resume.

    NOTE: Must be on recommend/search/deep-search page with list loaded first.
    Daily views are limited — use sparingly.

    Returns:
        {"ok": True, "data": {...}}
    """
    return _run_json(["preview", name])


# ---------------------------------------------------------------------------
# Greeting
# ---------------------------------------------------------------------------

def greet_candidate(name: str, job: str = None) -> dict:
    """Greet a candidate from the current recommend/deep-search list.

    NOTE: Consumes greeting quota. Costs are high. Use carefully.

    Args:
        name: Candidate name to greet.
        job: Optional job keyword to switch position before greeting.

    Returns:
        {"ok": True, "data": {...}}
    """
    args = ["greet", name]
    if job:
        args.extend(["--job", job])
    return _run_json(args)
