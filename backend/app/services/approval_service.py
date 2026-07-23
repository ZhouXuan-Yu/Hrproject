"""Approval service: workflow engine for recruitment demand approval.

Now fully DB-backed using t_hr_demand_approval and t_hr_recruit_demand.
All approval nodes are assigned real approvers based on role lookups.
"""
import logging
from datetime import datetime
from app.utils.response import AppError
from app.extensions import db

log = logging.getLogger(__name__)

# Approval level -> role label mapping
APPROVAL_LEVELS = {
    1: '部门负责人',
    2: 'HR',
    3: '高管',
}

# user_id -> display name used only when legacy rows do not join to IAM users.
_USER_NAME_MAP = {
    1: '刘博', 2: '张HR', 3: '陈总', 4: '周博',
    5: '李面试官', 6: '王面试官', 7: '赵博', 8: '高管',
}

_APPROVAL_IDENTITY_DEFAULTS = [
    (1, 'dept_head', '部门负责人', 'dept_head', None),
    (2, 'hr', 'HR', 'hr', None),
    (3, 'executive', '高管', 'executive', None),
]


def ensure_approval_identities():
    """Create/seed the approval identity table with role-based defaults."""
    from app.models.iam import IamUser, RecruitApprovalIdentity

    try:
        RecruitApprovalIdentity.__table__.create(bind=db.engine, checkfirst=True)
    except Exception as exc:
        log.warning("ensure approval identity table failed: %s", exc)

    try:
        existing = RecruitApprovalIdentity.query.filter_by(is_deleted=0).count()
        if existing:
            changed = False
            identities = RecruitApprovalIdentity.query.filter_by(is_deleted=0, status=1).all()
            for identity in identities:
                if not identity.user_id:
                    continue
                active_user = IamUser.query.filter(
                    IamUser.user_id == identity.user_id,
                    IamUser.role_code == identity.role_code,
                    IamUser.is_deleted == 0,
                    IamUser.status == 1,
                ).first()
                if not active_user:
                    identity.user_id = None
                    changed = True
            if changed:
                db.session.commit()
                log.info("Cleared stale approval identity user bindings")
            return
        for level, code, name, role, user_id in _APPROVAL_IDENTITY_DEFAULTS:
            db.session.add(RecruitApprovalIdentity(
                approve_level=level,
                identity_code=code,
                identity_name=name,
                role_code=role,
                user_id=user_id,
                status=1,
            ))
        db.session.commit()
        log.info("Seeded default recruit approval identities")
    except Exception as exc:
        db.session.rollback()
        log.warning("seed approval identities failed: %s", exc)


def _identity_for_level(level):
    ensure_approval_identities()
    from app.models.iam import RecruitApprovalIdentity

    return RecruitApprovalIdentity.query.filter(
        RecruitApprovalIdentity.approve_level == level,
        RecruitApprovalIdentity.status == 1,
        RecruitApprovalIdentity.is_deleted == 0,
    ).first()


def _assert_can_approve(record, current_user_id, current_role):
    """Enforce sequential approval and identity permissions."""
    if current_role == 'admin':
        return True, '管理员代审批'

    identity = _identity_for_level(record.approve_level)
    if not identity:
        raise AppError('APPROVAL_IDENTITY_MISSING',
                       f'层级 {record.approve_level} 未配置审批身份')
    if current_role != identity.role_code:
        raise AppError(
            'FORBIDDEN',
            f'当前身份无权审批「{identity.identity_name}」节点',
            403,
        )
    if identity.user_id and int(current_user_id or 0) != int(identity.user_id):
        raise AppError(
            'FORBIDDEN',
            f'当前用户不是「{identity.identity_name}」指定审批人',
            403,
        )
    return True, identity.identity_name


# ── Approver resolution ──

def _resolve_dept_head(demand_id):
    """Find the department head (部门负责人) for the demand's department.

    Looks up IamUser with role_code='dept_head' in the same dept as the demand.
    Falls back to admin user if not found.
    """
    try:
        from app.models.demand import RecruitDemand
        from app.models.iam import IamUser

        demand = RecruitDemand.query.filter_by(id=demand_id, is_deleted=0).first()
        if not demand:
            return _resolve_admin_user()

        # Find a user with dept_head role in the demand's department
        head = IamUser.query.filter(
            IamUser.dept_id == demand.dept_id,
            IamUser.role_code == 'dept_head',
            IamUser.is_deleted == 0,
            IamUser.status == 1,
        ).first()

        if head:
            return head.user_id

        # Broader: any dept_head
        head = IamUser.query.filter(
            IamUser.role_code == 'dept_head',
            IamUser.is_deleted == 0,
            IamUser.status == 1,
        ).first()
        if head:
            return head.user_id
    except Exception as exc:
        log.warning("Failed to resolve dept head for demand %s: %s", demand_id, exc)

    return _resolve_admin_user()


def _resolve_admin_user():
    """Resolve an active admin user id; demo auth maps admin to user_id=1."""
    try:
        from app.models.iam import IamUser
        admin = IamUser.query.filter(
            IamUser.role_code == 'admin',
            IamUser.is_deleted == 0,
            IamUser.status == 1,
        ).first()
        if admin:
            return admin.user_id
    except Exception as exc:
        log.warning("Failed to resolve admin approver: %s", exc)
    return 1


def _resolve_hr():
    """Find an HR approver. Queries IamUser with role_code='hr'."""
    try:
        from app.models.iam import IamUser
        hr = IamUser.query.filter(
            IamUser.role_code == 'hr',
            IamUser.is_deleted == 0,
            IamUser.status == 1,
        ).first()
        if hr:
            return hr.user_id
    except Exception as exc:
        log.warning("Failed to resolve HR approver: %s", exc)
    admin_id = _resolve_admin_user()
    log.warning("No active HR approver found; assigning approval task to admin user %s", admin_id)
    return admin_id


def _resolve_executive():
    """Find an executive approver.

    Queries IamUser with role_code='executive' first,
    then falls back to 'admin'.
    """
    try:
        from app.models.iam import IamUser
        executive = IamUser.query.filter(
            IamUser.role_code == 'executive',
            IamUser.is_deleted == 0,
            IamUser.status == 1,
        ).first()

        if executive:
            return executive.user_id

        admin_id = _resolve_admin_user()
        if admin_id:
            return admin_id
    except Exception as exc:
        log.warning("Failed to resolve executive approver: %s", exc)
    admin_id = _resolve_admin_user()
    log.warning("No active executive approver found; assigning approval task to admin user %s", admin_id)
    return admin_id


# ── Core API ──

def init_approval(demand_id):
    """Insert 3 approval rows for a newly created demand into DB.

    Each node is assigned a real approver by role lookup:
      Level 1: department head (dept_head)
      Level 2: HR (hr)
      Level 3: executive (executive)

    Returns the list of created DemandApproval records.
    """
    from app.models.demand import DemandApproval

    now = datetime.now()
    rows = []

    ensure_approval_identities()
    approvers = {
        1: _resolve_dept_head(demand_id),
        2: _resolve_hr(),
        3: _resolve_executive(),
    }

    for level in (1, 2, 3):
        approval = DemandApproval(
            demand_id=demand_id,
            approve_level=level,
            approve_result=1,  # 1 = pending
            approve_user_id=approvers[level],
            approve_opinion=None,
            approve_time=None,
        )
        db.session.add(approval)
        rows.append(approval)

    db.session.commit()
    log.info(
        "Initialized 3-level approval for demand %s: dept_head=%s, hr=%s, executive=%s",
        demand_id, approvers[1], approvers[2], approvers[3],
    )
    return rows


def approve(demand_id, level, approve_user_id, opinion=None, current_role='employee'):
    """Approve a single approval node.

    Sets approve_result = 2 (passed) for the given level.
    If all three levels are passed, sets demand_status = 2 (approved/open).
    Triggers match batch on full approval.
    """
    from app.models.demand import DemandApproval, RecruitDemand

    level = int(level)
    record = DemandApproval.query.filter_by(
        demand_id=demand_id,
        approve_level=level,
        is_deleted=0,
    ).first()

    if not record:
        raise AppError('NOT_FOUND', f'需求 {demand_id} 层级 {level} 审批记录不存在')

    if record.approve_result != 1:
        raise AppError('ALREADY_HANDLED', f'层级 {level} 已处理，无法重复审批')

    previous_pending = DemandApproval.query.filter(
        DemandApproval.demand_id == demand_id,
        DemandApproval.approve_level < level,
        DemandApproval.approve_result != 2,
        DemandApproval.is_deleted == 0,
    ).first()
    if previous_pending:
        raise AppError('APPROVAL_ORDER_REQUIRED',
                       f'请先完成层级 {previous_pending.approve_level} 审批')

    _, identity_note = _assert_can_approve(record, approve_user_id, current_role)

    now = datetime.now()
    record.approve_result = 2  # 2 = approved
    record.approve_user_id = approve_user_id
    base_opinion = (opinion or '').strip()
    record.approve_opinion = (
        f'{identity_note}：{base_opinion}' if identity_note and identity_note not in base_opinion
        else base_opinion
    )
    record.approve_time = now

    # Notify the approver
    _notify_approver(record)

    # Check if all levels are approved
    all_records = DemandApproval.query.filter_by(
        demand_id=demand_id,
        is_deleted=0,
    ).all()

    demand = RecruitDemand.query.filter_by(id=demand_id, is_deleted=0).first()
    if all(r.approve_result == 2 for r in all_records):
        if demand:
            demand.demand_status = 2  # 2 = approved/open
            demand.approved_at = now
            log.info("需求 %s 全部审批通过", demand.demand_no)
        _fire_match_batch(demand_id)

    # Refresh the audit_flow snapshot so list/detail reflect the new progress
    if demand:
        demand.audit_flow = get_approval_progress(demand_id)

    db.session.commit()

    return {
        'demand_id': demand_id,
        'level': level,
        'result': 'approved',
        'time': now.strftime('%Y-%m-%d %H:%M:%S'),
        'identity': identity_note,
    }


def reject(demand_id, level, approve_user_id, opinion=None, current_role='employee'):
    """Reject an approval node in DB.

    Sets approve_result = 3 (rejected).
    Sets demand_status = 3 (rejected).
    """
    from app.models.demand import DemandApproval, RecruitDemand

    level = int(level)
    record = DemandApproval.query.filter_by(
        demand_id=demand_id,
        approve_level=level,
        is_deleted=0,
    ).first()

    if not record:
        raise AppError('NOT_FOUND', f'需求 {demand_id} 层级 {level} 审批记录不存在')

    if record.approve_result != 1:
        raise AppError('ALREADY_HANDLED', f'层级 {level} 已处理，无法重复驳回')

    previous_pending = DemandApproval.query.filter(
        DemandApproval.demand_id == demand_id,
        DemandApproval.approve_level < level,
        DemandApproval.approve_result != 2,
        DemandApproval.is_deleted == 0,
    ).first()
    if previous_pending:
        raise AppError('APPROVAL_ORDER_REQUIRED',
                       f'请先完成层级 {previous_pending.approve_level} 审批')

    _, identity_note = _assert_can_approve(record, approve_user_id, current_role)

    now = datetime.now()
    record.approve_result = 3  # 3 = rejected
    record.approve_user_id = approve_user_id
    base_opinion = (opinion or '').strip()
    record.approve_opinion = (
        f'{identity_note}：{base_opinion}' if identity_note and identity_note not in base_opinion
        else base_opinion
    )
    record.approve_time = now

    # Update demand status
    demand = RecruitDemand.query.filter_by(id=demand_id, is_deleted=0).first()
    if demand:
        demand.demand_status = 3  # 3 = rejected
        # Refresh the audit_flow snapshot so list/detail reflect the rejection
        demand.audit_flow = get_approval_progress(demand_id)

    db.session.commit()

    return {
        'demand_id': demand_id,
        'level': level,
        'result': 'rejected',
        'time': now.strftime('%Y-%m-%d %H:%M:%S'),
        'identity': identity_note,
    }


def get_approval_progress(demand_id):
    """Return the approval progress array in frontend-compatible format.

    Queries t_hr_demand_approval for real data.

    Example output::

        [
            {"label": "部门负责人", "state": "done",   "actor": "刘博", "date": "2026-07-12 14:30"},
            {"label": "HR",         "state":"current", "actor": null,   "date": null},
            {"label": "高管",       "state":"pending", "actor": null,   "date": null},
        ]
    """
    from app.models.demand import DemandApproval

    records = DemandApproval.query.filter_by(
        demand_id=demand_id,
        is_deleted=0,
    ).order_by(DemandApproval.approve_level).all()

    if not records:
        return []

    # Determine the index of the "current" step (first pending node)
    current_idx = None
    for i, r in enumerate(records):
        if r.approve_result == 1:  # pending
            current_idx = i
            break

    result = []
    for i, r in enumerate(records):
        state = _derive_state(r, i, current_idx)
        actor_name = _resolve_actor_name(r.approve_user_id) if r.approve_user_id else None
        date_str = r.approve_time.strftime('%Y-%m-%d %H:%M:%S') if r.approve_time else None

        result.append({
            'label': APPROVAL_LEVELS.get(r.approve_level, f'层级{r.approve_level}'),
            'level': r.approve_level,
            'state': state,
            'actor': actor_name,
            'date': date_str,
            'opinion': r.approve_opinion,
        })

    return result


def _resolve_actor_name(user_id):
    try:
        from app.models.iam import IamUser
        user = IamUser.query.filter(
            IamUser.user_id == user_id,
            IamUser.is_deleted == 0,
        ).first()
        if user:
            return user.real_name
    except Exception:
        pass
    return _USER_NAME_MAP.get(user_id, str(user_id))


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------


def _derive_state(record, index, current_idx):
    """Map a single approval record to a frontend state string."""
    if record.approve_result == 3:  # rejected
        return 'rejected'

    if record.approve_result == 2:  # approved
        return 'done'

    # approve_result == 1 (pending)
    if current_idx is None:
        return 'pending'

    if index == current_idx:
        return 'current'
    elif index < current_idx:
        return 'done'
    else:
        return 'pending'


def _notify_approver(approval_record):
    """Notify the designated approver about a pending approval task.

    In v0.1 this logs the notification; the real implementation will
    send a Feishu/Lark notification to the approver.
    """
    actor_name = _USER_NAME_MAP.get(approval_record.approve_user_id, '未知用户')
    level_label = APPROVAL_LEVELS.get(approval_record.approve_level, f'层级{approval_record.approve_level}')

    log.info(
        "[NOTIFY] 审批通知 -> user_id=%s (%s): 需求 %s 的「%s」审批已通过",
        approval_record.approve_user_id,
        actor_name,
        approval_record.demand_id,
        level_label,
    )

    # TODO: Send real Feishu/Lark notification
    # from app.services.feishu_service import send_feishu_message
    # send_feishu_message(
    #     user_id=approval_record.approve_user_id,
    #     title='审批通知',
    #     content=f'需求 {approval_record.demand_id} 的「{level_label}」审批已通过',
    # )


def _fire_match_batch(demand_id):
    """Trigger candidate matching after full approval without blocking approval."""
    try:
        from flask import current_app
        enqueue_enabled = current_app.config.get('MATCH_ENQUEUE_ON_APPROVAL', False)
        inline_enabled = current_app.config.get('MATCH_INLINE_ON_APPROVAL', False)
    except Exception:
        enqueue_enabled = False
        inline_enabled = False

    if not enqueue_enabled and not inline_enabled:
        log.info("审批已通过；自动匹配未启用，请使用 /api/demand/<id>/match 手动重新匹配")
        return

    try:
        if enqueue_enabled:
            log.info("审批全部通过，触发 Celery 匹配任务: demand_id=%s", demand_id)
            from tasks.match_batch import batch_match_demand

            batch_match_demand.delay(demand_id)
            log.info("Celery batch_match_demand task enqueued for demand_id=%s", demand_id)
            return
    except ImportError as exc:
        log.warning("Cannot import Celery tasks (Celery may not be running): %s", exc)
    except Exception as e:
        log.error("Failed to enqueue match_batch task: %s", e)

    if inline_enabled:
        try:
            from app.services.match_service import batch_match_demand as run_match
            run_match(demand_id)
            log.info("Inline batch_match completed for demand_id=%s", demand_id)
        except Exception as exc2:
            log.error("Inline batch_match failed for demand_id=%s: %s", demand_id, exc2)
