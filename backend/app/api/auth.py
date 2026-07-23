"""Auth API: /api/auth/*"""
from datetime import datetime, timedelta, timezone
from flask import Blueprint, request, g, current_app
from app.utils.response import success, error, AppError, success_list
from app.utils.enums import ROLES, ROLE_MENUS
import jwt

bp = Blueprint('auth', __name__)


def _make_token(user_id, role, tenant_id=1):
    """Generate a real JWT token. Expiry from config (default 1 hour)."""
    expires_seconds = current_app.config.get('JWT_ACCESS_TOKEN_EXPIRES', 3600)
    exp = datetime.now(timezone.utc) + timedelta(seconds=expires_seconds)
    payload = {
        'user_id': user_id,
        'role': role,
        'tenant_id': tenant_id,
        'exp': exp,
    }
    return jwt.encode(payload, current_app.config['JWT_SECRET_KEY'], algorithm='HS256')


@bp.route('/me')
def get_me():
    """Return current user info + menu permissions."""
    user_id = getattr(g, 'current_user_id', None)
    role = getattr(g, 'current_role', 'employee')

    return success({
        'user': {
            'id': str(user_id) if user_id else '0',
            'name': ROLES.get(role, '用户'),
            'role': role,
            'avatar': None,
        },
        'menus': ROLE_MENUS.get(role, []),
    })


@bp.route('/login', methods=['POST'])
def login():
    """Login — generates a real JWT token for the chosen role."""
    data = request.get_json(silent=True) or {}
    username = data.get('username', '')
    role = data.get('role', 'admin')

    if role not in ROLES:
        raise AppError('INVALID_ROLE', '无效角色')

    # Map role to a user ID (v0.1 demo)
    # Matches ROLE_MENUS keys in enums.py and frontend useAuth.js
    role_user_ids = {
        'admin': 1, 'hr': 2, 'dept_head': 3, 'employee': 4,
        'interviewer': 5, 'temp_interviewer': 6, 'no_recruit': 7,
        'executive': 8,
    }
    user_id = role_user_ids.get(role, 1)

    token = _make_token(user_id, role)

    return success({
        'token': token,
        'user': {
            'id': str(user_id),
            'name': username or ROLES.get(role, role),
            'role': role,
            'avatar': None,
        },
        'menus': ROLE_MENUS.get(role, []),
    })
