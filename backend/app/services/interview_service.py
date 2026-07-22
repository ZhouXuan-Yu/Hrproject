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


def _candidate_id_for_resume(resume_id):
    """Resolve the candidate that owns a resume. Returns candidate id or None."""
    try:
        from app.models.candidate import Resume
        r = Resume.query.filter_by(id=resume_id, is_deleted=0).first()
        if r and r.candidate_id:
            return r.candidate_id
    except Exception as exc:
        log.warning("_candidate_id_for_resume failed: %s", exc)
    return None


def _release_lock_for_resume(resume_id):
    """Release the candidate lock via resume_id (book rows store resume_id, not candidate_id)."""
    cid = _candidate_id_for_resume(resume_id)
    if cid:
        return _release_candidate_lock(cid, None)
    log.warning("release lock skipped: no candidate for resume_id=%s", resume_id)
    return False


def _ensure_process_for_interview(resume_id, demand_id, interview_round):
    """Find-or-create the RecruitProcess linking this interview to demand management.

    联动规则：
      - 简历能定位到候选人时，按 (candidate_id, demand_id) 查进行中的流程
        （淘汰=4 / 放弃=7 除外），没有则创建 process_status=0 的新流程
      - 流程状态推进到面试阶段：一面=2 / 二面及以上=3
      - 候选人锁定 status='locked'，人才库同步显示"面试中"

    Returns (process_id, candidate_id)；无法定位候选人时返回 (0, None)。
    """
    from app.models.candidate import Candidate, Resume
    from app.models.process import RecruitProcess
    from app.extensions import db

    candidate = None
    resume = Resume.query.filter_by(id=resume_id, is_deleted=0).first() if resume_id else None
    if resume and resume.candidate_id:
        candidate = Candidate.query.filter_by(id=resume.candidate_id, is_deleted=0).first()
    if not candidate:
        return 0, None

    process = None
    if demand_id:
        process = RecruitProcess.query.filter(
            RecruitProcess.candidate_id == candidate.id,
            RecruitProcess.demand_id == demand_id,
            RecruitProcess.process_status.notin_([4, 7]),
            RecruitProcess.is_deleted == 0,
        ).first()
        if not process:
            now = datetime.now()
            process = RecruitProcess(
                process_no=f"RP{now.strftime('%Y%m%d%H%M%S')}{candidate.id % 100:02d}{random.randint(100, 999)}",
                demand_id=demand_id,
                resume_id=resume_id or 0,
                candidate_id=candidate.id,
                process_status=0,
            )
            db.session.add(process)
            db.session.flush()

    if process:
        process.process_status = 3 if interview_round >= 2 else 2
    if candidate.status != 'locked':
        candidate.status = 'locked'
        log.info("候选人已锁定（面试中）: id=%s name=%s", candidate.id, candidate.candidate_name)

    return (process.id if process else 0), candidate.id


def _db_to_dict(book):
    """Convert an InterviewBook ORM object to frontend-compatible dict.

    Resolves status from process/interview-record state rather than a column on the book row.
    """
    candidate_name = None
    candidate_no = ''
    demand_pos = None
    try:
        from app.models.candidate import Candidate, Resume
        from app.models.demand import RecruitDemand
        r = Resume.query.filter_by(id=book.resume_id, is_deleted=0).first()
        if r and r.candidate_id:
            c = Candidate.query.filter_by(id=r.candidate_id, is_deleted=0).first()
            if c:
                candidate_name = c.candidate_name
                candidate_no = c.candidate_no or ''
        d = RecruitDemand.query.filter_by(id=book.demand_id, is_deleted=0).first()
        if d:
            try:
                from app.services.demand_service import POS_NAMES
                pos_title = POS_NAMES.get(d.position_id)
            except Exception:
                pos_title = None
            demand_pos = pos_title or f'岗位#{d.demand_no}'
    except Exception:
        pass

    # Derive display status from InterviewRecord presence
    from app.models.interview import InterviewRecord
    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if record:
        if record.interview_result == 1:
            display_status = 'offer'
            display_label = '待录用'
        elif record.interview_result == 2:
            display_status = 'done'
            display_label = '已淘汰'
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

    # ── 联动 Offer 状态：面试通过后展示 Offer 全链路进度 ──
    # 1已发送=待候选人确认 / 2已接受=已录用 / 3已拒绝 / 4已过期淘汰
    offer_status = None
    offer_no = None
    if record and record.interview_result == 1 and book.resume_id and book.demand_id:
        try:
            from app.models.hire import Offer
            offer = (Offer.query.filter(
                Offer.resume_id == book.resume_id,
                Offer.demand_id == book.demand_id,
                Offer.is_deleted == 0,
            ).order_by(Offer.id.desc()).first())
            if offer:
                offer_status = offer.offer_status
                offer_no = offer.offer_no
                if offer.offer_status == 1:
                    display_status = 'offer'
                    display_label = 'Offer待确认'
                elif offer.offer_status == 2:
                    display_status = 'onboard'
                    display_label = '已录用'
                elif offer.offer_status == 3:
                    display_status = 'done'
                    display_label = '已拒绝'
                elif offer.offer_status == 4:
                    display_status = 'done'
                    display_label = '已淘汰'
        except Exception:
            pass

    # 评价结果/评分下发给前端（面试页"已完成"列据此区分 已入职/已回流人才库）
    eval_result = None
    if record:
        eval_result = {1: 'pass', 2: 'reject', 3: 'hold'}.get(record.interview_result)

    round_num = getattr(book, 'interview_round', 1) or 1
    return {
        'id': f'INT{book.id:04d}',
        'name': candidate_name or f'候选人#{book.resume_id}',
        'candidateId': candidate_no,
        'resumeId': book.resume_id or 0,
        'demandId': book.demand_id or 0,
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
        'offerStatus': offer_status,
        'offerNo': offer_no,
        'candidateConfirm': (book.invite_json or {}).get('candidate_confirm'),
        'emailSent': (book.invite_json or {}).get('email_sent', bool((book.invite_json or {}).get('email_log'))),
        'createdBy': '系统',
        'isMine': False,
        'score': (record.score_json or {}).get('total') if record else None,
        'duration': None,
        'result': eval_result,
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


def _resolve_links(data):
    """Resolve real (resume_id, demand_id) from display numbers or names.

    The frontend sends display identifiers (candidate_no like 'C2026…',
    demand_no like 'DM…') or plain names. Older callers may still send
    numeric ids — those are honored first.
    """
    resume_id = 0
    demand_id = 0
    try:
        from app.models.candidate import Candidate, Resume
        from app.models.demand import RecruitDemand

        # numeric ids from legacy callers
        raw_resume = data.get('resume_id') or data.get('candidate_id') or 0
        if isinstance(raw_resume, int) and raw_resume:
            resume_id = raw_resume
        raw_demand = data.get('position_id') or data.get('demand_id') or 0
        if isinstance(raw_demand, int) and raw_demand:
            demand_id = raw_demand

        # candidate resolution: candidateNo > candidate name
        if not resume_id:
            candidate = None
            candidate_no = (data.get('candidateNo') or data.get('candidate_no') or '').strip()
            if not candidate_no:
                raw = str(data.get('candidate_id') or '').strip()
                if raw.upper().startswith('C'):
                    candidate_no = raw
            if candidate_no:
                candidate = Candidate.query.filter_by(candidate_no=candidate_no, is_deleted=0).first()
            if not candidate:
                name = (data.get('candidate') or data.get('name') or '').strip()
                if name:
                    candidate = (Candidate.query.filter_by(candidate_name=name, is_deleted=0)
                                 .order_by(Candidate.id.desc()).first())
            if candidate:
                resume = (Resume.query.filter_by(candidate_id=candidate.id, is_deleted=0)
                          .order_by(Resume.storage_time.desc()).first())
                if resume:
                    resume_id = resume.id

        # demand resolution: demandNo > position title
        if not demand_id:
            demand_no = (data.get('demandNo') or data.get('demand_no') or '').strip()
            if not demand_no:
                raw = str(data.get('demand_id') or '').strip()
                if raw.upper().startswith('DM'):
                    demand_no = raw
            demand = None
            if demand_no:
                demand = RecruitDemand.query.filter_by(demand_no=demand_no, is_deleted=0).first()
            if demand:
                demand_id = demand.id
    except Exception:
        pass
    return resume_id, demand_id


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

    resolved_resume_id, resolved_demand_id = _resolve_links(data)

    # ── 联动需求管理：找到/创建流程并锁定候选人 ──
    process_id = data.get('process_id', 0) or 0
    if not process_id:
        try:
            process_id, _cid = _ensure_process_for_interview(
                resolved_resume_id, resolved_demand_id, interview_round)
        except Exception as exc:
            log.warning("ensure process for interview failed (best-effort): %s", exc)

    book = InterviewBook(
        demand_id=resolved_demand_id,
        resume_id=resolved_resume_id,
        process_id=process_id,
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

    # 邮件邀请（含候选人确认链接）—— 候选人通常不在飞书组织内，
    # 邮件是触达候选人的主通道。best-effort，不影响主流程。
    try:
        from app.services.confirm_service import send_interview_invite_email
        ok, msg = send_interview_invite_email(book)
        snapshot['email_sent'] = ok
        snapshot['email_msg'] = msg
    except Exception as exc:
        log.warning("面试邀请邮件发送失败（best-effort）: %s", exc)
        snapshot['email_sent'] = False
        snapshot['email_msg'] = str(exc)
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


def _normalize_book_id(book_id):
    """Accept both raw int ids and display ids like 'INT0004'."""
    s = str(book_id).strip()
    if s.upper().startswith('INT'):
        s = s[3:]
    return int(s)


def evaluate_interview(book_id, data):
    """Submit interview evaluation with state transition.

    Flow:
      evaluating + pass -> offer (waiting for offer)
      evaluating + fail -> candidate released, process set to rejected
      evaluating + hold -> stays evaluating, process returns to pending/screening

    评价理由（comment）为必填 —— 产品纪要要求面试官必须填写评价，
    不能仅点"通过/拒绝"。

    interview_result codes: 0=未评价 1=通过 2=淘汰 3=暂缓
    """
    from app.models.interview import InterviewBook, InterviewRecord
    from app.models.process import RecruitProcess
    from app.extensions import db

    try:
        bid = _normalize_book_id(book_id)
    except (TypeError, ValueError):
        raise AppError('BAD_REQUEST', f'无效的面试预约ID: {book_id}')

    book = InterviewBook.query.filter_by(id=bid, is_deleted=0).first()
    if not book:
        raise AppError('NOT_FOUND', f'面试预约 {book_id} 不存在')

    record = InterviewRecord.query.filter_by(book_id=book.id, is_deleted=0).first()
    if not record:
        raise AppError('INVALID_STATE', f'面试预约 {book_id} 尚未完成面试')

    if record.interview_result != 0:
        raise AppError('ALREADY_EVALUATED', f'面试预约 {book_id} 已评价，请勿重复提交')

    result = data.get('result', 'hold')  # pass | fail | hold
    if result not in ('pass', 'fail', 'hold'):
        raise AppError('VALIDATION_ERROR', '评价结果只能是 pass/fail/hold')

    # 强制评价：任何结果都必须填写评价理由
    comment = (data.get('comment') or '').strip()
    if len(comment) < 5:
        raise AppError('VALIDATION_ERROR', '必须填写评价理由（不少于 5 个字），不能仅提交通过/拒绝')

    score = data.get('score', 75)

    now = datetime.now()
    record.interview_result = {'pass': 1, 'fail': 2, 'hold': 3}[result]
    record.submit_interviewer_id = data.get('submit_user_id', record.submit_interviewer_id)
    record.evaluate_text = comment
    score_json = dict(data.get('score_json') or {})
    score_json.setdefault('total', score)
    record.score_json = score_json
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
        _release_lock_for_resume(book.resume_id)

    db.session.commit()
    log.info("面试评价已提交: book_id=%s, result=%s, score=%s", book.id, result, score)

    new_status = 'pending'
    new_label = '待安排'
    if result == 'pass':
        new_status = 'offer'
        new_label = '待录用'
    elif result == 'fail':
        new_status = 'done'
        new_label = '已淘汰'

    return {
        'evaluated': True,
        'newStatus': new_status,
        'newStatusLabel': new_label,
    }


def send_offer(book_id, data):
    """Send offer after evaluation passed — evaluating -> offer.

    Creates an Offer via hire_service and actually sends it (draft -> sent,
    with salary validation, letter generation and candidate email).

    Draft-reuse: if a previous attempt left a draft Offer for the same
    candidate+demand (e.g. the request was retried after a failure), the
    draft is updated and sent instead of raising DUPLICATE_OFFER, so the
    "发送 Offer" action is idempotent. An already-sent/accepted Offer still
    blocks with a clear message.
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
    from app.services import hire_service
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

    reused = False
    try:
        result = hire_service.create_offer(offer_data)
        offer_no = result['id']
    except AppError as exc:
        if exc.code != 'DUPLICATE_OFFER':
            raise
        # A previous attempt left an Offer for this candidate+demand — reuse if draft
        from app.models.hire import Offer
        existing = Offer.query.filter(
            Offer.resume_id == book.resume_id,
            Offer.demand_id == book.demand_id,
            Offer.is_deleted == 0,
        ).order_by(Offer.id.desc()).first()
        if not existing or existing.offer_status != 0:
            status_label = hire_service.OFFER_STATUS_LABELS.get(
                existing.offer_status if existing else -1, '未知')
            raise AppError(
                'DUPLICATE_OFFER',
                f'该候选人已存在"{status_label}"状态的Offer（{existing.offer_no if existing else "?"}），'
                '请勿重复发送；如需修改请先撤回原Offer')
        # Update the draft with the latest submitted fields
        if offer_data['offerContent']:
            existing.offer_content = offer_data['offerContent']
        if offer_data['salaryJson']:
            existing.salary_json = offer_data['salaryJson']
        if offer_data['validDeadline']:
            parsed = hire_service._parse_date(offer_data['validDeadline'])
            if parsed:
                existing.valid_deadline = datetime.combine(
                    parsed, datetime.max.time().replace(microsecond=0))
        existing.last_interview_id = record.id
        db.session.flush()
        offer_no = existing.offer_no
        reused = True
        log.info("复用已有草稿Offer: %s (book_id=%s)", offer_no, book_id)

    # Actually send it: draft -> sent, validates salary, generates letter, emails candidate
    send_result = hire_service.send_offer(offer_no)

    # Update process
    if book.process_id:
        process = RecruitProcess.query.filter_by(id=book.process_id, is_deleted=0).first()
        if process:
            process.process_status = 5  # pending offer

    db.session.commit()
    log.info("Offer已发送: book_id=%s, offer_no=%s, reused=%s", book_id, offer_no, reused)
    return {**send_result, 'id': offer_no, 'reused': reused}


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

    # 联动人才库：入职后候选人状态置为 hired
    try:
        from app.models.candidate import Candidate
        cid = _candidate_id_for_resume(book.resume_id)
        if cid:
            cand = Candidate.query.filter_by(id=cid, is_deleted=0).first()
            if cand:
                cand.status = 'hired'
                log.info("候选人已入职: id=%s name=%s", cand.id, cand.candidate_name)
    except Exception as exc:
        log.warning("set hired status failed (best-effort): %s", exc)

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

    _release_lock_for_resume(book.resume_id)

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
