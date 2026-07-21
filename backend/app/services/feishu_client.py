"""Feishu / Lark integration client for the recruitment system.

Uses the Feishu Open API via HTTP (``requests``) for production deployments.
No ``lark-cli`` dependency — works cross-platform with the same credential
story: ``FEISHU_APP_ID`` + ``FEISHU_APP_SECRET`` from environment variables.

Bot-mode is the primary path for notification cards and reminders.
MOCK_MODE (default True) keeps everything running without a real tenant.
Set ``FEISHU_MOCK_MODE=false`` to talk to the real Feishu Open API.

Usage in production::

    # Required env vars
    export FEISHU_APP_ID=cli_xxx
    export FEISHU_APP_SECRET=your-secret
    export FEISHU_MOCK_MODE=false
    export FEISHU_RECIPIENT_OPEN_IDS='{"张三面试官":"ou_xxx","李四面试官":"ou_yyy"}'
"""

import json
import logging
import os
import time
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

import requests

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
FEISHU_API_BASE = os.getenv("FEISHU_API_BASE", "https://open.feishu.cn/open-apis")

# Token cache (module-level — cleared on process restart)
_TOKEN_URL = f"{FEISHU_API_BASE}/auth/v3/tenant_access_token/internal"
_TOKEN_CACHE: Dict[str, Any] = {"token": None, "expires_at": 0}

# Message endpoint (bot-mode)
_SEND_MSG_URL = f"{FEISHU_API_BASE}/im/v1/messages"

# ---------------------------------------------------------------------------
# Mock mode — read from env, default True so dev environments work out of the box
# ---------------------------------------------------------------------------
MOCK_MODE = os.getenv("FEISHU_MOCK_MODE", "true").strip().lower() in ("true", "1", "yes")

# ---------------------------------------------------------------------------
# Recipient open_ids lookup table — loaded from FEISHU_RECIPIENT_OPEN_IDS env var.
# JSON map of user name → open_id, e.g.: {"张三":"ou_xxx","李四":"ou_yyy"}
# ---------------------------------------------------------------------------
_RECIPIENT_OPEN_IDS: Dict[str, str] = {}
_raw_recipient = os.getenv("FEISHU_RECIPIENT_OPEN_IDS", "")
if _raw_recipient:
    try:
        _parsed = json.loads(_raw_recipient)
        if isinstance(_parsed, dict):
            _RECIPIENT_OPEN_IDS = _parsed
    except json.JSONDecodeError:
        logger.warning("FEISHU_RECIPIENT_OPEN_IDS is not valid JSON: %s", _raw_recipient[:120])


def get_recipient_open_id(name: str) -> Optional[str]:
    """Look up a recipient open_id by name from the env-var configured map."""
    return _RECIPIENT_OPEN_IDS.get(name)


# ============================================================================
# Internal helpers
# ============================================================================


def _get_app_credentials() -> tuple:
    """Return (app_id, app_secret) from env vars.  Raises RuntimeError if missing."""
    app_id = os.getenv("FEISHU_APP_ID", "")
    app_secret = os.getenv("FEISHU_APP_SECRET", "")
    if not app_id or not app_secret:
        raise RuntimeError(
            "FEISHU_APP_ID and FEISHU_APP_SECRET must be set in the environment "
            "to call the Feishu Open API.  Set FEISHU_MOCK_MODE=true to bypass."
        )
    return app_id, app_secret


def _get_tenant_access_token() -> str:
    """Return a cached tenant_access_token, refreshing if expired (or within 60s of expiry).

    Calls ``POST /open-apis/auth/v3/tenant_access_token/internal``.
    """
    now = time.time()
    if _TOKEN_CACHE["token"] and _TOKEN_CACHE["expires_at"] > now + 60:
        return _TOKEN_CACHE["token"]

    app_id, app_secret = _get_app_credentials()
    try:
        resp = requests.post(
            _TOKEN_URL,
            json={"app_id": app_id, "app_secret": app_secret},
            timeout=15,
        )
        resp.raise_for_status()
        body = resp.json()
        code = body.get("code", -1)
        if code != 0:
            raise RuntimeError(f"Failed to get tenant_access_token: code={code} msg={body.get('msg', '')}")

        _TOKEN_CACHE["token"] = body["tenant_access_token"]
        _TOKEN_CACHE["expires_at"] = now + body.get("expire", 7200)
        logger.debug("tenant_access_token refreshed, expires in %ds", body.get("expire", 7200))
        return _TOKEN_CACHE["token"]

    except requests.RequestException as exc:
        raise RuntimeError(f"Failed to get tenant_access_token (network/HTTP error): {exc}") from exc


def _feishu_get(path: str, params: dict = None) -> dict:
    """GET a Feishu Open API endpoint. Returns parsed JSON body."""
    token = _get_tenant_access_token()
    resp = requests.get(
        f"{FEISHU_API_BASE}{path}",
        params=params or {},
        headers={"Authorization": f"Bearer {token}"},
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()


def _feishu_post(path: str, json_body: dict, *, params: dict = None) -> dict:
    """POST to a Feishu Open API endpoint. Returns parsed JSON body."""
    token = _get_tenant_access_token()
    resp = requests.post(
        f"{FEISHU_API_BASE}{path}",
        params=params or {},
        json=json_body,
        headers={"Authorization": f"Bearer {token}"},
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()


# ============================================================================
# Public API
# ============================================================================


def health_check() -> Dict[str, Any]:
    """Quick connectivity check — verifies token acquisition works."""
    if MOCK_MODE:
        return {"status": "ok", "mode": "mock"}
    try:
        _get_tenant_access_token()
        return {"status": "ok", "mode": "real", "token_refreshed": True}
    except Exception as exc:
        return {"status": "error", "error": str(exc)}


# ── Video conference (VC) ────────────────────────────────────────────────


def create_vc_meeting(topic: str, start_time: str, duration_minutes: int = 60) -> Dict[str, Any]:
    """Create a Feishu video-conference meeting and return its join URL.

    Mock mode returns a deterministic-shaped fake URL.
    Real mode calls ``POST /open-apis/vc/v1/reserves/apply``; any failure
    raises so the caller can fall back to a locally-built URL.

    Returns:
        {"meeting_url": str, "meeting_code": str}
    """
    import random

    if MOCK_MODE:
        code = str(random.randint(100000000, 999999999))
        url = f"https://vc.feishu.cn/j/{code}"
        logger.info("[MOCK] create_vc_meeting topic=%s url=%s", topic, url)
        return {"meeting_url": url, "meeting_code": code}

    body = _feishu_post(
        "/vc/v1/reserves/apply",
        {
            "meeting_settings": {
                "topic": topic,
                "start_time": start_time,
                "duration": duration_minutes,
                "action_permissions": [],
                "meeting_initial_type": 1,
            },
            "end_meeting_at_once": False,
        },
    )
    code = body.get("code", -1)
    if code != 0:
        raise RuntimeError(f"vc reserves/apply failed: code={code} msg={body.get('msg', '')}")

    data = body.get("data", {}) or {}
    reserve = data.get("reserve", {}) or {}
    meeting_url = reserve.get("meeting_link") or reserve.get("url")
    meeting_code = reserve.get("meeting_no") or str(reserve.get("id", ""))
    if not meeting_url:
        raise RuntimeError(f"vc reserves/apply returned no meeting link: {data}")
    return {"meeting_url": meeting_url, "meeting_code": meeting_code}


# ── Contact / user search ─────────────────────────────────────────────────


def search_user(name: str) -> Dict[str, Any]:
    """Search for a user's open_id by display name.

    Requires ``contact:user:search`` scope (user identity).
    Falls back gracefully in bot-only mode.

    Returns:
        {"success": bool, "open_id": str|None, "users": list, "error": str|None}
    """
    if MOCK_MODE:
        logger.info("[MOCK] search_user: %s", name)
        return {
            "success": True,
            "open_id": None,
            "users": [
                {
                    "name": name,
                    "open_id": f"ou_mock_{name}",
                    "email": f"{name}@example.com",
                    "mobile": "13800138000",
                    "employee_id": "EMP0001",
                }
            ],
            "error": None,
        }

    try:
        body = _feishu_post(
            "/contact/v3/users/batch_get_id",
            {"include_resigned": False, "mobiles": [], "emails": []},
            params={"user_id_type": "open_id"},
        )
        code = body.get("code", -1)
        if code != 0:
            return {"success": False, "open_id": None, "users": [], "error": body.get("msg", str(code))}

        user_list = (body.get("data", {}) or {}).get("user_list") or []
        users = [
            {
                "name": u.get("name", name),
                "open_id": u.get("user_id", ""),
                "email": u.get("email", ""),
                "mobile": u.get("mobile", ""),
                "employee_id": u.get("employee_id", ""),
            }
            for u in user_list
        ]
        return {"success": True, "open_id": users[0]["open_id"] if users else None, "users": users, "error": None}

    except Exception as exc:
        logger.warning("search_user failed: %s", exc)
        return {"success": False, "open_id": None, "users": [], "error": str(exc)}


# ── IM messaging ──────────────────────────────────────────────────────────


def _send_message(receive_id: str, msg_type: str, content: str) -> Dict[str, Any]:
    """Low-level: send a message via ``POST /im/v1/messages`` (bot mode).

    Returns:
        {"success": bool, "message_id": str|None, "error": str|None}
    """
    if MOCK_MODE:
        logger.info("[MOCK] _send_message type=%s to=%s", msg_type, receive_id)
        return {"success": True, "message_id": "om_mock_msg_001", "error": None}

    try:
        body = _feishu_post(
            _SEND_MSG_URL,
            {
                "receive_id": receive_id,
                "msg_type": msg_type,
                "content": content,
            },
            params={"receive_id_type": "open_id"},
        )
        code = body.get("code", -1)
        if code != 0:
            return {"success": False, "message_id": None, "error": f"code={code} msg={body.get('msg', '')}"}

        msg_id = (body.get("data", {}) or {}).get("message_id")
        return {"success": True, "message_id": msg_id, "error": None}

    except Exception as exc:
        logger.error("_send_message failed: %s", exc)
        return {"success": False, "message_id": None, "error": str(exc)}


def send_text_message(open_id: str, content: str) -> Dict[str, Any]:
    """Send a plain text message to a user via Feishu Bot.

    Args:
        open_id: Recipient Feishu open_id (``ou_`` prefix).
        content: Plain text body.

    Returns:
        {"success": bool, "message_id": str|None, "error": str|None}
    """
    return _send_message(open_id, "text", json.dumps({"text": content}))


def send_card_message(open_id: str, card_json: str) -> Dict[str, Any]:
    """Send an interactive card message via Feishu Bot.

    Args:
        open_id: Recipient Feishu open_id.
        card_json: JSON string of the card body (Feishu card schema).

    Returns:
        {"success": bool, "message_id": str|None, "error": str|None}
    """
    card_obj = json.loads(card_json) if isinstance(card_json, str) else card_json
    formatted = {
        "config": card_obj.get("config", {"wide_screen_mode": True}),
        "header": card_obj.get("header", {}),
        "elements": card_obj.get("elements", []),
    }
    return _send_message(open_id, "interactive", json.dumps(formatted))


def _build_card(header_title: str, header_color: str, elements: List[Dict]) -> dict:
    """Build a Feishu interactive card dict."""
    return {
        "config": {"wide_screen_mode": True},
        "header": {
            "title": {"tag": "plain_text", "content": header_title},
            "template": header_color,
        },
        "elements": elements,
    }


def send_interview_invite(
    candidate_name: str,
    interviewer_name: str,
    position: str,
    interview_date: str,
    round_name: str,
    *,
    interviewer_open_id: str = None,
    candidate_open_id: str = None,
    meeting_url: str = None,
    meeting_code: str = None,
    meeting_pwd: str = None,
) -> Dict[str, Any]:
    """Send interview invitation card to interviewer + candidate.

    Prefer explicit ``interviewer_open_id`` / ``candidate_open_id`` (bot mode).
    Falls back to ``search_user()`` and ``get_recipient_open_id()``.
    Optional meeting info (url/code/pwd) is rendered into the card and the
    candidate text message when provided.

    Returns:
        {"success": bool, "interviewer_sent": bool, "candidate_sent": bool,
         "interviewer_msg_id": str|None, "candidate_msg_id": str|None, "errors": list}
    """
    errors: List[str] = []

    card_content = f"**候选人**: {candidate_name}\n**岗位**: {position}\n**轮次**: {round_name}\n**时间**: {interview_date}"
    if meeting_url:
        card_content += f"\n**会议链接**: [点击进入会议]({meeting_url})"
    if meeting_code:
        card_content += f"\n**会议号**: {meeting_code}"
    if meeting_pwd:
        card_content += f"\n**入会密码**: {meeting_pwd}"

    card_elements = [
        {"tag": "markdown", "content": card_content},
    ]
    if meeting_url:
        card_elements.append({"tag": "action", "actions": [{
            "tag": "button",
            "text": {"tag": "plain_text", "content": "进入面试"},
            "type": "primary",
            "url": meeting_url,
        }]})
    card_elements.extend([
        {"tag": "hr"},
        {"tag": "note", "elements": [{"tag": "plain_text", "content": "请在面试结束后及时在系统中提交评价"}]},
    ])
    card = _build_card(header_title=f"面试邀请 — {round_name}", header_color="blue", elements=card_elements)

    # ── Interviewer ──
    iv_open_id = interviewer_open_id or get_recipient_open_id(interviewer_name)
    interviewer_sent = False
    interviewer_msg_id = None
    if not iv_open_id and not MOCK_MODE:
        r = search_user(interviewer_name)
        if r["success"] and r["users"]:
            iv_open_id = r["users"][0]["open_id"]
    if MOCK_MODE:
        # In mock mode, send_card_message itself is mocked
        r = send_card_message("ou_mock_interviewer", json.dumps(card))
        interviewer_sent = r["success"]
        interviewer_msg_id = r.get("message_id")
    elif iv_open_id:
        r = send_card_message(iv_open_id, json.dumps(card))
        interviewer_sent = r["success"]
        interviewer_msg_id = r.get("message_id")
        if not interviewer_sent:
            errors.append(f"interviewer card fail: {r.get('error')}")
    else:
        errors.append(f"interviewer not found: {interviewer_name}")

    # ── Candidate (best-effort) ──
    cand_open_id = candidate_open_id or get_recipient_open_id(candidate_name)
    candidate_sent = False
    candidate_msg_id = None
    if not cand_open_id and not MOCK_MODE:
        r = search_user(candidate_name)
        if r["success"] and r["users"]:
            cand_open_id = r["users"][0]["open_id"]
    if MOCK_MODE:
        r = send_text_message("ou_mock_candidate", _candidate_text(candidate_name, position, interview_date, meeting_url))
        candidate_sent = r["success"]
        candidate_msg_id = r.get("message_id")
    elif cand_open_id:
        text = _candidate_text(candidate_name, position, interview_date, meeting_url)
        r = send_text_message(cand_open_id, text)
        candidate_sent = r["success"]
        candidate_msg_id = r.get("message_id")
        if not candidate_sent:
            errors.append(f"candidate msg fail: {r.get('error')}")
    else:
        errors.append(f"candidate not found: {candidate_name}")

    overall = interviewer_sent or candidate_sent
    logger.info("send_interview_invite candidate=%s interviewer=%s success=%s", candidate_name, interviewer_name, overall)
    return {
        "success": overall, "interviewer_sent": interviewer_sent, "candidate_sent": candidate_sent,
        "interviewer_msg_id": interviewer_msg_id, "candidate_msg_id": candidate_msg_id, "errors": errors,
    }


def _candidate_text(candidate_name: str, position: str, interview_date: str, meeting_url: str = None) -> str:
    """Candidate notification text, appending the meeting link when present."""
    text = f"您好 {candidate_name}，您有一场关于 {position} 的面试安排在 {interview_date}，请准时参加。"
    if meeting_url:
        text += f"\n会议链接：{meeting_url}"
    return text


def send_reminder(book_id: str, *, recipient_open_id: str = None, interviewer_name: str = None) -> Dict[str, Any]:
    """Send a 15-minute pre-interview reminder card.

    Args:
        book_id: Interview booking identifier.
        recipient_open_id: Direct open_id (bot mode, preferred).
        interviewer_name: Fallback display name for open_id lookup.

    Returns:
        {"success": bool, "message_id": str|None, "error": str|None}
    """
    if MOCK_MODE:
        logger.info("[MOCK] send_reminder book_id=%s", book_id)
        return {"success": True, "message_id": "om_mock_reminder_001", "error": None}

    open_id = recipient_open_id or get_recipient_open_id(interviewer_name or "")
    if not open_id:
        return {"success": False, "message_id": None, "error": "No recipient open_id — set FEISHU_RECIPIENT_OPEN_IDS or pass recipient_open_id"}

    card = _build_card(
        header_title="面试提醒", header_color="red",
        elements=[
            {"tag": "markdown", "content": "距离面试开始还有 **15 分钟**，请做好准备。"},
            {"tag": "action", "actions": [{"tag": "button", "text": {"tag": "plain_text", "content": "进入面试"}, "type": "primary", "url": "https://vc.feishu.cn/"}]},
        ],
    )
    return send_card_message(open_id, json.dumps(card))


def send_overdue_alert(interviewer_name: str, *, recipient_open_id: str = None) -> Dict[str, Any]:
    """Send overdue evaluation reminder (over 3 days) card.

    Args:
        interviewer_name: Display name of the overdue interviewer.
        recipient_open_id: Direct open_id (bot mode, preferred).

    Returns:
        {"success": bool, "message_id": str|None, "error": str|None}
    """
    if MOCK_MODE:
        logger.info("[MOCK] send_overdue_alert to=%s", interviewer_name)
        return {"success": True, "message_id": "om_mock_overdue_001", "error": None}

    open_id = recipient_open_id or get_recipient_open_id(interviewer_name)
    if not open_id:
        return {"success": False, "message_id": None, "error": f"No recipient open_id for '{interviewer_name}' — set FEISHU_RECIPIENT_OPEN_IDS or pass recipient_open_id"}

    card = _build_card(
        header_title="评价逾期提醒", header_color="red",
        elements=[
            {"tag": "markdown", "content": "您有面试评价已超过 **3 天** 未提交。\n\n请在系统中及时完成评价，以免影响候选人后续流程。"},
            {"tag": "note", "elements": [{"tag": "plain_text", "content": "此消息由招聘系统自动发送"}]},
        ],
    )
    return send_card_message(open_id, json.dumps(card))


# ── Calendar ──────────────────────────────────────────────────────────────


def check_freebusy(user_ids: List[str], start_time: str, end_time: str) -> Dict[str, Any]:
    """Query free/busy status for one or more users.

    Requires ``calendar:calendar.free_busy:read`` scope.

    Returns:
        {"success": bool, "busy_slots": list, "error": str|None}
    """
    if MOCK_MODE:
        logger.info("[MOCK] check_freebusy users=%s %s->%s", user_ids, start_time, end_time)
        return {"success": True, "busy_slots": [], "error": None}

    try:
        body = _feishu_post(
            "/calendar/v4/freebusy/list",
            {"user_id": user_ids[0], "start_time": start_time, "end_time": end_time},
        )
        code = body.get("code", -1)
        if code != 0:
            return {"success": False, "busy_slots": [], "error": body.get("msg", str(code))}

        freebusy_list = (body.get("data", {}) or {}).get("freebusy_list") or [{}]
        busy = freebusy_list[0].get("free_busy", []) if freebusy_list else []
        return {"success": True, "busy_slots": busy, "error": None}

    except Exception as exc:
        logger.warning("check_freebusy failed: %s", exc)
        return {"success": False, "busy_slots": [], "error": str(exc)}


def create_calendar_event(
    title: str,
    start_time: str,
    end_time: str,
    attendees: List[str],
    description: str = "",
) -> Dict[str, Any]:
    """Create a calendar event (interview schedule) in Feishu.

    Requires ``calendar:calendar.event:create`` scope.

    Returns:
        {"success": bool, "event_id": str|None, "error": str|None}
    """
    if MOCK_MODE:
        logger.info("[MOCK] create_calendar_event title=%s %s->%s", title, start_time, end_time)
        return {"success": True, "event_id": "evt_mock_cal_001", "error": None}

    try:
        body = _feishu_post(
            "/calendar/v4/calendars/primary/events",
            {
                "summary": title,
                "start_time": {"date_time": start_time},
                "end_time": {"date_time": end_time},
                "attendees": [{"type": "user", "user_id": uid} for uid in attendees] if attendees else [],
                "description": description or "",
            },
        )
        code = body.get("code", -1)
        if code != 0:
            return {"success": False, "event_id": None, "error": body.get("msg", str(code))}

        event_id = (body.get("data", {}) or {}).get("event_id")
        return {"success": True, "event_id": event_id, "error": None}

    except Exception as exc:
        logger.warning("create_calendar_event failed: %s", exc)
        return {"success": False, "event_id": None, "error": str(exc)}


# ============================================================================
# Mock data — structured data matching what the frontend expects
# ============================================================================


def mock_data() -> Dict[str, Any]:
    """Return mock interview invite and notification data for frontend consumption."""
    now = datetime.now()
    tomorrow = now + timedelta(days=1)
    day_after = now + timedelta(days=2)

    def _fmt(dt):
        return dt.strftime("%m-%d")

    return {
        "invites": [
            {"candidate": "张三", "position": "高级Java工程师", "round": "初试(1/3轮)", "interviewer": "李面试官", "date": _fmt(tomorrow), "time": "14:00", "method": "飞书视频", "status": "scheduled", "statusLabel": "待面试", "bookId": "BK202407001"},
            {"candidate": "李四", "position": "前端工程师", "round": "初试(1/2轮)", "interviewer": "王面试官", "date": _fmt(day_after), "time": "10:00", "method": "飞书视频", "status": "scheduled", "statusLabel": "待面试", "bookId": "BK202407002"},
            {"candidate": "王五", "position": "数据分析师", "round": "初试(2轮)", "interviewer": "张HR", "date": _fmt(day_after), "time": "11:00", "method": "电话", "status": "scheduled", "statusLabel": "待面试", "bookId": "BK202407003"},
        ],
        "alerts": [
            {"text": "孙九 · 复试超3天未评价", "type": "reject", "action": "去评价", "actionMsg": "填写对孙九的评价"},
            {"text": "陈二 · 初试超5天未评价", "type": "reject", "action": "去评价", "actionMsg": "填写对陈二的评价"},
        ],
        "notifications": [
            {"name": "面试邀请通知", "type": "面试", "method": "飞书 + 短信", "updated": _fmt(now)},
            {"name": "Offer 发送模板", "type": "Offer", "method": "邮件", "updated": _fmt(now)},
            {"name": "未通过通知", "type": "淘汰", "method": "短信", "updated": _fmt(now)},
            {"name": "面试提醒（前一天）", "type": "提醒", "method": "飞书 + 短信", "updated": _fmt(now)},
        ],
        "template_preview": {
            "card_title": "面试邀请", "card_color": "blue",
            "fields": ["候选人姓名", "应聘岗位", "面试轮次", "面试时间", "面试方式"],
            "sample_values": {"候选人姓名": "张三", "应聘岗位": "高级Java工程师", "面试轮次": "初试(1/3轮)", "面试时间": f"{_fmt(tomorrow)} 14:00", "面试方式": "飞书视频"},
        },
        "calendar_sample": {
            "title": "面试-张三-高级Java工程师初试",
            "start": f"2026-{_fmt(tomorrow)}T14:00:00+08:00",
            "end": f"2026-{_fmt(tomorrow)}T15:00:00+08:00",
            "attendees": ["ou_mock_李面试官", "ou_mock_张三"],
        },
    }
