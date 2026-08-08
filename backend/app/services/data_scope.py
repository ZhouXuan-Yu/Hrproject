"""Data scope helpers — apply role-based row-level filtering.

For a given role, returns filter conditions that restrict queries to only
the rows the current user is authorised to see.
"""
import logging
from flask import g

log = logging.getLogger(__name__)


def _current_user_dept_id():
    """Return the dept_id of the current authenticated user, or None."""
    user_id = getattr(g, 'current_user_id', None)
    if not user_id:
        return None
    try:
        from app.models.iam import IamUser
        user = IamUser.query.filter_by(user_id=user_id, status=1, is_deleted=0).first()
        if user:
            return user.dept_id
    except Exception as exc:
        log.debug("_current_user_dept_id lookup failed: %s", exc)
    return None


def apply_demand_scope(query, model):
    """Apply role-based filters to a demand query.

    Returns the modified query object.

    Rules:
      - admin/hr/director → no filter (see all demands)
      - dept_head → only demands from the same department
      - employee → only demands created by this user
    """
    role = getattr(g, 'current_role', 'employee')
    user_id = getattr(g, 'current_user_id', None)

    if role in ('admin', 'hr', 'director'):
        return query

    if role == 'dept_head':
        dept_id = _current_user_dept_id()
        if dept_id is not None:
            return query.filter(model.dept_id == dept_id)

    if role == 'employee' and user_id is not None:
        return query.filter(model.creator_id == user_id)

    return query


def apply_interview_scope(query, book_model, slot_model):
    """Apply role-based filters to an interview query.

    Rules:
      - admin/hr → no filter (see all interviews)
      - interviewer/temp_interviewer → only interviews assigned to this user
    """
    role = getattr(g, 'current_role', 'employee')
    user_id = getattr(g, 'current_user_id', None)

    if role in ('admin', 'hr'):
        return query

    if role in ('interviewer', 'temp_interviewer') and user_id is not None:
        return query.join(slot_model, book_model.slot_id == slot_model.id)\
                    .filter(slot_model.interviewer_id == user_id,
                            slot_model.is_deleted == 0)

    return query
