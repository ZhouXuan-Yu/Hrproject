"""Demand service: CRUD + approval flow + status state machine.

Status transitions:
  draft(0) -> approval(1) -> open(2) / rejected(3)
  open(2) -> closed(4) or fulfilled
  Any -> cancelled(5)

Validations:
  - Cannot delete/close a demand with active interviews or pending offers
  - Closing auto-releases linked candidates
"""
import logging
from app.utils.response import AppError
from app.utils.enums import DemandStatus, DemandUrgency
from app.utils.scoring import candidate_profile_score

log = logging.getLogger(__name__)


def _mock_enabled():
    from app.utils.response import should_mock_fallback
    return should_mock_fallback()


STATUS_LABELS = {0: '草稿', 1: '审批中', 2: '招聘中', 3: '已驳回', 4: '已关闭', 5: '已取消'}
STATUS_TYPES = {0: 'draft', 1: 'warn', 2: 'progress', 3: 'reject', 4: 'draft', 5: 'draft'}
URGENCY_LABELS = {'very': '非常紧急', 'high': '紧急', 'normal': '普通'}
URGENCY_TYPES = {'very': 'reject', 'high': 'warn', 'normal': 'draft'}

# Allowed status transitions
DEMAND_TRANSITIONS = {
    0: [1, 5],       # draft -> approval / cancelled
    1: [2, 3, 5],    # approval -> open / rejected / cancelled
    2: [4, 5],       # open -> closed / cancelled
    3: [0, 5],       # rejected -> draft / cancelled
    4: [],           # closed: terminal
    5: [],           # cancelled: terminal
}

# Mapping from dept_id/position_id/user_id -> Chinese names
DEPT_NAMES = {1: '技术部', 2: '产品部', 3: '运营部', 4: '数据部', 5: '财务部'}
POS_NAMES = {1: '高级Java工程师', 2: '前端工程师', 3: '产品经理', 4: '运营总监', 5: '数据分析师'}
USER_NAMES = {1: '刘博', 2: '张HR', 3: '陈总', 4: '周博', 5: '李面试官', 6: '王面试官', 7: '赵博'}

EDU_LEVEL_LABELS = {1: '大专', 2: '本科', 3: '硕士', 4: '博士'}

DEPT_IDS = {v: k for k, v in DEPT_NAMES.items()}

_URGENCY_ALIAS = {
    '普通': 'normal', '紧急': 'high', '非常紧急': 'very',
    'normal': 'normal', 'high': 'high', 'very': 'very',
}


def _normalize_urgency(value):
    """Map frontend Chinese urgency labels to backend enum codes."""
    if not value:
        return 'normal'
    return _URGENCY_ALIAS.get(str(value).strip(), 'normal')


_name_cols_ensured = False


def _ensure_name_columns():
    """Best-effort ALTER TABLE to add position_name/dept_name for existing SQLite DBs.

    New databases get the columns via create_all / migrations; this only patches
    legacy hr_recruit.db files that predate the columns.
    """
    global _name_cols_ensured
    if _name_cols_ensured:
        return
    _name_cols_ensured = True
    try:
        from app.extensions import db
        from sqlalchemy import text, inspect
        insp = inspect(db.engine)
        cols = {c['name'] for c in insp.get_columns('t_hr_recruit_demand')}
        missing_defs = {
            'position_name': 'VARCHAR(128)',
            'dept_name': 'VARCHAR(64)',
            'salary_range': 'VARCHAR(64)',
            'urgency': "VARCHAR(16) DEFAULT 'normal'",
            'required_skills': 'JSON',
            'plus_skills': 'JSON',
            'work_city': 'VARCHAR(64)',
            'edu_min': 'VARCHAR(64)',
            'exp_min': 'INTEGER',
            'approved_at': 'DATETIME',
            'closed_at': 'DATETIME',
            'recommend_limit': 'INTEGER',
        }
        with db.engine.begin() as conn:
            for col, ddl in missing_defs.items():
                if col not in cols:
                    conn.execute(text(f'ALTER TABLE t_hr_recruit_demand ADD COLUMN {col} {ddl}'))
                    log.info("Added column %s to t_hr_recruit_demand", col)
    except Exception as exc:
        log.warning("ensure name columns failed (non-fatal): %s", exc)


def _validate_demand_transition(demand, target_status):
    """Validate demand status transition. Raises AppError on invalid transition."""
    allowed = DEMAND_TRANSITIONS.get(demand.demand_status, [])
    if target_status not in allowed:
        current_label = STATUS_LABELS.get(demand.demand_status, '未知')
        target_label = STATUS_LABELS.get(target_status, '未知')
        raise AppError(
            'INVALID_STATE',
            f'需求状态"{current_label}"({demand.demand_status})不允许切换到"{target_label}"({target_status})'
        )


def _has_active_interviews_or_offers(demand_id):
    """Check if a demand has active interviews (scheduled/evaluating) or pending offers.

    Returns True if active engagements exist.
    """
    try:
        from app.models.interview import InterviewBook, InterviewRecord
        from app.models.process import RecruitProcess

        # An interview is only active while it has not been evaluated, or it
        # was explicitly put on hold. Finished rejected interviews should not
        # block demand cleanup.
        books = InterviewBook.query.filter(
            InterviewBook.demand_id == demand_id,
            InterviewBook.is_deleted == 0,
        ).all()
        for book in books:
            record = InterviewRecord.query.filter(
                InterviewRecord.book_id == book.id,
                InterviewRecord.is_deleted == 0,
            ).order_by(InterviewRecord.id.desc()).first()
            if not record or record.interview_result in (0, 3):
                return True

        # Check for processes that are in interview/offer stages
        active_process = RecruitProcess.query.filter(
            RecruitProcess.demand_id == demand_id,
            RecruitProcess.process_status.in_([2, 3, 5]),  # interviewing or pending offer
            RecruitProcess.is_deleted == 0,
        ).count()
        if active_process > 0:
            return True

        return False
    except Exception as exc:
        log.warning("Failed to check active interviews: %s", exc)
        return False  # If check fails, allow


def _release_linked_candidates(demand_id):
    """Release all candidates linked to this demand by setting their process status to 'released'."""
    released = 0
    try:
        from app.models.process import RecruitProcess
        from app.extensions import db

        processes = RecruitProcess.query.filter(
            RecruitProcess.demand_id == demand_id,
            RecruitProcess.is_deleted == 0,
        ).all()

        for p in processes:
            p.process_status = 7  # giveup/released
            released += 1

        if released > 0:
            db.session.commit()
            log.info("Released %d linked candidates for demand %s", released, demand_id)
    except Exception as exc:
        log.warning("Failed to release linked candidates: %s", exc)
    return released


def _auto_fulfill_if_complete(demand):
    """Check if demand's headcount is fully filled and auto-close."""
    try:
        if demand.filled_count and demand.plan_headcount and demand.filled_count >= demand.plan_headcount:
            demand.demand_status = 4  # closed
            demand.closed_at = __import__('datetime').datetime.now()
            log.info("Demand %s auto-fulfilled: filled %d/%d", demand.demand_no, demand.filled_count, demand.plan_headcount)
            return True
    except Exception as exc:
        log.warning("Failed to auto-fulfill demand: %s", exc)
    return False


# ── Core API ──

def _extract_skills_from_jd(jd_content):
    """Extract skill-like phrases from JD content for requiredSkills/plusSkills."""
    if not jd_content:
        return [], []
    import re
    parts = re.split(r'[，,、；;、\n]', jd_content)
    parts = [p.strip().strip('·-*') for p in parts if len(p.strip()) > 2]
    tech_hints = [
        'Java', 'Spring', 'MySQL', 'Kubernetes', 'K8s', 'React', 'Vue',
        'Python', 'Go', 'Golang', 'Docker', 'Redis', 'MQ', '消息队列',
        '分布式', '微服务', '架构', '设计', '开发', '管理', '部署', '调优',
        '运维', 'DevOps', 'CI', 'CD', '容器化', '敏捷', 'Scrum',
        'SQL', 'NoSQL', 'MongoDB', 'Elasticsearch', 'Flask', 'Django',
        '前端', '后端', '全栈', '测试', '产品', '运营', '数据分析',
    ]
    skills = [p for p in parts if any(h in p for h in tech_hints)]
    if not skills:
        skills = [p for p in parts if len(p) > 4 and '。' not in p]
    if not skills:
        return [], []
    mid = max(1, len(skills) // 2)
    return skills[:mid], skills[mid:]


def _live_pipeline_counts(demand_id):
    """从 RecruitProcess 实时统计某需求的候选人漏斗计数。

    返回 dict: linked（进行中）/ interviewing（面试中）/ direct / recommend / internal。
    人才库「加入需求」、邮箱自动关联等路径都会建 RecruitProcess，需求列表页
    必须读实时计数而不是需求行上的静态字段，否则候选人永远显示为 0。
    """
    counts = {'linked': 0, 'interviewing': 0, 'direct': 0, 'recommend': 0, 'internal': 0}
    try:
        from app.models.process import RecruitProcess
        from app.models.candidate import Candidate

        processes = RecruitProcess.query.filter(
            RecruitProcess.demand_id == demand_id,
            RecruitProcess.is_deleted == 0,
        ).all()
        for p in processes:
            if p.process_status in (4, 7):  # 淘汰 / 放弃不计入进行中
                continue
            counts['linked'] += 1
            if p.process_status in (2, 3):
                counts['interviewing'] += 1
            cand = Candidate.query.filter_by(id=p.candidate_id, is_deleted=0).first()
            ch = (cand.source_channel if cand else '') or ''
            if ch in ('内推', '内部推荐'):
                counts['internal'] += 1
            elif ch in ('邮箱', 'Boss', '猎聘'):
                counts['direct'] += 1
            else:
                counts['recommend'] += 1
    except Exception as exc:
        log.warning("live pipeline counts failed for demand %s: %s", demand_id, exc)
    return counts


def _demand_to_dict(d):
    """Convert a RecruitDemand ORM object to API dict."""
    pos_name = getattr(d, 'position_name', None) or POS_NAMES.get(d.position_id, str(d.position_id))
    dept_name = getattr(d, 'dept_name', None) or DEPT_NAMES.get(d.dept_id, str(d.dept_id))
    submitter = USER_NAMES.get(d.creator_id, str(d.creator_id))

    # 实时流程计数（人才库加入 / 邮箱关联的候选人都会反映在这里）
    live = _live_pipeline_counts(d.id)

    st = d.demand_status
    result = {
        'id': d.demand_no,
        'position': pos_name,
        'dept': dept_name,
        'hc': d.plan_headcount,
        'urgency': d.urgency or 'normal',
        'urgencyLabel': URGENCY_LABELS.get(d.urgency, '普通'),
        'urgencyType': URGENCY_TYPES.get(d.urgency, 'draft'),
        'submitter': submitter,
        'status': {0: 'draft', 1: 'approval', 2: 'open', 3: 'rejected', 4: 'closed', 5: 'cancelled'}.get(st, 'draft'),
        'statusLabel': STATUS_LABELS.get(st, '草稿'),
        'statusType': STATUS_TYPES.get(st, 'draft'),
        'salary': d.salary_range or '',
        'date': d.expect_entry_date.strftime('%Y-%m-%d') if d.expect_entry_date else '',
        'desc': d.jd_content or '',
        'linkedCount': live['linked'],
    }

    if st == 1:  # approval — list only needs current progress, not completed timestamps after pass
        try:
            from app.services.approval_service import get_approval_progress
            live = get_approval_progress(d.id)
            result['approvalNodes'] = live if live else _default_approval_nodes(st)
        except Exception:
            result['approvalNodes'] = d.audit_flow or _default_approval_nodes(st)
    else:
        result['approvalNodes'] = []

    if st == 2:  # open
        result.update({
            'directApply': live['direct'],
            'systemRecommend': live['recommend'],
            'internalMatch': live['internal'],
            'internalNames': [],
            'interviewing': live['interviewing'],
        })

    return result


def _default_approval_nodes(status):
    if status == 0:  # draft
        return []
    nodes = [
        {'label': '部门负责人', 'state': 'done'},
        {'label': 'HR', 'state': 'pending'},
        {'label': '高管', 'state': 'pending'},
    ]
    if status == 2:  # approved/open
        nodes[0]['state'] = 'done'
        nodes[1]['state'] = 'done'
        nodes[2]['state'] = 'done'
    elif status == 1:  # approval
        nodes[0]['state'] = 'current'
    return nodes


def submit_for_approval(demand_id):
    """Submit a draft demand for approval — draft(0) -> approval(1).

    Creates approval nodes via approval_service.init_approval().
    """
    from app.models.demand import RecruitDemand
    from app.extensions import db

    _ensure_name_columns()

    d = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
    if not d:
        raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在')

    _validate_demand_transition(d, 1)  # draft -> approval

    d.demand_status = 1  # approval

    # Initialize approval records (idempotent — create_demand may have done it already)
    from app.services.approval_service import init_approval
    from app.models.demand import DemandApproval
    existing = DemandApproval.query.filter_by(demand_id=d.id, is_deleted=0).count()
    if existing == 0:
        init_approval(d.id)

    # Build audit_flow snapshot
    from app.services.approval_service import get_approval_progress
    d.audit_flow = get_approval_progress(d.id)

    db.session.commit()
    log.info("需求提交审批: %s", demand_id)

    return {'submitted': True, 'id': demand_id, 'status': 'approval'}


def list_demands(params):
    """Return paginated demand list — DB-level filters with SQL pagination."""
    page = max(1, int(params.get('page', 1)))
    page_size = min(100, max(1, int(params.get('pageSize', 20))))
    search = params.get('search', '').strip()
    status = params.get('status', 'all')
    urgency = params.get('urgency', 'all')

    try:
        from app.models.demand import RecruitDemand
        _ensure_name_columns()
        q = RecruitDemand.query.filter(RecruitDemand.is_deleted == 0)

        if search:
            sl = f'%{search}%'
            q = q.filter(RecruitDemand.demand_no.contains(search))
        if status and status != 'all':
            status_map = {'approval': 1, 'open': 2, 'draft': 0, 'closed': 4, 'rejected': 3, 'cancelled': 5}
            q = q.filter(RecruitDemand.demand_status == status_map.get(status))
        if urgency and urgency != 'all':
            q = q.filter(RecruitDemand.urgency == urgency)

        total = q.count()
        rows = q.order_by(RecruitDemand.id.desc()).offset((page - 1) * page_size).limit(page_size).all()
        if rows:
            demands = [_demand_to_dict(d) for d in rows]
            return demands, total
    except Exception as exc:
        log.error("DB query failed in list_demands: %s", exc, exc_info=True)
    return _mock_list_demands(params) if _mock_enabled() else ([], 0)


def _mock_list_demands(params):
    search = params.get('search', '').strip()
    status = params.get('status', 'all')
    urgency = params.get('urgency', 'all')

    all_demands = [
        {
            'id': 'DM2026070006', 'position': '运营总监', 'dept': '运营部', 'hc': 1,
            'urgency': 'very', 'urgencyLabel': '非常紧急', 'urgencyType': 'reject',
            'submitter': '陈总', 'status': 'approval', 'statusLabel': '审批中', 'statusType': 'warn',
            'approvalNodes': [
                {'label': '部门负责人', 'state': 'current'},
                {'label': 'HR', 'state': 'pending'},
                {'label': '高管', 'state': 'pending'},
            ],
            'linkedCount': 0, 'directApply': 0, 'systemRecommend': 0,
            'internalMatch': 0, 'internalNames': [], 'interviewing': 0,
        },
        {
            'id': 'DM2026070004', 'position': '产品经理', 'dept': '产品部', 'hc': 1,
            'urgency': 'normal', 'urgencyLabel': '普通', 'urgencyType': 'draft',
            'submitter': '周博', 'status': 'approval', 'statusLabel': '审批中', 'statusType': 'warn',
            'approvalNodes': [
                {'label': '部门负责人', 'state': 'done'},
                {'label': 'HR', 'state': 'current'},
                {'label': '高管', 'state': 'pending'},
            ],
            'linkedCount': 0, 'directApply': 0, 'systemRecommend': 0,
            'internalMatch': 0, 'internalNames': [], 'interviewing': 0,
        },
        {
            'id': 'DM2026070005', 'position': '高级Java工程师', 'dept': '技术部', 'hc': 2,
            'urgency': 'high', 'urgencyLabel': '紧急', 'urgencyType': 'warn',
            'submitter': '刘博', 'status': 'open', 'statusLabel': '招聘中', 'statusType': 'progress',
            'approvalNodes': [
                {'label': '部门负责人', 'state': 'done'},
                {'label': 'HR', 'state': 'done'},
                {'label': '高管', 'state': 'done'},
            ],
            'directApply': 4, 'systemRecommend': 5, 'internalMatch': 2,
            'internalNames': ['王工·92', '赵工·42'],
            'interviewing': 5, 'linkedCount': 0,
        },
        {
            'id': 'DM2026070003', 'position': '运营总监', 'dept': '运营部', 'hc': 1,
            'urgency': 'very', 'urgencyLabel': '非常紧急', 'urgencyType': 'reject',
            'submitter': '陈总', 'status': 'open', 'statusLabel': '招聘中', 'statusType': 'progress',
            'approvalNodes': [
                {'label': '部门负责人', 'state': 'done'},
                {'label': 'HR', 'state': 'done'},
                {'label': '高管', 'state': 'done'},
            ],
            'directApply': 1, 'systemRecommend': 0, 'internalMatch': 0,
            'internalNames': [], 'interviewing': 0, 'linkedCount': 0,
        },
        {
            'id': 'DM2026070002', 'position': '前端工程师', 'dept': '技术部', 'hc': 3,
            'urgency': 'high', 'urgencyLabel': '紧急', 'urgencyType': 'warn',
            'submitter': '刘博', 'status': 'closed', 'statusLabel': '已关闭', 'statusType': 'draft',
            'approvalNodes': [
                {'label': '部门负责人', 'state': 'done'},
                {'label': 'HR', 'state': 'done'},
                {'label': '高管', 'state': 'done'},
            ],
            'linkedCount': 0,
        },
        {
            'id': 'DM2026070001', 'position': '数据分析师', 'dept': '数据部', 'hc': 1,
            'urgency': 'normal', 'urgencyLabel': '普通', 'urgencyType': 'draft',
            'submitter': '陈博', 'status': 'draft', 'statusLabel': '草稿', 'statusType': 'draft',
            'approvalNodes': [],
            'linkedCount': 0,
        },
    ]

    if search:
        search_lower = search.lower()
        all_demands = [d for d in all_demands
                       if search_lower in d['position'].lower()
                       or search_lower in d['dept'].lower()
                       or search_lower in d['id'].lower()]
    if status != 'all':
        all_demands = [d for d in all_demands if d['status'] == status]
    if urgency != 'all':
        all_demands = [d for d in all_demands if d['urgency'] == urgency]

    return all_demands, len(all_demands)


def create_demand(data):
    """Create a new demand and write it to DB."""
    try:
        from app.models.demand import RecruitDemand
        from app.extensions import db
        from datetime import datetime
        from sqlalchemy.exc import IntegrityError

        _ensure_name_columns()

        now = datetime.now()
        prefix = f"DM{now.strftime('%Y%m')}"

        dept_text = (data.get('dept') or '').strip()
        position_text = (data.get('position') or '').strip()

        entry_date = None
        raw_date = data.get('date') or data.get('expectEntryDate')
        if raw_date:
            try:
                entry_date = datetime.strptime(str(raw_date)[:10], '%Y-%m-%d').date()
            except (ValueError, TypeError):
                entry_date = None

        def next_demand_no(min_seq=1):
            # Unique index covers soft-deleted rows too, so include all history.
            latest = RecruitDemand.query.filter(
                RecruitDemand.demand_no.like(f'{prefix}%'),
            ).order_by(RecruitDemand.demand_no.desc()).first()
            seq = int(latest.demand_no[-4:]) + 1 if latest else 1
            seq = max(seq, min_seq)
            return f"{prefix}{seq:04d}"

        def build_demand(demand_no):
            return RecruitDemand(
                demand_no=demand_no,
                dept_id=data.get('deptId') or DEPT_IDS.get(dept_text) or 1,
                dept_name=dept_text or None,
                position_id=data.get('positionId') or 1,
                position_name=position_text or None,
                recruit_type=data.get('recruitType') or 1,
                plan_headcount=data.get('hc') or data.get('planHeadcount') or 1,
                demand_status=0,  # draft
                urgency=_normalize_urgency(data.get('urgency')),
                creator_id=data.get('creatorId') or 1,
                hr_owner_id=data.get('hrOwnerId') or 1,
                jd_content=data.get('description') or data.get('desc', ''),
                salary_range=data.get('salary', ''),
                work_city=data.get('workCity', ''),
                edu_min=data.get('eduMin', ''),
                exp_min=data.get('expMin'),
                expect_entry_date=entry_date,
                required_skills=data.get('requiredSkills'),
                plus_skills=data.get('plusSkills'),
            )

        from app.services.approval_service import init_approval

        last_integrity_error = None
        next_min_seq = 1
        for attempt in range(5):
            demand_no = next_demand_no(next_min_seq)
            next_min_seq = int(demand_no[-4:]) + 1
            d = build_demand(demand_no)
            db.session.add(d)
            try:
                db.session.flush()
                # Initialize approval records while the demand is still draft.
                init_approval(d.id)
                db.session.commit()
                return {'id': demand_no, 'created': True, 'demandId': d.id}
            except IntegrityError as exc:
                db.session.rollback()
                last_integrity_error = exc
                log.warning(
                    "Demand number collision for %s on attempt %s; retrying",
                    demand_no, attempt + 1,
                )

        if last_integrity_error:
            raise last_integrity_error
    except Exception as exc:
        log.error("DB write failed in create_demand: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise

    return {'id': 'DM2026070007', 'created': True, '_fallback': True}


def update_demand(demand_id, data):
    """Partial update demand — writes to DB."""
    updated = False
    try:
        from app.models.demand import RecruitDemand
        from app.extensions import db
        from datetime import datetime

        _ensure_name_columns()

        d = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if not d:
            raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在')

        # Cannot update a closed/cancelled demand
        if d.demand_status in (4, 5):
            raise AppError('INVALID_STATE', f'需求已{STATUS_LABELS.get(d.demand_status, "关闭")}，无法编辑')

        if d.demand_status == 2 and _has_active_interviews_or_offers(d.id):
            raise AppError('HAS_ACTIVE_ENGAGEMENTS', '需求存在进行中的面试或Offer，无法编辑')

        if 'urgency' in data:
            d.urgency = _normalize_urgency(data['urgency'])
        if 'position' in data:
            d.position_name = (data['position'] or '').strip() or None
        if 'dept' in data:
            dept_text = (data['dept'] or '').strip()
            d.dept_name = dept_text or None
            if dept_text in DEPT_IDS:
                d.dept_id = DEPT_IDS[dept_text]
        if 'deptId' in data and data['deptId']:
            d.dept_id = data['deptId']
        if 'positionId' in data and data['positionId']:
            d.position_id = data['positionId']
        if 'description' in data or 'desc' in data:
            d.jd_content = data.get('description') or data.get('desc') or ''
        if 'salary' in data:
            d.salary_range = data['salary']
        if 'hc' in data or 'planHeadcount' in data:
            hc = data.get('hc') or data.get('planHeadcount')
            if hc:
                d.plan_headcount = int(hc)
        if 'date' in data or 'expectEntryDate' in data:
            raw_date = data.get('date') or data.get('expectEntryDate')
            if raw_date:
                try:
                    d.expect_entry_date = datetime.strptime(str(raw_date)[:10], '%Y-%m-%d').date()
                except (ValueError, TypeError):
                    pass
            else:
                d.expect_entry_date = None
        if 'eduMin' in data:
            d.edu_min = data['eduMin']
        if 'expMin' in data:
            d.exp_min = data['expMin']
        if 'workCity' in data:
            d.work_city = data['workCity']
        db.session.commit()
        updated = True
    except AppError:
        raise
    except Exception as exc:
        log.error("DB write failed in update_demand: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return {'updated': updated}


def close_demand(demand_id):
    """Close a demand — open(2) -> closed(4).

    Validates no active interviews or pending offers before closing.
    Auto-releases all linked candidates.
    """
    try:
        from app.models.demand import RecruitDemand
        from app.extensions import db
        from datetime import datetime

        _ensure_name_columns()

        d = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if not d:
            raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在')

        _validate_demand_transition(d, 4)  # open -> closed

        # Check active engagements
        if _has_active_interviews_or_offers(d.id):
            raise AppError('HAS_ACTIVE_ENGAGEMENTS', '需求存在进行中的面试或Offer，无法关闭')

        d.demand_status = 4  # closed
        d.closed_at = datetime.now()

        # Auto-release all linked candidates
        _release_linked_candidates(d.id)

        db.session.commit()
        log.info("需求已关闭: %s", demand_id)
        return {'closed': True, 'releasedCandidates': True}
    except AppError:
        raise
    except Exception as exc:
        log.error("DB write failed in close_demand: %s", exc, exc_info=True)
    return {'closed': False}


def delete_demand(demand_id):
    """Soft-delete a demand. Only allowed for draft(0) or rejected(3) status."""
    try:
        from app.models.demand import RecruitDemand
        from app.extensions import db

        _ensure_name_columns()

        d = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if not d:
            raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在')

        if d.demand_status not in (0, 2, 3, 5):
            raise AppError('INVALID_STATE', f'需求状态为{STATUS_LABELS.get(d.demand_status, "未知")}，不允许删除')

        if _has_active_interviews_or_offers(d.id):
            raise AppError('HAS_ACTIVE_ENGAGEMENTS', '需求存在进行中的面试或Offer，无法删除')

        d.soft_delete()
        db.session.commit()
        log.info("需求已删除: %s", demand_id)
        return {'deleted': True}
    except AppError:
        raise
    except Exception as exc:
        log.error("DB delete failed in delete_demand: %s", exc, exc_info=True)
        return {'deleted': False}


def get_demand_detail(demand_id):
    """Return demand detail with approval nodes — DB first, mock fallback."""
    try:
        from app.models.demand import RecruitDemand
        _ensure_name_columns()
        d = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if d:
            pos_name = getattr(d, 'position_name', None) or POS_NAMES.get(d.position_id, str(d.position_id))
            dept_name = getattr(d, 'dept_name', None) or DEPT_NAMES.get(d.dept_id, str(d.dept_id))
            submitter = USER_NAMES.get(d.creator_id, str(d.creator_id))

            req_skills = d.required_skills or []
            plus_skills = d.plus_skills or []
            if not req_skills and not plus_skills and d.jd_content:
                req_skills, plus_skills = _extract_skills_from_jd(d.jd_content)

            # Resolve approval progress
            from app.services.approval_service import get_approval_progress
            approval_nodes = d.audit_flow or get_approval_progress(d.id)

            detail = {
                'id': d.demand_no,
                'position': pos_name,
                'dept': dept_name,
                'hc': d.plan_headcount,
                'urgency': URGENCY_LABELS.get(d.urgency, '普通'),
                'salary': d.salary_range or '面议',
                'date': d.expect_entry_date.strftime('%Y-%m-%d') if d.expect_entry_date else '',
                'submitter': submitter,
                'submitDate': d.created_at.strftime('%Y-%m-%d') if d.created_at else '',
                'channels': d.publishing_channels or [],
                'progress': {
                    'hired': d.filled_count or 0,
                    'total': d.plan_headcount,
                    'pct': round((d.filled_count or 0) / d.plan_headcount * 100) if d.plan_headcount else 0,
                },
                'description': d.jd_content or '',
                'requiredSkills': req_skills,
                'plusSkills': plus_skills,
                'approvalNodes': approval_nodes or [],
            }

            return detail
    except Exception as exc:
        log.error("DB query failed in get_demand_detail: %s", exc, exc_info=True)

    if not _mock_enabled():
        raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在于数据库')
    return _mock_demand_detail()


def _mock_demand_detail():
    return {
        'id': 'DM2026070005',
        'position': '高级Java工程师', 'dept': '技术部', 'hc': 2,
        'urgency': '紧急', 'salary': '¥15K - ¥25K / 月', 'date': '2026-08-01',
        'submitter': '刘博', 'submitDate': '2026-07-12',
        'channels': ['Boss直聘', '猎聘', '邮箱采集'],
        'progress': {'hired': 1, 'total': 2, 'pct': 50},
        'description': ('负责公司电商中台核心服务的架构设计与开发，主导微服务拆分和容器化改造，'
                        '参与技术选型和 code review，指导初中级工程师成长。直接汇报给技术总监，有转管理通道。'),
        'requiredSkills': ['Java · 5年以上', 'Spring Boot/Cloud', 'MySQL 调优', 'Kubernetes', '微服务架构设计', '分布式系统'],
        'plusSkills': ['团队管理经验', 'DevOps/CI-CD', '多语言（Go/Python）', '技术博客/开源贡献'],
        'approvalNodes': [
            {'actor': '刘博', 'role': '部门负责人', 'status': '已通过', 'date': '2026-07-12 14:30'},
            {'actor': '张HR', 'role': 'HR', 'status': '已通过', 'date': '2026-07-13 09:15'},
            {'actor': '高管', 'role': '高管', 'status': '已通过', 'date': '2026-07-13 16:00'},
        ],
    }


def list_demand_candidates(demand_id, params):
    """Return candidates linked to this demand from DB."""
    db_success = False
    demand_found = False
    try:
        from app.models.process import RecruitProcess
        from app.models.candidate import Candidate
        from app.models.demand import RecruitDemand
        from app.extensions import db

        demand = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if demand:
            demand_found = True
            processes = RecruitProcess.query.filter(
                RecruitProcess.demand_id == demand.id,
                RecruitProcess.is_deleted == 0,
            ).all()

            if processes:
                candidates_db = []
                for p in processes:
                    cand = Candidate.query.filter_by(id=p.candidate_id, is_deleted=0).first()
                    if not cand:
                        continue

                    edu_label = EDU_LEVEL_LABELS.get(cand.edu_level, '—')
                    years_label = _compute_years_label(cand.work_years)

                    source_ch = cand.source_channel or '人才库'
                    source_type = {
                        '邮箱': 'mail', '邮箱采集': 'mail',
                        'Boss': 'boss', 'Boss直聘': 'boss',
                        '猎聘': 'liepin',
                        '内推': 'refer', '内部推荐': 'refer',
                        '手动上传': 'upload',
                    }.get(source_ch, 'pool')
                    source_label_map = {
                        'mail': '邮箱采集', 'boss': 'Boss直聘', 'liepin': '猎聘',
                        'refer': '内推', 'upload': '手动上传', 'pool': '人才库',
                        'internal': '内部员工',
                    }

                    ps_map = {
                        0: ('available', '待筛选'), 1: ('available', '已邀约'),
                        2: ('interviewing', '面试中'), 3: ('interviewing', '面试中'),
                        4: ('available', '已淘汰'), 5: ('offer', '待Offer'),
                        6: ('offer', '已录用'), 7: ('available', '已放弃'), 8: ('onboard', '已入职'),
                    }
                    status, status_label = ps_map.get(p.process_status, ('available', '可联系'))

                    profile_score = candidate_profile_score(cand)
                    candidates_db.append({
                        'id': cand.candidate_no or str(cand.id),
                        'name': cand.candidate_name,
                        'profileScore': int(profile_score),
                        'profileGrade': _compute_grade(profile_score),
                        'matchScore': None,
                        'ageDays': (__import__('datetime').datetime.now() - p.created_at).days if p.created_at else 0,
                        'source': source_type,
                        'sourceLabel': source_label_map.get(source_type, '人才库检索'),
                        'status': status,
                        'statusLabel': status_label,
                        'notRecReason': None,
                        'edu': edu_label,
                        'years': years_label,
                        'isEmployee': False,
                        '_cid': cand.id,
                        '_resumeId': p.resume_id,
                    })

                if candidates_db:
                    db_success = True
                    # 优先使用真实的 JD 匹配打分（t_hr_resume_match），没有再退回确定性估算
                    from app.models.process import ResumeMatch
                    from app.services.match_service import calc_match_score
                    for c in candidates_db:
                        try:
                            real = None
                            if c.get('_resumeId'):
                                rm = (ResumeMatch.query
                                      .filter_by(resume_id=c['_resumeId'], demand_id=demand.id, is_deleted=0)
                                      .order_by(ResumeMatch.calculate_time.desc()).first())
                                if rm and rm.match_score is not None:
                                    real = float(rm.match_score)
                            if real is not None:
                                c['matchScore'] = round(real, 1)
                            else:
                                match = calc_match_score(str(c['id']), str(demand.id), c.get('profileScore') or 50)
                                c['matchScore'] = match['score']
                        except Exception:
                            c['matchScore'] = None
                        c.pop('_cid', None)
                        c.pop('_resumeId', None)

                    source_filter = params.get('source', 'all')
                    if source_filter != 'all':
                        candidates_db = [c for c in candidates_db if c['source'] == source_filter]
                    return candidates_db
            return []
    except Exception as exc:
        log.error("DB query failed in list_demand_candidates: %s", exc, exc_info=True)
        if db_success:
            return []

    if not _mock_enabled():
        if demand_found:
            return []
        raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在于数据库')

    candidates = _mock_list_demand_candidates()
    source = params.get('source', 'all')
    if source != 'all':
        candidates = [c for c in candidates if c['source'] == source]
    return candidates


def _compute_years_label(work_years):
    if work_years is None:
        return '—'
    if work_years >= 5:
        return '5+'
    if work_years >= 3:
        return '3-5'
    if work_years >= 1:
        return '1-3'
    return 'fresh'


def _compute_grade(score):
    if not score:
        return 'C'
    s = float(score)
    return 'A' if s >= 85 else 'B+' if s >= 75 else 'B' if s >= 60 else 'C+'


def _mock_list_demand_candidates():
    return [
        {'name': '张三', 'profileScore': 88, 'profileGrade': 'A', 'matchScore': 92, 'ageDays': 4,
         'source': 'direct', 'sourceLabel': '直接投递', 'status': 'interviewing', 'statusLabel': '面试中',
         'notRecReason': None, 'edu': '本科', 'years': '5+'},
        {'name': '孙九', 'profileScore': 68, 'profileGrade': 'B', 'matchScore': 72, 'ageDays': 3,
         'source': 'direct', 'sourceLabel': '直接投递', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': None, 'edu': '本科', 'years': '5+'},
        {'name': '刘八', 'profileScore': 48, 'profileGrade': 'C', 'matchScore': None, 'ageDays': 2,
         'source': 'direct', 'sourceLabel': '直接投递', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': '学历不符（大专）', 'edu': '大专', 'years': '1-3'},
        {'name': '王五', 'profileScore': 80, 'profileGrade': 'B+', 'matchScore': 86, 'ageDays': 4,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': None, 'edu': '硕士', 'years': '3-5'},
        {'name': '李四', 'profileScore': 76, 'profileGrade': 'B+', 'matchScore': 85, 'ageDays': 5,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': None, 'edu': '硕士', 'years': '3-5'},
        {'name': '周明', 'profileScore': 72, 'profileGrade': 'B', 'matchScore': 81, 'ageDays': 36,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': '匹配分超期 36 天', 'edu': '本科', 'years': '5+'},
        {'name': '吴六', 'profileScore': 78, 'profileGrade': 'B+', 'matchScore': 82, 'ageDays': 7,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': None, 'edu': '本科', 'years': '5+'},
        {'name': '赵十', 'profileScore': 65, 'profileGrade': 'B', 'matchScore': 70, 'ageDays': 12,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': None, 'edu': '本科', 'years': '3-5'},
        {'name': '陈二', 'profileScore': 56, 'profileGrade': 'C+', 'matchScore': 62, 'ageDays': 7,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': None, 'edu': '本科', 'years': '1-3'},
        {'name': '钱七', 'profileScore': 55, 'profileGrade': 'C+', 'matchScore': 52, 'ageDays': 15,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': '经验不足 6 个月', 'edu': '本科', 'years': 'fresh'},
        {'name': '孙八', 'profileScore': 68, 'profileGrade': 'B', 'matchScore': None, 'ageDays': 60,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': '未在检索范围', 'edu': '硕士', 'years': '5+'},
        {'name': '郑七', 'profileScore': 50, 'profileGrade': 'C', 'matchScore': None, 'ageDays': 45,
         'source': 'external', 'sourceLabel': '人才库检索', 'status': 'available', 'statusLabel': '可联系',
         'notRecReason': '学历不符（大专）', 'edu': '大专', 'years': '3-5'},
        {'name': '王工', 'profileScore': 92, 'profileGrade': 'A', 'matchScore': 92, 'ageDays': 1278,
         'source': 'internal', 'sourceLabel': '内部员工', 'status': 'available', 'statusLabel': '可调岗',
         'notRecReason': None, 'edu': '硕士', 'years': '3-5', 'isEmployee': True},
        {'name': '钱工', 'profileScore': 95, 'profileGrade': 'A+', 'matchScore': 88, 'ageDays': 1780,
         'source': 'internal', 'sourceLabel': '内部员工', 'status': 'available', 'statusLabel': '可调岗',
         'notRecReason': None, 'edu': '硕士', 'years': '5+', 'isEmployee': True},
        {'name': '赵工', 'profileScore': 65, 'profileGrade': 'B', 'matchScore': 42, 'ageDays': 760,
         'source': 'internal', 'sourceLabel': '内部员工', 'status': 'available', 'statusLabel': '不可调',
         'notRecReason': '入职不满 2 年', 'edu': '硕士', 'years': '1-3', 'isEmployee': True},
    ]


def link_candidate_to_demand(demand_id, name):
    """Link a candidate to demand by looking up candidate name in DB.

    联动规则：
      - 写入真实 resume_id（候选人最新一份简历），不再留 0
      - 黑名单 / 面试锁定中的候选人不可加入
      - 同一候选人在同一需求下的进行中流程去重（淘汰/放弃后允许重新加入）
      - 加入时用 JD 对简历做一次匹配打分，写入 t_hr_resume_match
    """
    try:
        from app.models.candidate import Candidate, Resume
        from app.models.process import RecruitProcess, ResumeMatch
        from app.models.demand import RecruitDemand
        from app.extensions import db
        from datetime import datetime

        demand = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if not demand:
            log.warning("link_candidate_to_demand: demand %s not found", demand_id)
            return {'linked': False, 'linkedCount': 0, 'reason': '需求不存在', '_fallback': True}

        candidate = Candidate.query.filter(
            Candidate.candidate_name == name,
            Candidate.is_deleted == 0,
        ).first()

        if not candidate:
            log.warning("link_candidate_to_demand: candidate '%s' not found", name)
            return {'linked': False, 'linkedCount': 0, 'reason': '候选人不存在', '_fallback': True}

        # 状态守卫
        if candidate.black_flag:
            return {'linked': False, 'linkedCount': 0, 'reason': '黑名单候选人不可加入需求'}
        if candidate.status == 'locked':
            return {'linked': False, 'linkedCount': 0, 'reason': '候选人面试中（锁定），不可重复加入'}

        # 去重：进行中的流程不重复创建
        existing = RecruitProcess.query.filter(
            RecruitProcess.candidate_id == candidate.id,
            RecruitProcess.demand_id == demand.id,
            RecruitProcess.process_status.notin_([4, 7]),  # 淘汰/放弃除外
            RecruitProcess.is_deleted == 0,
        ).first()
        if existing:
            linked_count = RecruitProcess.query.filter(
                RecruitProcess.demand_id == demand.id,
                RecruitProcess.is_deleted == 0,
            ).count()
            return {'linked': True, 'already': True, 'linkedCount': linked_count,
                    'reason': '该候选人已在此需求流程中'}

        # 真实 resume_id（最新一份简历）
        resume = (Resume.query.filter_by(candidate_id=candidate.id, is_deleted=0)
                  .order_by(Resume.storage_time.desc()).first())
        resume_id = resume.id if resume else 0

        now = datetime.now()
        import random as _rnd
        process_no = f"RP{now.strftime('%Y%m%d%H%M%S')}{candidate.id % 100:02d}{_rnd.randint(100, 999)}"

        process = RecruitProcess(
            process_no=process_no,
            demand_id=demand.id,
            resume_id=resume_id,
            candidate_id=candidate.id,
            process_status=0,  # pending screening
        )
        db.session.add(process)

        # 匹配打分（best-effort）：JD vs 简历解析结果
        match_score = None
        try:
            if resume and demand.jd_content:
                from app.services.ai_engine import match_job
                candidate_data = (resume.extract_json or {})
                m = match_job(candidate_data, demand.jd_content)
                match_score = m.get('match_score')
                db.session.add(ResumeMatch(
                    resume_id=resume_id,
                    demand_id=demand.id,
                    match_score=match_score or 0,
                    score_detail=m.get('score_detail'),
                    calculate_time=now,
                ))
        except Exception as exc:
            log.warning("match scoring failed on link (best-effort): %s", exc)

        db.session.commit()

        log.info("Linked candidate '%s' (id=%s, resume=%s) to demand '%s', match=%s",
                 name, candidate.id, resume_id, demand_id, match_score)

        linked_count = RecruitProcess.query.filter(
            RecruitProcess.demand_id == demand.id,
            RecruitProcess.is_deleted == 0,
        ).count()

        return {'linked': True, 'linkedCount': linked_count, 'matchScore': match_score}

    except Exception as exc:
        log.error("DB write failed in link_candidate_to_demand: %s", exc, exc_info=True)
        return {'linked': False, 'linkedCount': 0, '_fallback': True}
