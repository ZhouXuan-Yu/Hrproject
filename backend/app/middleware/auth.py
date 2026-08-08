"""Auth middleware: JWT verification and role extraction."""
from functools import wraps
from flask import request, g, current_app
from app.utils.response import AppError
import jwt


AUTH_WHITELIST = {
    'auth.login',
    'auth.logout',
    'auth.register',
    'auth.setup_status',
    'auth.setup',
    'auth.forgot_password',
    'auth.verify_reset_code',
    'static',
    'health',
    'health.health_check',
    # 候选人确认页/提交 —— 通过签名 token 鉴权，无需登录
    'confirm.confirm_page',
    'confirm.confirm_submit',
}


def _extract_token():
    """Try to get the JWT from the Authorization header, then from the httpOnly cookie."""
    # 1) Authorization: Bearer <token>
    auth_header = request.headers.get('Authorization', '')
    if auth_header.startswith('Bearer '):
        token = auth_header[7:]
        if token:
            return token

    # 2) Cookie: hr_token=<token>  (for SSE/EventSource and browser requests)
    cookie_token = request.cookies.get('hr_token')
    if cookie_token:
        return cookie_token

    return None


def _decode_payload(token, app):
    """Decode and validate JWT, return payload. Raises AppError on failure."""
    try:
        payload = jwt.decode(token, app.config['JWT_SECRET_KEY'], algorithms=['HS256'])
    except jwt.ExpiredSignatureError:
        raise AppError('TOKEN_EXPIRED', '登录已过期', 401)
    except jwt.InvalidTokenError:
        raise AppError('INVALID_TOKEN', '无效令牌', 401)

    if 'user_id' not in payload:
        raise AppError('INVALID_TOKEN', '无效令牌: 缺少 user_id', 401)
    if 'role' not in payload:
        raise AppError('INVALID_TOKEN', '无效令牌: 缺少 role', 401)
    if 'tenant_id' not in payload:
        raise AppError('INVALID_TOKEN', '无效令牌: 缺少 tenant_id', 401)

    return payload


def init_auth_middleware(app):
    """Register JWT auth before_request handler."""

    @app.before_request
    def authenticate():
        if request.endpoint in AUTH_WHITELIST:
            return
        if request.endpoint is None:
            return

        token = _extract_token()
        if not token:
            raise AppError('UNAUTHORIZED', '请先登录', 401)

        payload = _decode_payload(token, app)
        g.current_user_id = payload['user_id']
        g.current_role = payload['role']
        g.current_tenant_id = payload['tenant_id']
        g.current_username = payload.get('username', '')
        g.real_name = payload.get('real_name', '')


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
