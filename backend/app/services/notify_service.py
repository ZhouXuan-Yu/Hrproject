"""Unified notification service — Feishu primary, log fallback.

All business notifications (approval, interview, offer, onboarding) route
through this module. When Feishu is configured and the recipient has a valid
open_id, messages are delivered via the Feishu Bot API. Otherwise they fall
back to structured application logs (JSON) that can be picked up by log
aggregation.

Usage::

    from app.services.notify_service import notify_user

    notify_user(user_id, title="审批通知", body="你的需求已通过部门负责人审批")
"""

import json
import logging
from typing import Optional

log = logging.getLogger(__name__)

# ── Card colour constants (Feishu message card template colours) ──────────
CARD_BLUE = 'blue'
CARD_GREEN = 'green'
CARD_RED = 'red'
CARD_YELLOW = 'yellow'


def _resolve_open_id(user_id: int) -> Optional[str]:
    """Look up a user's Feishu open_id from IamUser."""
    if not user_id:
        return None
    try:
        from app.models.iam import IamUser
        user = IamUser.query.filter_by(user_id=user_id, status=1, is_deleted=0).first()
        if user and user.feishu_open_id:
            return user.feishu_open_id
    except Exception as exc:
        log.debug("_resolve_open_id lookup failed for user %s: %s", user_id, exc)
    return None


def _resolve_user_name(user_id: int) -> str:
    """Best-effort user display name."""
    try:
        from app.services.name_resolver import resolve_user_name
        return resolve_user_name(user_id)
    except Exception:
        return str(user_id)


def _send_feishu_card(open_id: str, title: str, body: str, color: str = CARD_BLUE) -> dict:
    """Deliver a Feishu interactive card message.

    Returns {'sent': True} on success or {'sent': False, 'reason': ...} on failure.
    """
    try:
        from app.services.feishu_client import send_text_message

        # Build a readable text message (bot-mode sends text, not card JSON in v0.1)
        text = f"【{title}】\n{body}"
        result = send_text_message(open_id, text)
        if result.get('success'):
            return {'sent': True, 'message_id': result.get('message_id')}
        return {'sent': False, 'reason': result.get('error', 'unknown')}
    except Exception as exc:
        return {'sent': False, 'reason': str(exc)}


def notify_user(user_id: int, title: str, body: str, color: str = CARD_BLUE) -> dict:
    """Send a notification to a user. Feishu first, log fallback.

    Args:
        user_id: IamUser.user_id of the recipient.
        title: Short title (e.g. '审批通知').
        body: Message body in plain text.
        color: Card colour hint (CARD_BLUE/CARD_GREEN/CARD_RED/CARD_YELLOW).

    Returns:
        {'user_id': ..., 'name': ..., 'method': 'feishu'|'log', 'sent': bool, ...}
    """
    name = _resolve_user_name(user_id)
    open_id = _resolve_open_id(user_id)

    if open_id:
        result = _send_feishu_card(open_id, title, body, color)
        if result.get('sent'):
            log.info("[NOTIFY] feishu → %s (%s): %s", name, user_id, title)
            return {'user_id': user_id, 'name': name, 'method': 'feishu', 'sent': True,
                    'message_id': result.get('message_id')}
        # Feishu send failed — fall through to log fallback
        log.warning("[NOTIFY] feishu failed for %s (%s): %s — falling back to log",
                    name, user_id, result.get('reason', 'unknown'))

    # Log fallback — structured JSON for log aggregation
    log.info(
        "[NOTIFY] log → %s (%s): %s — %s",
        name, user_id, title,
        json.dumps(body, ensure_ascii=False)[:200],
    )
    return {'user_id': user_id, 'name': name, 'method': 'log', 'sent': True,
            'open_id_missing': not bool(open_id)}
