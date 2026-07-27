"""Health API: /api/health — system status endpoint."""
import logging

from flask import Blueprint, current_app, jsonify
from app.utils.response import success

log = logging.getLogger(__name__)

bp = Blueprint('health', __name__)


@bp.route('')
def health_check():
    """GET /api/health — report system health and mock mode status."""
    from config import Config

    # Check database connectivity
    db_ok = False
    db_name = 'unknown'
    try:
        from app.extensions import db
        from sqlalchemy import text
        db.session.execute(text('SELECT 1'))
        db_ok = True
        db_name = str(db.engine.url).split('://')[0]
    except Exception as exc:
        log.warning("Health check: DB unavailable: %s", exc)

    if not db_ok:
        return jsonify({
            'status': 'error',
            'database': {
                'connected': False,
                'type': db_name,
            },
            'redis': _redis_health(),
            'mock_fallback': bool(current_app.config.get('MOCK_FALLBACK', False)),
            'deepseek': {
                'key_configured': bool(Config.DEEPSEEK_API_KEY),
            },
        }), 503

    return success({
        'status': 'ok',
        'database': {
            'connected': True,
            'type': db_name,
        },
        'redis': _redis_health(),
        'mock_fallback': bool(current_app.config.get('MOCK_FALLBACK', False)),
        'deepseek': {
            'key_configured': bool(Config.DEEPSEEK_API_KEY),
        },
    })


def _redis_health():
    try:
        from app.services.cache_service import cache_status
        return cache_status()
    except Exception as exc:
        return {'enabled': False, 'connected': False, 'error': str(exc)}
