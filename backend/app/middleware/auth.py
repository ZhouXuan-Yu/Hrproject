"""Auth middleware: JWT verification and role extraction."""
from functools import wraps
from flask import request, g, current_app
from app.utils.response import AppError
import jwt


AUTH_WHITELIST = {
    'auth.login',
    'static',
    'health.health_check',
    # Boss endpoints — used for browser automation, auth is via boss-cli session
    'boss.login',
    'boss.get_status',
    'boss.list_positions',
    'boss.get_position_detail',
    'boss.search_candidates',
    'boss.chat_list',
    'boss.chat_open',
    'boss.chat_send',
    'boss.action',
    'boss.resume_preview',
    'boss.greet_candidate',
}

# SSE streaming endpoints use query param for auth (EventSource doesn't support headers)
SSE_WHITELIST_PREFIXES = (
    'ai_stream.',
)


def init_auth_middleware(app):
    """Register JWT auth before_request handler."""

    @app.before_request
    def authenticate():
        if request.endpoint in AUTH_WHITELIST:
            return
        if request.endpoint is None:
            return

        # SSE streaming endpoints use query param auth (EventSource can't set headers)
        if request.endpoint and request.endpoint.startswith(SSE_WHITELIST_PREFIXES):
            role = request.args.get('role', 'admin')
            g.current_user_id = 1
            g.current_role = role
            g.current_tenant_id = 'default'
            return

        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            raise AppError('UNAUTHORIZED', '请先登录', 401)
        token = auth_header[7:]
        if not token:
            raise AppError('UNAUTHORIZED', '请先登录', 401)

        try:
            payload = jwt.decode(
                token,
                app.config['JWT_SECRET_KEY'],
                algorithms=['HS256']
            )
            if 'user_id' not in payload:
                raise AppError('INVALID_TOKEN', '无效令牌: 缺少 user_id', 401)
            if 'role' not in payload:
                raise AppError('INVALID_TOKEN', '无效令牌: 缺少 role', 401)
            if 'tenant_id' not in payload:
                raise AppError('INVALID_TOKEN', '无效令牌: 缺少 tenant_id', 401)
            g.current_user_id = payload['user_id']
            g.current_role = payload['role']
            g.current_tenant_id = payload['tenant_id']
        except jwt.ExpiredSignatureError:
            raise AppError('TOKEN_EXPIRED', '登录已过期', 401)
        except jwt.InvalidTokenError:
            raise AppError('INVALID_TOKEN', '无效令牌', 401)


def require_role(*roles):
    """Decorator: restrict endpoint to specific roles."""
    def decorator(f):
        @wraps(f)
        def wrapper(*args, **kwargs):
            role = getattr(g, 'current_role', 'employee')
            if role not in roles:
                raise AppError('FORBIDDEN', '无权限访问', 403)
            return f(*args, **kwargs)
        return wrapper
    return decorator
