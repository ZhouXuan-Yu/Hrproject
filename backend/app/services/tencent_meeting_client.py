"""Tencent Meeting (腾讯会议) integration client for the recruitment system.

Follows the same pattern as ``feishu_client.py``: MOCK_MODE (default True)
keeps everything running without a real tenant; set
``TENCENT_MEETING_MOCK_MODE=false`` to call the real Tencent Meeting REST API.

Credentials are managed through the config page (AES-256-GCM encrypted in
``ApiKeyConfig``) under these key names:

    tencent_appid      — 腾讯会议开放平台 AppId
    tencent_secretid   — SecretId
    tencent_secretkey  — SecretKey (encrypted at rest)
    tencent_userid     — 主持人 userid (required when creating a meeting)

Environment variables ``TENCENT_MEETING_APP_ID`` / ``TENCENT_MEETING_SECRET_ID``
/ ``TENCENT_MEETING_SECRET_KEY`` / ``TENCENT_MEETING_USERID`` take precedence
over the DB-stored values, mirroring the "env wins" rule used elsewhere.
"""

import hashlib
import hmac
import json
import logging
import os
import random
import time
from typing import Any, Dict, Optional

import requests

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
TENCENT_API_BASE = os.getenv("TENCENT_MEETING_API_BASE", "https://api.meeting.qq.com")
_CREATE_MEETING_URI = "/v1/meetings"

# ---------------------------------------------------------------------------
# Mock mode — default True so dev environments work out of the box
# ---------------------------------------------------------------------------
MOCK_MODE = os.getenv("TENCENT_MEETING_MOCK_MODE", "false").strip().lower() in ("true", "1", "yes")

# key_name in ApiKeyConfig → env var override
_ENV_MAP = {
    "tencent_appid": "TENCENT_MEETING_APP_ID",
    "tencent_secretid": "TENCENT_MEETING_SECRET_ID",
    "tencent_secretkey": "TENCENT_MEETING_SECRET_KEY",
    "tencent_userid": "TENCENT_MEETING_USERID",
}


def _get_credentials() -> Dict[str, Optional[str]]:
    """Return credential dict; env vars take precedence over encrypted DB values."""
    try:
        from app.services.config_service import get_tencent_meeting_credentials
        db_creds = get_tencent_meeting_credentials()
    except Exception as exc:  # no app context / DB error — env-only fallback
        logger.warning("tencent_meeting: failed to read stored credentials: %s", exc)
        db_creds = {}
    return {
        key: (os.getenv(env_name) or db_creds.get(key))
        for key, env_name in _ENV_MAP.items()
    }


def is_configured() -> bool:
    """True when all four credentials (AppId/SecretId/SecretKey/UserID) are present."""
    creds = _get_credentials()
    return all(creds.get(k) for k in _ENV_MAP)


def _random_code(n: int = 10) -> str:
    return "".join(random.choice("0123456789") for _ in range(n))


def create_meeting(topic: str, start_time, duration_minutes: int = 60) -> Dict[str, Any]:
    """Create a Tencent Meeting and return its join URL.

    Args:
        topic: Meeting subject.
        start_time: Meeting start as epoch seconds (int or str) — anything
            ``int()`` can parse.
        duration_minutes: Meeting duration, default 60.

    Returns:
        {"meeting_url": str, "meeting_code": str}

    Mock mode (or missing credentials) returns a locally-built fake URL.
    Real mode calls ``POST https://api.meeting.qq.com/v1/meetings``; any
    failure raises so the caller can fall back to a locally-built URL.
    """
    if MOCK_MODE or not is_configured():
        logger.info(
            "[MOCK] create_meeting topic=%s — 模拟模式/未配置，不生成假链接 (mock_mode=%s configured=%s)",
            topic, MOCK_MODE, is_configured(),
        )
        return {"meeting_url": "", "meeting_code": ""}

    creds = _get_credentials()
    start_ts = int(start_time)
    end_ts = start_ts + int(duration_minutes) * 60

    body = {
        "userid": creds["tencent_userid"],
        "instanceid": 1,
        "subject": topic,
        "type": 0,  # 预约会议
        "start_time": str(start_ts),
        "end_time": str(end_ts),
    }
    body_str = json.dumps(body, separators=(",", ":"), ensure_ascii=False)

    # Tencent Meeting REST API signature:
    #   signature = HEX( HMAC-SHA256(SecretKey, "METHOD\nURI\nsorted_query\nbody") )
    method = "POST"
    query_string = ""  # no query params on this endpoint
    string_to_sign = f"{method}\n{_CREATE_MEETING_URI}\n{query_string}\n{body_str}"
    signature = hmac.new(
        creds["tencent_secretkey"].encode("utf-8"),
        string_to_sign.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()

    headers = {
        "Content-Type": "application/json",
        "AppId": creds["tencent_appid"],
        "SecretId": creds["tencent_secretid"],
        "timestamp": str(int(time.time())),
        "nonce": str(random.randint(100000, 999999)),
        "signature": signature,
    }

    resp = requests.post(
        f"{TENCENT_API_BASE}{_CREATE_MEETING_URI}",
        data=body_str.encode("utf-8"),
        headers=headers,
        timeout=15,
    )
    resp.raise_for_status()
    payload = resp.json()

    error_info = payload.get("error_info") or {}
    if error_info.get("error_code"):
        raise RuntimeError(
            f"tencent meeting create failed: error_code={error_info.get('error_code')} "
            f"message={error_info.get('message', '')}"
        )

    info_list = payload.get("meeting_info_list") or []
    info = info_list[0] if info_list else {}
    join_url = info.get("join_url")
    meeting_code = info.get("meeting_code") or str(info.get("meeting_id", ""))
    if not join_url:
        raise RuntimeError(f"tencent meeting create returned no join_url: {payload}")
    logger.info("tencent meeting created: code=%s url=%s", meeting_code, join_url)
    return {"meeting_url": join_url, "meeting_code": meeting_code}


def health_check() -> Dict[str, Any]:
    """Quick connectivity/config check."""
    if MOCK_MODE:
        return {"status": "ok", "mode": "mock", "configured": is_configured()}
    if not is_configured():
        return {"status": "error", "mode": "real", "configured": False,
                "error": "credentials incomplete"}
    return {"status": "ok", "mode": "real", "configured": True}
