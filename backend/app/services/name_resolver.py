"""Name resolver — unified lookup for department, position, and user display names.

All services should use this module instead of maintaining private dicts.
Queries IAM tables (t_core_dept / t_core_position / t_core_user) first;
falls back to built-in defaults when the database is unavailable.
"""
import logging

log = logging.getLogger(__name__)


def resolve_dept_name(dept_id):
    """Return department name for a dept_id, or str(dept_id) if unknown."""
    if dept_id is None:
        return '—'
    try:
        from app.models.iam import IamDept
        dept = IamDept.query.filter_by(dept_id=dept_id, status=1, is_deleted=0).first()
        if dept:
            return dept.dept_name
    except Exception as exc:
        log.debug("resolve_dept_name DB lookup failed: %s", exc)
    return _DEPT_DEFAULTS.get(int(dept_id), str(dept_id))


def resolve_position_name(position_id):
    """Return position name for a position_id, or str(position_id) if unknown."""
    if position_id is None:
        return '—'
    try:
        from app.models.iam import IamPosition
        pos = IamPosition.query.filter_by(position_id=position_id, status=1, is_deleted=0).first()
        if pos:
            return pos.position_name
    except Exception as exc:
        log.debug("resolve_position_name DB lookup failed: %s", exc)
    return _POS_DEFAULTS.get(int(position_id), str(position_id))


def resolve_user_name(user_id):
    """Return real_name for a user_id, or str(user_id) if unknown."""
    if user_id is None:
        return '系统'
    try:
        from app.models.iam import IamUser
        user = IamUser.query.filter_by(user_id=user_id, status=1, is_deleted=0).first()
        if user:
            return user.real_name
    except Exception as exc:
        log.debug("resolve_user_name DB lookup failed: %s", exc)
    return _USER_DEFAULTS.get(int(user_id), str(user_id))


def resolve_dept_id(dept_name):
    """Reverse-lookup: dept_name → dept_id. Returns None if unknown."""
    if not dept_name:
        return None
    name = str(dept_name).strip()
    try:
        from app.models.iam import IamDept
        dept = IamDept.query.filter_by(dept_name=name, status=1, is_deleted=0).first()
        if dept:
            return dept.dept_id
    except Exception as exc:
        log.debug("resolve_dept_id DB lookup failed: %s", exc)
    for k, v in _DEPT_DEFAULTS.items():
        if v == name:
            return k
    return None


# ── Built-in fallback maps (used when DB is unavailable) ──────────────

_DEPT_DEFAULTS = {
    1: '技术部', 2: '产品部', 3: '运营部', 4: '数据部', 5: '财务部',
}

_POS_DEFAULTS = {
    1: '高级Java工程师', 2: '前端工程师', 3: '产品经理',
    4: '运营总监', 5: '数据分析师',
}

_USER_DEFAULTS = {
    1: '刘博', 2: '张HR', 3: '陈总', 4: '周博',
    5: '李面试官', 6: '王面试官', 7: '赵博', 8: '总监',
}
