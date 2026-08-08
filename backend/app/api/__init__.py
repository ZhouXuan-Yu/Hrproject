from flask import Blueprint, g
from flask import current_app
from app.utils.response import AppError


# ── Role constants (mirrors enums.py ROLES) ──────────────────────────────
_ROLE_ALL        = {'admin'}
_ROLE_HR         = {'admin', 'hr'}
_ROLE_HR_DEPT    = {'admin', 'hr', 'dept_head', 'director', 'employee'}
_ROLE_INTERVIEW  = {'admin', 'hr', 'interviewer', 'temp_interviewer', 'dept_head'}
_ROLE_ANY_AUTH   = {'admin', 'hr', 'dept_head', 'director',
                     'interviewer', 'temp_interviewer'}
_ROLE_TALENT      = {'admin', 'hr', 'dept_head', 'interviewer'}


def _make_role_guard(allowed_roles: set):
    """Return a before_request function that only allows roles in *allowed_roles*."""
    def guard():
        role = getattr(g, 'current_role', '')
        if not role:
            raise AppError('UNAUTHORIZED', '请先登录', 401)
        if role not in allowed_roles:
            raise AppError('FORBIDDEN', '无权限访问', 403)
    return guard


def register_blueprints(app):
    """Register all API blueprints with role-based access control."""
    from app.api.auth import bp as auth_bp
    from app.api.dashboard import bp as dashboard_bp
    from app.api.demand import bp as demand_bp
    from app.api.talent import bp as talent_bp
    from app.api.interview import bp as interview_bp
    from app.api.ai import bp as ai_bp
    from app.api.config import bp as config_bp
    from app.api.health import bp as health_bp
    from app.api.hire import bp as hire_bp
    from app.api.dedup import bp as dedup_bp
    from app.api.confirm import bp as confirm_bp

    # Attach role guards — login/me is role-agnostic, health/confirm are public
    dashboard_bp.before_request(_make_role_guard(_ROLE_ANY_AUTH))
    demand_bp.before_request(_make_role_guard(_ROLE_HR_DEPT))
    talent_bp.before_request(_make_role_guard(_ROLE_TALENT))
    interview_bp.before_request(_make_role_guard(_ROLE_INTERVIEW))
    ai_bp.before_request(_make_role_guard(_ROLE_HR))
    config_bp.before_request(_make_role_guard(_ROLE_ALL))
    hire_bp.before_request(_make_role_guard(_ROLE_HR))
    dedup_bp.before_request(_make_role_guard(_ROLE_HR))

    app.register_blueprint(auth_bp, url_prefix='/api/auth')
    app.register_blueprint(dashboard_bp, url_prefix='/api/dashboard')
    app.register_blueprint(demand_bp, url_prefix='/api/demand')
    app.register_blueprint(talent_bp, url_prefix='/api/talent')
    app.register_blueprint(interview_bp, url_prefix='/api/interview')
    app.register_blueprint(ai_bp, url_prefix='/api/ai')
    app.register_blueprint(config_bp, url_prefix='/api/config')
    app.register_blueprint(health_bp, url_prefix='/api/health')
    app.register_blueprint(hire_bp, url_prefix='/api/hire')
    app.register_blueprint(dedup_bp, url_prefix='/api/dedup')
    # 候选人确认页（GET /confirm/<token>）与提交（POST /api/confirm/<token>），无需登录
    app.register_blueprint(confirm_bp)
