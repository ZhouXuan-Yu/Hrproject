"""Feishu notification task: interview invitations, reminders, overdue alerts.

All tasks query the database for real data before calling feishu_client
functions.  Failures are logged and returned in the result dict so they
never crash the Celery worker.
"""
import logging
from tasks.celery_app import celery_app

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# v0.1 recipient open_id lookup table.
# Maps user display names -> Feishu open_id so that interview invitations
# and reminders can be sent via bot mode (bypassing search_user).
#
# Production path: once the IamUser / Candidate table has a `feishu_open_id`
# column, this module-level dict will be replaced by a DB query.  For now
# it is populated from the FEISHU_RECIPIENT_OPEN_IDS env var (a JSON map).
# ---------------------------------------------------------------------------
import json as _json
import os as _os

_RECIPIENT_OPEN_IDS: dict = {}
_raw = _os.getenv("FEISHU_RECIPIENT_OPEN_IDS", "")
if _raw:
    try:
        _parsed = _json.loads(_raw)
        if isinstance(_parsed, dict):
            _RECIPIENT_OPEN_IDS = _parsed
            log.info("Loaded %d recipient open_id(s) from FEISHU_RECIPIENT_OPEN_IDS", len(_parsed))
    except _json.JSONDecodeError:
        log.warning("FEISHU_RECIPIENT_OPEN_IDS is not valid JSON: %s", _raw[:120])


def _resolve_interview_book(book_id):
    """Look up an InterviewBook by its primary key id.

    Returns the ORM object or None.  Logs on failure.
    """
    try:
        from app.models.interview import InterviewBook
        from app.extensions import db

        book = db.session.query(InterviewBook).filter(
            InterviewBook.id == book_id,
            InterviewBook.is_deleted == 0,
        ).first()
        if not book:
            log.warning("InterviewBook not found: id=%s", book_id)
        return book
    except Exception as exc:
        log.exception("Failed to query InterviewBook id=%s: %s", book_id, exc)
        return None


def _resolve_interview_info(book):
    """Resolve candidate_name, interviewer_name, position from an InterviewBook.

    Args:
        book: An InterviewBook ORM instance.

    Returns:
        dict with keys: candidate_name, interviewer_name, position,
        interview_date, round_name, or None for unresolvable fields.
    """
    from app.extensions import db

    candidate_name = None
    interviewer_name = None
    position = None
    interview_date = None
    round_name = None

    # -- Resolve candidate name from Resume -> Candidate --
    try:
        from app.models.candidate import Candidate, Resume

        resume = db.session.query(Resume).filter(
            Resume.id == book.resume_id,
            Resume.is_deleted == 0,
        ).first()
        if resume and resume.candidate_id:
            cand = db.session.query(Candidate).filter(
                Candidate.id == resume.candidate_id,
                Candidate.is_deleted == 0,
            ).first()
            if cand:
                candidate_name = cand.candidate_name
    except Exception as exc:
        log.warning("Failed to resolve candidate for book %s: %s", book.id, exc)

    # -- Resolve position name from RecruitDemand --
    try:
        from app.models.demand import RecruitDemand
        from app.services.demand_service import POS_NAMES

        demand = db.session.query(RecruitDemand).filter(
            RecruitDemand.id == book.demand_id,
            RecruitDemand.is_deleted == 0,
        ).first()
        if demand:
            position = POS_NAMES.get(demand.position_id, f'岗位#{demand.position_id}')
    except Exception as exc:
        log.warning("Failed to resolve position for book %s: %s", book.id, exc)

    # -- Resolve interviewer name from InterviewSlot -> IamUser --
    try:
        from app.models.interview import InterviewSlot
        from app.models.iam import IamUser

        slot = db.session.query(InterviewSlot).filter(
            InterviewSlot.id == book.slot_id,
            InterviewSlot.is_deleted == 0,
        ).first()
        if slot:
            user = db.session.query(IamUser).filter(
                IamUser.user_id == slot.interviewer_id,
            ).first()
            if user:
                interviewer_name = user.real_name
    except Exception as exc:
        log.warning("Failed to resolve interviewer for book %s: %s", book.id, exc)

    # -- Format date and round --
    if book.book_time:
        interview_date = book.book_time.strftime('%m-%d %H:%M')

    if book.interview_round:
        round_label = '初试' if book.interview_round == 1 else '复试'
        round_name = f'{round_label}({book.interview_round}轮)'
    else:
        round_name = '待定'

    return {
        'candidate_name': candidate_name,
        'interviewer_name': interviewer_name,
        'position': position,
        'interview_date': interview_date,
        'round_name': round_name,
    }


# ============================================================================
# Tasks
# ============================================================================


@celery_app.task(name='tasks.notify.send_interview_invite')
def send_interview_invite(book_id):
    """Send Feishu interview invitation card to interviewer + candidate.

    Looks up InterviewBook by id, resolves candidate/interviewer/position
    from related tables, then delegates to feishu_client.send_interview_invite().
    """
    try:
        from app.services import feishu_client as fc
    except ImportError as exc:
        log.error("Failed to import feishu_client: %s", exc)
        return {'status': 'error', 'error': str(exc), 'book_id': book_id}

    book = _resolve_interview_book(book_id)
    if not book:
        return {'status': 'error', 'error': 'InterviewBook not found', 'book_id': book_id}

    info = _resolve_interview_info(book)

    if not info['candidate_name']:
        log.warning("send_interview_invite: candidate_name unresolvable for book %s", book_id)
        info['candidate_name'] = f'候选人#{book.resume_id}'

    if not info['interviewer_name']:
        log.warning("send_interview_invite: interviewer_name unresolvable for book %s", book_id)
        info['interviewer_name'] = f'面试官#{book.slot_id}'

    if not info['position']:
        info['position'] = f'岗位#{book.demand_id}'

    # Resolve open_ids from env-configured map (bot-mode path)
    interviewer_oid = _RECIPIENT_OPEN_IDS.get(info['interviewer_name'])
    candidate_oid = _RECIPIENT_OPEN_IDS.get(info['candidate_name'])

    try:
        result = fc.send_interview_invite(
            candidate_name=info['candidate_name'],
            interviewer_name=info['interviewer_name'],
            position=info['position'],
            interview_date=info['interview_date'] or '待定',
            round_name=info['round_name'] or '待定',
            interviewer_open_id=interviewer_oid,
            candidate_open_id=candidate_oid,
        )

        log.info(
            "Interview invite sent for book %s: candidate=%s interviewer=%s success=%s",
            book_id, info['candidate_name'], info['interviewer_name'],
            result.get('success'),
        )

        return {
            'status': 'ok' if result.get('success') else 'partial',
            'book_id': book_id,
            'channels': ['feishu_card'],
            'result': result,
        }
    except Exception as exc:
        log.exception("send_interview_invite failed for book %s: %s", book_id, exc)
        return {'status': 'error', 'error': str(exc), 'book_id': book_id}


@celery_app.task(name='tasks.notify.send_reminder')
def send_reminder(book_id):
    """Send a 15-minute pre-interview reminder via Feishu.

    Resolves the InterviewBook record and interviewer info, then calls
    feishu_client.send_reminder().
    """
    try:
        from app.services import feishu_client as fc
    except ImportError as exc:
        log.error("Failed to import feishu_client: %s", exc)
        return {'status': 'error', 'error': str(exc), 'book_id': book_id}

    book = _resolve_interview_book(book_id)
    if not book:
        return {'status': 'error', 'error': 'InterviewBook not found', 'book_id': book_id}

    # Resolve interviewer name for logging; pass book_id to client
    info = _resolve_interview_info(book)
    interviewer = info['interviewer_name'] or f'面试官#{book.slot_id}'

    # Resolve open_id from env-configured map
    interviewer_oid = _RECIPIENT_OPEN_IDS.get(info['interviewer_name'])

    try:
        result = fc.send_reminder(str(book_id), recipient_open_id=interviewer_oid)

        log.info(
            "Reminder sent for book %s (interviewer=%s): success=%s",
            book_id, interviewer, result.get('success'),
        )

        return {
            'status': 'ok' if result.get('success') else 'error',
            'book_id': book_id,
            'interviewer': interviewer,
            'type': '15min_reminder',
            'result': result,
        }
    except Exception as exc:
        log.exception("send_reminder failed for book %s: %s", book_id, exc)
        return {'status': 'error', 'error': str(exc), 'book_id': book_id}


@celery_app.task(name='tasks.notify.send_overdue_alert')
def send_overdue_alert(interviewer_name):
    """Send an overdue evaluation reminder (>3 days after interview) via Feishu.

    Delegates to feishu_client.send_overdue_alert().
    """
    try:
        from app.services import feishu_client as fc
    except ImportError as exc:
        log.error("Failed to import feishu_client: %s", exc)
        return {'status': 'error', 'error': str(exc), 'interviewer': interviewer_name}

    # Resolve open_id from env-configured map
    recipient_oid = _RECIPIENT_OPEN_IDS.get(interviewer_name)

    try:
        result = fc.send_overdue_alert(interviewer_name, recipient_open_id=recipient_oid)

        log.info(
            "Overdue alert sent to interviewer '%s': success=%s",
            interviewer_name, result.get('success'),
        )

        return {
            'status': 'ok' if result.get('success') else 'error',
            'interviewer': interviewer_name,
            'type': 'overdue_alert',
            'result': result,
        }
    except Exception as exc:
        log.exception("send_overdue_alert failed for interviewer '%s': %s", interviewer_name, exc)
        return {'status': 'error', 'error': str(exc), 'interviewer': interviewer_name}


@celery_app.task(name='tasks.notify.check_overdue')
def check_overdue():
    """
    Periodic task: find interviews older than 3 days where the evaluation
    has not been submitted, and send overdue alerts to each interviewer.

    Checks InterviewBook records where book_time is older than the cutoff
    and no InterviewRecord with non-empty evaluate_text exists.
    """
    from datetime import datetime, timedelta

    cutoff = datetime.now() - timedelta(days=3)

    try:
        from app.models.interview import InterviewBook, InterviewSlot, InterviewRecord
        from app.models.iam import IamUser
        from app.extensions import db
    except ImportError as exc:
        log.error("Failed to import models for check_overdue: %s", exc)
        return {'status': 'error', 'error': str(exc)}

    try:
        # Find all InterviewBook records older than 3 days
        old_books = db.session.query(InterviewBook).filter(
            InterviewBook.is_deleted == 0,
            InterviewBook.book_time.isnot(None),
            InterviewBook.book_time < cutoff,
        ).all()
    except Exception as exc:
        log.exception("check_overdue DB query failed: %s", exc)
        return {'status': 'error', 'error': f'DB query failed: {exc}'}

    if not old_books:
        log.info("check_overdue: no interviews older than 3 days found")
        return {'status': 'ok', 'overdue_count': 0, 'alerts_sent': 0, 'details': []}

    log.info("check_overdue: checking %d interview(s) older than 3 days", len(old_books))

    alerts_sent = 0
    overdue_details = []

    for book in old_books:
        try:
            # Check whether an evaluation has already been submitted
            record = db.session.query(InterviewRecord).filter(
                InterviewRecord.book_id == book.id,
                InterviewRecord.is_deleted == 0,
            ).first()

            # If a record exists with non-empty evaluate_text, skip
            if record and record.evaluate_text:
                continue

            # Resolve interviewer name
            slot = db.session.query(InterviewSlot).filter(
                InterviewSlot.id == book.slot_id,
                InterviewSlot.is_deleted == 0,
            ).first()
            if not slot:
                log.debug("No InterviewSlot found for book %s (slot_id=%s)", book.id, book.slot_id)
                continue

            user = db.session.query(IamUser).filter(
                IamUser.user_id == slot.interviewer_id,
            ).first()
            if not user:
                log.debug("No IamUser found for interviewer_id=%s", slot.interviewer_id)
                continue

            days_overdue = (datetime.now() - book.book_time).days

            # Send the alert
            task_result = send_overdue_alert(user.real_name)
            if task_result.get('status') == 'ok':
                alerts_sent += 1

            overdue_details.append({
                'book_id': book.id,
                'interviewer': user.real_name,
                'days_overdue': days_overdue,
                'alert_sent': task_result.get('status') == 'ok',
            })

            log.info(
                "Overdue: book=%s interviewer=%s days=%d alert_sent=%s",
                book.id, user.real_name, days_overdue,
                task_result.get('status') == 'ok',
            )

        except Exception as exc:
            log.exception("Error processing overdue check for book %s: %s", book.id, exc)
            overdue_details.append({
                'book_id': book.id,
                'error': str(exc),
            })

    log.info(
        "check_overdue complete: %d overdue found, %d alerts sent",
        len(overdue_details), alerts_sent,
    )

    return {
        'status': 'ok',
        'overdue_count': len(overdue_details),
        'alerts_sent': alerts_sent,
        'details': overdue_details,
    }


@celery_app.task(name='tasks.notify.notify_batch')
def notify_batch(book_ids):
    """
    Batch notification for multiple interviews.

    Iterates over book_ids and calls send_interview_invite for each one.
    Aggregates results into a summary.
    """
    if not book_ids:
        return {'status': 'ok', 'sent': 0, 'total': 0, 'results': []}

    log.info("Batch notify: sending invites for %d interview(s)", len(book_ids))

    results = []
    for bid in book_ids:
        result = send_interview_invite(bid)
        results.append(result)

    sent_count = sum(1 for r in results if r.get('status') == 'ok')

    log.info("Batch notify complete: %d/%d sent", sent_count, len(book_ids))

    return {
        'status': 'ok',
        'sent': sent_count,
        'total': len(book_ids),
        'results': results,
    }


@celery_app.task(name='tasks.notify.offer_followup')
def offer_followup():
    """Periodic task: Offer 确认倒计时巡检。

    每小时执行一次；任务内部按配置判断：
      - 已发送 Offer 距上次提醒超过 OFFER_REMINDER_INTERVAL_HOURS（默认24h）
        → 发倒计时提醒邮件
      - 超过 OFFER_CONFIRM_DEADLINE_DAYS（默认3天）未确认 → 自动淘汰
    """
    try:
        from app.services import hire_service
        result = hire_service.offer_followup()
        log.info("offer_followup complete: %s", result)
        return {'status': 'ok', **result}
    except Exception as exc:
        log.exception("offer_followup failed: %s", exc)
        return {'status': 'error', 'error': str(exc)}


# ============================================================================
# Celery Beat schedule — merged into the app's beat_schedule
# ============================================================================

BEAT_SCHEDULE = {
    # 15 分钟 tick 一次；每个邮箱按自己的 sync_freq（30分钟/2小时/每天…）
    # 在任务内部判断是否到期，避免为不同频率建多个 schedule
    'sync-email-tick': {
        'task': 'tasks.email_sync.sync_all_mailboxes',
        'schedule': 900.0,  # 15 minutes
    },
    'check-overdue-evaluations': {
        'task': 'tasks.notify.check_overdue',
        'schedule': 3600.0,  # 1 hour
    },
    # Offer 确认倒计时：每小时巡检一次，任务内部按 24h 间隔去重提醒、
    # 超过 OFFER_CONFIRM_DEADLINE_DAYS 自动淘汰
    'offer-confirm-countdown': {
        'task': 'tasks.notify.offer_followup',
        'schedule': 3600.0,  # 1 hour
    },
}

celery_app.conf.beat_schedule = BEAT_SCHEDULE
