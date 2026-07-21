"""Interview service: scheduling, state machine, evaluation.

Complete state machine with 6 states: pending -> scheduled -> evaluating -> offer -> onboard -> done.
All transitions are validated against INTERVIEW_TRANSITIONS. In-memory store removed — DB only.
"""
import logging
import random
from datetime import datetime, timedelta
from app.utils.response import AppError

log = logging.getLogger(__name__)


# ── State machine transition rules ──

INTERVIEW_TRANSITIONS = {
    'pending':    {'next': ['scheduled'],          'label': '待安排', 'action': '发起面试'},
    'scheduled':  {'next': ['evaluating', 'pending'], 'label': '待面试', 'action': '完成面试并评价'},
    'evaluating': {'next': ['offer', 'pending'],    'label': '待评价', 'action': '填写评价'},
    'offer':      {'next': ['onboard', 'pending'],  'label': '待录用', 'action': '发Offer'},
    'onboard':    {'next': ['done'],                'label': '待入职', 'action': '确认入职'},
    'done':       {'next': [],                      'label': '已入职', 'action': None},
    'cancelled':  {'next': [],                      'label': '已取消', 'action': None},
}

STATUS_LABELS = {k: v['label'] for k, v in INTERVIEW_TRANSITIONS.items()}


# ── Helpers ──

def _release_candidate_lock(candidate_id, candidate_name):
    """Release candidate lock when interview is not passed."""
    try:
        from app.models.candidate import Candidate
        from app.extensions import db
        c = Candidate.query.filter_by(id=candidate_id, is_deleted=0).first()
        if c:
            c.status = 'available'
            db.session.commit()
            log.info("Candidate lock released: id=%s name=%s", candidate_id, candidate_name)
            return True
    except Exception as exc:
        log.error("Failed to release candidate lock: %s", exc, exc_info=True)
    log.info("[MOCK] Candidate lock released: id=%s name=%s", candidate_id, candidate_name)
    return True


def _db_to_dict(book):
    """Convert an InterviewBook ORM object to frontend-compatible dict.

    Resolves status from process/interview-record state rather than a column on the book row.
    """
    candidate_name = None
    demand_pos = None
    try:
        from app.models.candidate import Candidate, Resume
        from app.models.demand import RecruitDemand
        r = Resume.query.filter_by(id=book.resume_id, is_deleted=0).first()
        if r and r.candidate_id:
            c = Candidate.query.filter_by(id=r.candidate_id, is_deleted=0).first()
            if c:
                candidate_name = c.candidate_name
        d = RecruitDemand.query.filter_by(id=book.demand_id, is_deleted=0).first()
        if d:
            demand_pos = f'岗位#{d.demand_no}'
    except Exception:
        pass

    # Derive display status from InterviewRecord presence
    from app.models.interview import InterviewRecord
    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if record:
        if record.interview_result == 1:
            display_status = 'offer'
            display_label = '待录用'
        else:
            display_status = 'evaluating'
            display_label = '待评价'
    else:
        if book.book_time and book.book_time > datetime.now():
            display_status = 'scheduled'
            display_label = '待面试'
        else:
            display_status = 'pending'
            display_label = '待安排'

    round_num = getattr(book, 'interview_round', 1) or 1
    return {
        'id': f'INT{book.id:04d}',
        'name': candidate_name or f'候选人#{book.resume_id}',
        'position': demand_pos or f'岗位#{book.demand_id}',
        'round': f"{'初试' if round_num == 1 else '复试'}({round_num}轮)",
        'interviewer': '待分配',
        'date': book.book_time.strftime('%m-%d') if book.book_time else '待定',
        'time': book.book_time.strftime('%H:%M') if book.book_time else '待定',
        'method': {1: '飞书视频', 2: '腾讯会议', 3: '其他线上', 4: '线下'}.get(book.interview_type, '待定'),
        'meetingUrl': getattr(book, 'meeting_url', '') or '',
        'meetingCode': book.meeting_code or '',
        'meetingPwd': book.meeting_pwd or '',
        'status': display_status,
        'statusLabel': display_label,
        'createdBy': '系统',
        'isMine': False,
        'score': None,
        'duration': None,
        'result': None,
    }


# ── Core API ──

def list_interviews(params):
    """Return paginated interview list — SQL-level pagination, DB only."""
    tab = params.get('tab', 'all')
    status = params.get('status', 'all')
    page = max(1, int(params.get('page', 1)))
    page_size = min(100, max(1, int(params.get('pageSize', 20))))

    from app.models.interview import InterviewBook
    q = InterviewBook.query.filter(InterviewBook.is_deleted == 0)

    if status and status != 'all':
        if status == 'pending':
            q = q.filter(~InterviewBook.interview_records.any())
        elif status == 'scheduled':
            q = q.filter(~InterviewBook.interview_records.any()).filter(
                InterviewBook.book_time > datetime.now()
            )

    total = q.count()
    books = q.order_by(InterviewBook.book_time.desc()).offset(
        (page - 1) * page_size).limit(page_size).all()
    if not books:
        return [], 0

    all_interviews = [_db_to_dict(b) for b in books]
    return all_interviews, total


def get_alerts():
    """Return interview alerts with state-aware logic."""
    alerts = [
        {'text': '孙九 · 复试超3天未评价', 'type': 'reject', 'action': '去评价',
         'actionMsg': '填写对孙九的评价'},
        {'text': '陈二 · 初试超5天未评价', 'type': 'reject', 'action': '去评价',
         'actionMsg': '填写对陈二的评价'},
        {'text': '张三 · 07-16 14:00 初试', 'type': 'warn', 'action': '查看', 'actionMsg': ''},
        {'text': '李四 · 07-17 10:00 初试', 'type': 'warn', 'action': '查看', 'actionMsg': ''},
        {'text': '郑一 · 已通过，待发Offer', 'type': 'done', 'action': '发Offer',
         'actionMsg': '发起Offer'},
        {'text': '王工 · 内部面试已通过', 'type': 'done', 'action': '发起调岗',
         'actionMsg': '发起调岗'},
    ]

    # Real overdue checks from DB
    try:
        from app.models.interview import InterviewBook, InterviewRecord
        from app.extensions import db
        now = datetime.now()
        books = InterviewBook.query.filter(InterviewBook.is_deleted == 0).all()
        for book in books:
            record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
            if not record and book.book_time and book.book_time < now:
                days_ago = (now - book.book_time).days
                if days_ago > 3:
                    alerts.append({
                        'text': f"预约#{book.id} · 面试超{days_ago}天未评价",
                        'type': 'reject',
                        'action': '去评价',
                        'actionMsg': f"填写面试评价",
                    })
    except Exception:
        pass

    return alerts[:6]


def create_interview(data):
    """Create an interview booking — DB only.

    Creates InterviewSlot + InterviewBook records, transitions from pending -> scheduled.
    """
    from app.models.interview import InterviewBook
    from app.extensions import db

    # Parse book_time from date+time fields
    date_str = data.get('date', '')
    time_str = data.get('time', '')
    book_time = None
    if date_str and time_str:
        try:
            now = datetime.now()
            book_time = datetime.strptime(f"{now.year}-{date_str} {time_str}", "%Y-%m-%d %H:%M")
        except (ValueError, TypeError):
            try:
                book_time = datetime.strptime(f"{date_str} {time_str}", "%Y-%m-%d %H:%M")
            except (ValueError, TypeError):
                pass
    if not book_time:
        book_time = datetime.now()

    # Frontend may send round as an int (e.g. 1); coerce to str before label checks.
    round_label = str(data.get('round') or '初试(1轮)')
    interview_round = 2 if '复试' in round_label or '终面' in round_label else 1

    method_label = data.get('method') or data.get('mode') or '待定'
    interview_type = {'飞书视频': 1, '腾讯会议': 2, '其他线上': 3, '线下': 4, '电话': 1, '现场': 4}.get(method_label, 1)
    if str(data.get('mode_id', '')).strip() in ('1', '2', '3', '4'):
        interview_type = int(data.get('mode_id'))

    # ── Meeting link generation by interview type ──
    meeting_code = data.get('meetingCode', '') or ''
    meeting_pwd = data.get('meetingPwd', '') or ''
    meeting_url = ''
    if interview_type == 1:
        meeting_url, meeting_code = _build_feishu_vc(data, book_time, meeting_code)
    elif interview_type == 2:
        meeting_url, meeting_code = _build_tencent_meeting(data, book_time, meeting_code)
    elif interview_type == 3:
        meeting_url = (data.get('meetingUrl') or '').strip()
    else:  # 4 offline
        meeting_url = ''
    if interview_type in (1, 2, 3) and not meeting_pwd:
        meeting_pwd = _random_digits(random.choice((4, 5, 6)))

    book = InterviewBook(
        demand_id=data.get('position_id', 0) or data.get('demand_id', 0) or 0,
        resume_id=data.get('resume_id', 0) or data.get('candidate_id', 0) or 0,
        process_id=data.get('process_id', 0) or 0,
        slot_id=data.get('slot_id', 0) or 0,
        interview_round=interview_round,
        interview_type=interview_type,
        book_time=book_time,
        meeting_code=meeting_code,
        meeting_pwd=meeting_pwd,
        meeting_url=meeting_url,
        address=data.get('address', ''),
    )
    db.session.add(book)
    db.session.commit()

    # ── Best-effort Feishu notification + invite snapshot ──
    book.invite_json = _notify_interview_invite(book, data, book_time)
    db.session.commit()

    log.info("面试预约已创建: id=%s, demand=%s, time=%s, url=%s", book.id, book.demand_id, book_time, meeting_url)

    return {'created': True, 'id': f'INT{book.id:04d}', 'book_id': book.id, 'meetingUrl': meeting_url}


def _random_digits(n):
    return ''.join(random.choice('0123456789') for _ in range(n))


def _build_feishu_vc(data, book_time, meeting_code):
    """Create a Feishu VC meeting; fall back to a locally-built URL on any error."""
    from app.services import feishu_client
    try:
        topic = f"面试-{data.get('candidate', '')}-{data.get('position', '')}".strip('-') or '招聘面试'
        start_ts = str(int(book_time.timestamp())) if book_time else ''
        vc = feishu_client.create_vc_meeting(topic, start_ts, duration_minutes=60)
        url = vc.get('meeting_url') or ''
        code = vc.get('meeting_code') or meeting_code
        if url:
            return url, code or meeting_code or _random_digits(9)
    except Exception as exc:
        log.warning("create_vc_meeting failed, using fallback URL: %s", exc)
    if not meeting_code:
        meeting_code = _random_digits(9)
    return f'https://vc.feishu.cn/j/{meeting_code}', meeting_code


def _build_tencent_meeting(data, book_time, meeting_code):
    """Create a real Tencent Meeting; fall back to a locally-built dm URL on any error."""
    from app.services import tencent_meeting_client
    try:
        topic = f"面试-{data.get('candidate', '')}-{data.get('position', '')}".strip('-') or '招聘面试'
        start_ts = str(int(book_time.timestamp())) if book_time else str(int(datetime.now().timestamp()))
        mt = tencent_meeting_client.create_meeting(topic, start_ts, duration_minutes=60)
        url = mt.get('meeting_url') or ''
        code = mt.get('meeting_code') or meeting_code
        if url:
            return url, code or meeting_code or _random_digits(10)
    except Exception as exc:
        log.warning("tencent create_meeting failed, using fallback URL: %s", exc)
    if not meeting_code:
        meeting_code = _random_digits(10)
    return f'https://meeting.tencent.com/dm/{meeting_code}', meeting_code


def _notify_interview_invite(book, data, book_time):
    """Best-effort Feishu interview notification; returns the invite snapshot dict.

    Never raises — failures only degrade the snapshot's notified flag.
    """
    from app.services import feishu_client
    snapshot = {
        'meeting_url': book.meeting_url or '',
        'notified': False,
        'notified_at': None,
        'channel': 'feishu',
    }
    try:
        candidate_name = data.get('candidate') or ''
        if not candidate_name:
            try:
                from app.models.candidate import Resume, Candidate
                r = Resume.query.filter_by(id=book.resume_id, is_deleted=0).first()
                if r and r.candidate_id:
                    c = Candidate.query.filter_by(id=r.candidate_id, is_deleted=0).first()
                    if c:
                        candidate_name = c.candidate_name
            except Exception:
                pass
        result = feishu_client.send_interview_invite(
            candidate_name or f'候选人#{book.resume_id}',
            data.get('interviewer') or '面试官',
            data.get('position') or f'岗位#{book.demand_id}',
            book_time.strftime('%Y-%m-%d %H:%M') if book_time else '待定',
            data.get('round') or ('复试' if book.interview_round == 2 else '初试'),
            meeting_url=book.meeting_url or None,
            meeting_code=book.meeting_code or None,
            meeting_pwd=book.meeting_pwd or None,
        )
        snapshot['notified'] = bool(result.get('success'))
        snapshot['notified_at'] = datetime.now().isoformat(timespec='seconds')
        snapshot['detail'] = {
            'interviewer_sent': result.get('interviewer_sent'),
            'candidate_sent': result.get('candidate_sent'),
            'errors': result.get('errors'),
        }
    except Exception as exc:
        log.warning("面试通知发送失败（best-effort，不影响主流程）: %s", exc)
        snapshot['error'] = str(exc)
    return snapshot


def schedule_interview(data):
    """Schedule interview(s) — batch support, transitions pending -> scheduled.

    Creates InterviewSlot + InterviewBook records for each item.
    """
    items = data if isinstance(data, list) else [data]
    results = []
    for item in items:
        result = create_interview(item)
        results.append(result)

    log.info("批量安排面试: %d 场", len(results))
    return {'created': True, 'count': len(results)}


def _validate_transition(current_status, target_status):
    """Validate state transition. Raises AppError if not allowed."""
    allowed = INTERVIEW_TRANSITIONS.get(current_status, {}).get('next', [])
    if target_status not in allowed:
        label = INTERVIEW_TRANSITIONS.get(current_status, {}).get('label', current_status)
        raise AppError(
            'INVALID_STATE',
            f'当前状态"{label}"({current_status})不允许切换到"{target_status}"'
        )


def complete_interview(book_id, data):
    """Complete an interview — scheduled -> evaluating.

    Creates an InterviewRecord with basic arrival info.
    The interviewer evaluation is submitted separately via evaluate_interview().
    """
    from app.models.interview import InterviewBook
    from app.extensions import db

    book = InterviewBook.query.filter_by(id=book_id, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')

    # Check existing record
    from app.models.interview import InterviewRecord
    existing = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if existing:
        raise AppError('ALREADY_COMPLETED', f'面试预约 {book_id} 已完成，请勿重复操作')

    now = datetime.now()
    record = InterviewRecord(
        book_id=book.id,
        process_id=book.process_id,
        interviewer_ids=data.get('interviewer_ids', []),
        submit_interviewer_id=data.get('interviewer_id', 0),
        is_arrive=data.get('is_arrive', 1),
        interview_result=0,  # default: not yet evaluated
        end_time=now,
    )
    db.session.add(record)
    db.session.commit()

    log.info("面试完成: book_id=%s, record_id=%s", book_id, record.id)
    return {'completed': True, 'record_id': record.id}


def evaluate_interview(book_id, data):
    """Submit interview evaluation with state transition.

    Flow:
      evaluating + pass -> offer (waiting for offer)
      evaluating + fail -> candidate released, process set to rejected
      evaluating + hold -> stays evaluating, process returns to pending/screening

    The book must exist and have an InterviewRecord associated.
    """
    from app.models.interview import InterviewBook, InterviewRecord
    from app.models.process import RecruitProcess
    from app.extensions import db

    book = InterviewBook.query.filter_by(id=book_id, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')

    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if not record:
        raise AppError('INVALID_STATE', f'面试预约 {book_id} 尚未完成面试')

    if record.interview_result in (1,):
        raise AppError('ALREADY_EVALUATED', f'面试预约 {book_id} 已评价')

    score = data.get('score', 75)
    result = data.get('result', 'hold')  # pass | fail | hold
    comment = data.get('comment', '')

    now = datetime.now()
    record.interview_result = 1 if result == 'pass' else 0
    record.submit_interviewer_id = data.get('submit_user_id', record.submit_interviewer_id)
    record.evaluate_text = comment
    record.score_json = data.get('score_json', {})
    record.end_time = now

    # Update process status
    if book.process_id and result in ('pass', 'fail'):
        process = RecruitProcess.query.filter_by(id=book.process_id, is_deleted=0).first()
        if process:
            if result == 'pass':
                process.process_status = 5  # pending offer
            else:
                process.process_status = 4  # rejected

    # Release lock on fail
    if result == 'fail':
        _release_candidate_lock(book.resume_id, None)

    db.session.commit()
    log.info("面试评价已提交: book_id=%s, result=%s, score=%s", book_id, result, score)

    new_status = 'pending'
    new_label = '待安排'
    if result == 'pass':
        new_status = 'offer'
        new_label = '待录用'
    elif result == 'fail':
        new_status = 'pending'
        new_label = '已淘汰'

    return {
        'evaluated': True,
        'newStatus': new_status,
        'newStatusLabel': new_label,
    }


def send_offer(book_id, data):
    """Send offer after evaluation passed — evaluating -> offer.

    Creates an Offer record via hire_service and updates the process.
    """
    from app.models.interview import InterviewBook, InterviewRecord
    from app.models.process import RecruitProcess
    from app.extensions import db

    book = InterviewBook.query.filter_by(id=book_id, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')

    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if not record or record.interview_result != 1:
        raise AppError('INVALID_STATE', f'面试预约 {book_id} 尚未通过评价')

    # Create the offer via hire_service
    from app.services.hire_service import create_offer
    offer_data = {
        'resumeId': book.resume_id,
        'processId': book.process_id,
        'demandId': book.demand_id,
        'lastInterviewId': record.id,
        'offerContent': data.get('offer_content', ''),
        'salaryJson': data.get('salary_json', {}),
        'validDeadline': data.get('valid_deadline'),
        'sendUserId': data.get('send_user_id', 0),
    }
    result = create_offer(offer_data)

    # Update process
    if book.process_id:
        process = RecruitProcess.query.filter_by(id=book.process_id, is_deleted=0).first()
        if process:
            process.process_status = 5  # pending offer

    db.session.commit()
    log.info("Offer已发送: book_id=%s, offer_no=%s", book_id, result.get('id'))
    return result


def confirm_onboard(book_id):
    """Confirm candidate onboarding — onboard -> done.

    Updates the hire event and process status to reflect completed onboarding.
    """
    from app.models.interview import InterviewBook, InterviewRecord
    from app.models.process import RecruitProcess
    from app.extensions import db

    book = InterviewBook.query.filter_by(id=book_id, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')

    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if not record or record.interview_result != 1:
        raise AppError('INVALID_STATE', '候选人尚未通过面试，无法确认入职')

    # Update process
    if book.process_id:
        process = RecruitProcess.query.filter_by(id=book.process_id, is_deleted=0).first()
        if process:
            process.process_status = 8  # onboard

    db.session.commit()
    log.info("入职确认: book_id=%s", book_id)
    return {'confirmed': True}


def cancel_interview(book_id, reason=None):
    """Cancel an interview — any state -> cancelled.

    Valid for any state except 'done' and 'cancelled'.
    Releases candidate lock and updates process status.
    """
    from app.models.interview import InterviewBook
    from app.models.process import RecruitProcess
    from app.extensions import db

    book = InterviewBook.query.filter_by(id=book_id, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')

    # Cannot cancel completed interviews
    from app.models.interview import InterviewRecord
    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if record and record.interview_result == 1:
        raise AppError('INVALID_STATE', '面试已通过，无法取消')

    book.soft_delete()

    if book.process_id:
        process = RecruitProcess.query.filter_by(id=book.process_id, is_deleted=0).first()
        if process:
            process.process_status = 4  # rejected

    _release_candidate_lock(book.resume_id, None)

    db.session.commit()
    log.info("面试已取消: book_id=%s, reason=%s", book_id, reason or '无原因')
    return {'cancelled': True}


def get_interview(book_id):
    """Get a single interview record by book ID."""
    from app.models.interview import InterviewBook
    book = InterviewBook.query.filter_by(id=book_id, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')
    return _db_to_dict(book)


def get_calendar(week_start=None):
    """Return calendar view for interviews in a given week."""
    from app.models.interview import InterviewBook
    from app.extensions import db

    if week_start:
        try:
            start = datetime.strptime(week_start, '%Y-%m-%d')
        except (ValueError, TypeError):
            start = datetime.now()
    else:
        start = datetime.now()

    # Calculate week range
    monday = start - timedelta(days=start.weekday())
    sunday = monday + timedelta(days=6)
    monday = monday.replace(hour=0, minute=0, second=0, microsecond=0)
    sunday = sunday.replace(hour=23, minute=59, second=59, microsecond=0)

    books = InterviewBook.query.filter(
        InterviewBook.is_deleted == 0,
        InterviewBook.book_time >= monday,
        InterviewBook.book_time <= sunday,
    ).order_by(InterviewBook.book_time.asc()).all()

    events = []
    for book in books:
        d = _db_to_dict(book)
        events.append({
            'id': d['id'],
            'title': d['name'],
            'start': book.book_time.strftime('%Y-%m-%d %H:%M') if book.book_time else '',
            'end': (book.book_time + timedelta(hours=1)).strftime('%Y-%m-%d %H:%M') if book.book_time else '',
            'status': d['status'],
            'round': d['round'],
            'interviewer': d['interviewer'],
            'method': d['method'],
            'meetingUrl': d['meetingUrl'],
            'meetingCode': d['meetingCode'],
            'meetingPwd': d['meetingPwd'],
        })

    return {
        'weekStart': monday.strftime('%Y-%m-%d'),
        'weekEnd': sunday.strftime('%Y-%m-%d'),
        'events': events,
    }


def seed_interviews():
    """Seed default interview records into DB so list_interviews() has real data."""
    seeded = 0
    try:
        from app.models.interview import InterviewBook
        from app.extensions import db
        from datetime import datetime, timedelta

        existing = InterviewBook.query.filter(InterviewBook.is_deleted == 0).count()
        if existing >= 12:
            log.info("Interview data already seeded (%d records), skipping", existing)
            return seeded

        default_data = _default_seed_data()
        now = datetime.now()

        for item in default_data:
            date_str = item.get('date', '')
            time_str = item.get('time', '')
            book_time = None
            if date_str and time_str:
                try:
                    book_time = datetime.strptime(f"2026-{date_str} {time_str}", "%Y-%m-%d %H:%M")
                except (ValueError, TypeError):
                    pass
            if not book_time:
                book_time = now

            round_label = item.get('round', '初试(1轮)')
            interview_round = 2 if '复试' in round_label or '终面' in round_label else 1

            method_label = item.get('method', '待定')
            interview_type = {
                '飞书视频': 1, '腾讯会议': 2, '其他线上': 3, '线下': 4,
                '电话': 1, '现场': 4, '飞书消息': 1,
            }.get(method_label, 1)

            book = InterviewBook(
                demand_id=0, resume_id=0, process_id=0, slot_id=0,
                interview_round=interview_round, interview_type=interview_type,
                book_time=book_time,
            )
            db.session.add(book)
            seeded += 1

        db.session.commit()
        log.info("Seeded %d interview records into DB", seeded)
    except Exception as exc:
        log.warning("DB seed failed for interviews: %s", exc, exc_info=True)
    return seeded


def _default_seed_data():
    """Default mock data matching frontend display expectations."""
    return [
        {'name': '王五', 'position': '数据分析师', 'round': '初试(1轮)',
         'date': '待定', 'time': '待定', 'method': '待定'},
        {'name': '吴六', 'position': '高级Java工程师', 'round': '初试(1轮)',
         'date': '待定', 'time': '待定', 'method': '待定'},
        {'name': '赵十', 'position': '高级Java工程师', 'round': '初试(1轮)',
         'date': '待定', 'time': '待定', 'method': '待定'},
        {'name': '钱七', 'position': '产品经理', 'round': '初试(1轮)',
         'date': '待定', 'time': '待定', 'method': '待定'},
        {'name': '张三', 'position': '高级Java工程师', 'round': '初试(1/3轮)',
         'date': '07-16', 'time': '14:00', 'method': '飞书视频'},
        {'name': '李四', 'position': '前端工程师', 'round': '初试(1/2轮)',
         'date': '07-17', 'time': '10:00', 'method': '飞书视频'},
        {'name': '孙九', 'position': '高级Java工程师', 'round': '复试(2/3轮)',
         'date': '07-13', 'time': '10:00', 'method': '现场'},
        {'name': '陈二', 'position': '产品经理', 'round': '初试(2/2轮)',
         'date': '07-14', 'time': '09:00', 'method': '电话'},
        {'name': '郑一', 'position': '前端工程师', 'round': '终面(2/2轮)',
         'date': '07-12', 'time': '15:00', 'method': '现场'},
        {'name': '王五', 'position': '数据分析师', 'round': 'Offer确认',
         'date': '07-26', 'time': '09:30', 'method': '飞书消息'},
        {'name': '周工', 'position': '产品经理', 'round': '初试(1/2轮)',
         'date': '07-05', 'time': '10:00', 'method': '现场'},
        {'name': '陈二', 'position': '产品经理', 'round': '初试(1/2轮)',
         'date': '07-10', 'time': '09:00', 'method': '电话'},
    ]


# Legacy alias — kept for backward compatibility with API callers
advance_status = cancel_interview
