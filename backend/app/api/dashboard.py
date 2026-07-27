"""Dashboard API: /api/dashboard/*"""
from flask import Blueprint, g
from app.utils.response import success, success_list

bp = Blueprint('dashboard', __name__)


@bp.route('/kpi')
def get_kpi():
    """KPI cards (role-aware: admin/hr/interviewer sets)."""
    from app.services.cache_service import cached
    from app.services.dashboard_service import get_kpi_data
    role = getattr(g, 'current_role', 'admin')
    data = cached('dashboard:kpi', {'role': role}, lambda: get_kpi_data(role), ttl=30)
    return success(data)


@bp.route('/funnel')
def get_funnel():
    """Recruitment funnel with 5 stages."""
    from app.services.cache_service import cached
    from app.services.dashboard_service import get_funnel_data
    data = cached('dashboard:funnel', {}, get_funnel_data, ttl=30)
    return success(data)


@bp.route('/dept-progress')
def get_dept_progress():
    """Department HC fill progress."""
    from app.services.cache_service import cached
    from app.services.dashboard_service import get_dept_progress_data
    data = cached('dashboard:dept-progress', {}, get_dept_progress_data, ttl=60)
    return success(data)


@bp.route('/channel')
def get_channel():
    """Channel effectiveness statistics."""
    from app.services.cache_service import cached
    from app.services.dashboard_service import get_channel_data
    data = cached('dashboard:channel', {}, get_channel_data, ttl=60)
    return success(data)


@bp.route('/risk-alerts')
def get_risk_alerts():
    """Risk/success alert list."""
    from app.services.cache_service import cached
    from app.services.dashboard_service import get_risk_alerts
    data = cached('dashboard:risk-alerts', {}, get_risk_alerts, ttl=30)
    return success(data)
