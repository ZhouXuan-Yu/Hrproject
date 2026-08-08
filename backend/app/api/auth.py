"""Auth API: /api/auth/*"""
import hashlib
import threading
import time
from datetime import datetime, timedelta, timezone
from flask import Blueprint, request, g, current_app, make_response
from app.utils.response import success, error, AppError, success_list
from app.utils.enums import ROLES, ROLE_MENUS
from werkzeug.security import generate_password_hash, check_password_hash
import jwt

bp = Blueprint('auth', __name__)


def _hash_password(password):
    """Hash a password using werkzeug (scrypt by default, with random salt)."""
    return generate_password_hash(password)


def _is_legacy_hash(h):
    """Return True if h is a raw SHA-256 hex digest (64 hex chars)."""
    return bool(h) and len(h) == 64 and all(c in '0123456789abcdef' for c in h)


def _verify_password(stored_hash, password):
    """Verify password against stored hash.

    Supports both werkzeug hashes (scrypt/bcrypt/pbkdf2) and legacy SHA-256.
    """
    if not stored_hash:
        return False
    if _is_legacy_hash(stored_hash):
        from flask import current_app as _ctx
        salt = _ctx.config.get('PASSWORD_SALT', '')
        return stored_hash == hashlib.sha256((password + salt).encode('utf-8')).hexdigest()
    return check_password_hash(stored_hash, password)


def _get_role_menus(role_code):
    """Get menu IDs for a role from DB, with hardcoded fallback."""
    try:
        from app.services.config_service import get_role_menus_from_db
        menus = get_role_menus_from_db(role_code)
        if menus:
            return menus
    except Exception:
        pass
    return ROLE_MENUS.get(role_code, [])

# ── Login failure tracking (in-memory, per-username) ─────────────────────
_login_failures: dict[str, list[float]] = {}
_login_lock = threading.Lock()
MAX_FAILURES = 5
FAILURE_WINDOW = 900  # 15 minutes in seconds


def _record_login_failure(username: str):
    """Record a failed login attempt. Returns (blocked: bool, remaining: int)."""
    now = time.time()
    with _login_lock:
        attempts = [t for t in _login_failures.get(username, []) if t > now - FAILURE_WINDOW]
        attempts.append(now)
        _login_failures[username] = attempts
        if len(attempts) >= MAX_FAILURES:
            return True, 0
        return False, MAX_FAILURES - len(attempts)


def _clear_login_failures(username: str):
    """Clear failure records on successful login."""
    with _login_lock:
        _login_failures.pop(username, None)


def _cleanup_stale_failures():
    """Background thread: periodically remove stale failure records."""
    while True:
        time.sleep(600)  # every 10 minutes
        cutoff = time.time() - FAILURE_WINDOW
        with _login_lock:
            stale = [u for u, ts in list(_login_failures.items())
                     if not ts or max(ts) < cutoff]
            for u in stale:
                del _login_failures[u]


_cleanup_thread = threading.Thread(
    target=_cleanup_stale_failures, daemon=True, name='login-failure-cleanup')
_cleanup_thread.start()


def _make_token(user_id, role, tenant_id=1, username='', remember_me=False):
    """Generate a real JWT token. Expiry from config (default 1 hour), or 30 days for remember_me."""
    if remember_me:
        expires_seconds = 30 * 24 * 3600  # 30 days
    else:
        expires_seconds = current_app.config.get('JWT_ACCESS_TOKEN_EXPIRES', 3600)
    exp = datetime.now(timezone.utc) + timedelta(seconds=expires_seconds)
    payload = {
        'user_id': user_id,
        'role': role,
        'tenant_id': tenant_id,
        'username': username,
        'exp': exp,
    }
    return jwt.encode(payload, current_app.config['JWT_SECRET_KEY'], algorithm='HS256')


def _set_auth_cookie(response, token, max_age=None):
    """Set the JWT as an httpOnly cookie so JS cannot read it."""
    if max_age is None:
        max_age = current_app.config.get('JWT_ACCESS_TOKEN_EXPIRES', 3600)
    secure = current_app.config.get('COOKIE_SECURE', False)
    domain = current_app.config.get('COOKIE_DOMAIN') or None
    response.set_cookie(
        'hr_token', token,
        httponly=True,
        secure=secure,
        samesite='Strict' if secure else 'Lax',
        domain=domain,
        path='/',
        max_age=max_age,
    )


@bp.route('/me')
def get_me():
    """Return current user info + menu permissions."""
    user_id = getattr(g, 'current_user_id', None)
    role = getattr(g, 'current_role', 'employee')
    username = getattr(g, 'current_username', '')

    return success({
        'user': {
            'id': str(user_id) if user_id else '0',
            'name': username or ROLES.get(role, '用户'),
            'role': role,
            'avatar': None,
        },
        'menus': _get_role_menus(role),
    })


@bp.route('/login', methods=['POST'])
def login():
    """Login — validates credentials, returns user info and sets httpOnly cookie."""
    data = request.get_json(silent=True) or {}
    username = (data.get('username') or '').strip()
    password = data.get('password') or ''

    if not username:
        raise AppError('INVALID_INPUT', '请输入用户名')
    if not password:
        raise AppError('INVALID_INPUT', '请输入密码')

    # ── Login failure lockout check ──
    with _login_lock:
        attempts = [t for t in _login_failures.get(username, [])
                    if t > time.time() - FAILURE_WINDOW]
        if len(attempts) >= MAX_FAILURES:
            remaining = int(attempts[0] + FAILURE_WINDOW - time.time())
            raise AppError(
                'ACCOUNT_LOCKED',
                f'账户已临时锁定，请 {max(1, remaining)} 秒后再试',
                429,
            )

    # Query user from database — login with username or employee_no
    from app.models.iam import IamUser
    from app.extensions import db
    user = IamUser.query.filter(
        db.or_(IamUser.username == username, IamUser.employee_no == username),
        IamUser.status == 1, IamUser.is_deleted == 0).first()
    if not user:
        blocked, remaining = _record_login_failure(username)
        if blocked:
            raise AppError('ACCOUNT_LOCKED',
                           f'登录失败次数过多，账户已锁定 {int(FAILURE_WINDOW / 60)} 分钟', 429)
        raise AppError('AUTH_FAILED',
                       f'用户名或密码错误（剩余尝试次数：{remaining}）')

    # Verify password (supports legacy SHA-256 + werkzeug scrypt/bcrypt)
    if user.password_hash and not _verify_password(user.password_hash, password):
        blocked, remaining = _record_login_failure(username)
        if blocked:
            raise AppError('ACCOUNT_LOCKED',
                           f'登录失败次数过多，账户已锁定 {int(FAILURE_WINDOW / 60)} 分钟', 429)
        raise AppError('AUTH_FAILED',
                       f'用户名或密码错误（剩余尝试次数：{remaining}）')
    if not user.password_hash:
        import logging
        logging.getLogger(__name__).warning(
            "User %s (id=%s) has no password_hash set — allowing login (legacy)",
            username, user.user_id)

    # Clear failure record on successful login
    _clear_login_failures(username)

    remember_me = bool(data.get('rememberMe', False))
    token = _make_token(
        user_id=user.user_id,
        role=user.role_code,
        tenant_id=1,
        username=user.real_name or username,
        remember_me=remember_me,
    )
    max_age = 30 * 24 * 3600 if remember_me else current_app.config.get('JWT_ACCESS_TOKEN_EXPIRES', 3600)

    body = {
        'token': token,
        'user': {
            'id': str(user.user_id),
            'name': user.real_name or username,
            'role': user.role_code,
            'avatar': None,
            'mustChangePassword': bool(user.must_change_password),
        },
        'menus': _get_role_menus(user.role_code),
    }
    resp = make_response(success(body))
    _set_auth_cookie(resp, token, max_age)
    return resp


@bp.route('/departments')
def get_departments():
    """GET /api/auth/departments — 部门列表。?all=1 返回全部（含停用），否则仅启用。"""
    try:
        from app.models.iam import IamDept
        q = IamDept.query.filter_by(is_deleted=0)
        if request.args.get('all') != '1':
            q = q.filter_by(status=1)
        depts = q.order_by(IamDept.sort_num).all()
        return success([{'id': d.dept_id, 'name': d.dept_name, 'sortNum': d.sort_num, 'status': d.status} for d in depts])
    except Exception:
        return success([
            {'id': 1, 'name': '技术部', 'sortNum': 0, 'status': 1},
            {'id': 2, 'name': '产品部', 'sortNum': 1, 'status': 1},
            {'id': 3, 'name': '运营部', 'sortNum': 2, 'status': 1},
            {'id': 4, 'name': '数据部', 'sortNum': 3, 'status': 1},
            {'id': 5, 'name': '财务部', 'sortNum': 4, 'status': 1},
        ])


@bp.route('/departments', methods=['POST'])
def create_department():
    """POST /api/auth/departments — admin: create department."""
    _require_admin()
    from app.models.iam import IamDept
    from app.extensions import db
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    if not name:
        raise AppError('VALIDATION', '部门名称不能为空')

    max_dept = db.session.query(db.func.max(IamDept.dept_id)).filter(
        IamDept.is_deleted == 0).scalar() or 1000
    new_id = max_dept + 1

    d = IamDept(
        dept_id=new_id,
        dept_name=name,
        parent_dept_id=data.get('parentDeptId') or None,
        sort_num=data.get('sortNum', 0),
        status=1,
    )
    db.session.add(d)
    db.session.commit()
    return success({'id': d.dept_id, 'name': d.dept_name, 'status': d.status})


@bp.route('/departments/<int:dept_id>', methods=['PUT'])
def update_department(dept_id):
    """PUT /api/auth/departments/{id} — admin: update department."""
    _require_admin()
    from app.models.iam import IamDept
    from app.extensions import db
    d = IamDept.query.filter_by(dept_id=dept_id, is_deleted=0).first()
    if not d:
        raise AppError('NOT_FOUND', f'部门 {dept_id} 不存在')
    data = request.get_json(silent=True) or {}
    if data.get('name'):
        d.dept_name = data['name'].strip()
    if 'sortNum' in data:
        d.sort_num = data['sortNum']
    if 'parentDeptId' in data:
        d.parent_dept_id = data['parentDeptId'] or None
    db.session.commit()
    return success({'id': d.dept_id, 'name': d.dept_name})


@bp.route('/departments/<int:dept_id>', methods=['DELETE'])
def delete_department(dept_id):
    """DELETE /api/auth/departments/{id} — admin: delete department (only if unreferenced)."""
    _require_admin()
    from app.models.iam import IamDept, IamUser, IamPosition
    from app.models.demand import RecruitDemand
    from app.extensions import db

    d = IamDept.query.filter_by(dept_id=dept_id, is_deleted=0).first()
    if not d:
        raise AppError('NOT_FOUND', f'部门 {dept_id} 不存在')

    # 引用检查
    user_count = IamUser.query.filter_by(dept_id=dept_id, is_deleted=0, status=1).count()
    demand_count = RecruitDemand.query.filter_by(dept_id=dept_id, is_deleted=0).count()
    pos_count = IamPosition.query.filter_by(dept_id=dept_id, is_deleted=0, status=1).count()

    refs = []
    if user_count > 0:
        refs.append(f'{user_count} 个用户')
    if demand_count > 0:
        refs.append(f'{demand_count} 个招聘需求')
    if pos_count > 0:
        refs.append(f'{pos_count} 个岗位')
    if refs:
        raise AppError('HAS_REFERENCES', f'部门被引用（{", ".join(refs)}），无法删除，请先停用')

    d.soft_delete()
    db.session.commit()
    return success({'deleted': True, 'id': dept_id})


@bp.route('/departments/<int:dept_id>/status', methods=['PUT'])
def toggle_department_status(dept_id):
    """PUT /api/auth/departments/{id}/status — admin: enable/disable department."""
    _require_admin()
    from app.models.iam import IamDept, IamUser
    from app.extensions import db

    d = IamDept.query.filter_by(dept_id=dept_id, is_deleted=0).first()
    if not d:
        raise AppError('NOT_FOUND', f'部门 {dept_id} 不存在')

    new_status = request.get_json(silent=True).get('status', None) if request.get_json(silent=True) else None
    if new_status is None:
        new_status = 0 if d.status == 1 else 1

    # 停用时检查是否有在职员工
    if new_status == 0:
        active_users = IamUser.query.filter_by(dept_id=dept_id, is_deleted=0, status=1).count()
        if active_users > 0:
            raise AppError('HAS_ACTIVE_USERS', f'部门下有 {active_users} 名在职员工，请先转岗后再停用')

    d.status = new_status
    db.session.commit()
    return success({'id': d.dept_id, 'name': d.dept_name, 'status': d.status})


@bp.route('/change-password', methods=['PUT'])
def change_password():
    """PUT /api/auth/change-password — logged-in user changes their own password."""
    data = request.get_json(silent=True) or {}
    old_pwd = (data.get('oldPassword') or '').strip()
    new_pwd = (data.get('newPassword') or '').strip()

    if not old_pwd or not new_pwd:
        raise AppError('VALIDATION', '请输入旧密码和新密码')
    if len(new_pwd) < 6:
        raise AppError('VALIDATION', '新密码至少6位')

    from app.models.iam import IamUser
    from app.extensions import db
    from datetime import datetime, timezone

    user_id = getattr(g, 'current_user_id', None)
    u = IamUser.query.filter_by(user_id=user_id, is_deleted=0).first()
    if not u:
        raise AppError('NOT_FOUND', '用户不存在')

    if u.password_hash and not _verify_password(u.password_hash, old_pwd):
        raise AppError('AUTH_FAILED', '旧密码不正确')

    u.password_hash = _hash_password(new_pwd)
    u.must_change_password = 0
    u.password_updated_at = datetime.now(timezone.utc)
    db.session.commit()

    return success({'changed': True, 'message': '密码修改成功'})


def _generate_reset_code():
    import random, string
    return ''.join(random.choices(string.digits, k=6))


def _save_reset_token(user_id, channel, target, token):
    from app.models.auxiliary import PasswordResetToken
    from app.extensions import db
    from datetime import timedelta
    row = PasswordResetToken(
        user_id=user_id, token=token, channel=channel, target=target,
        status='pending',
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=5))
    db.session.add(row)
    db.session.commit()
    return row


@bp.route('/forgot-password', methods=['POST'])
def forgot_password():
    """POST /api/auth/forgot-password — send password reset code via email or phone.

    Body: { username: string, channel?: 'email'|'phone' }
    Returns masked target info and a requestId for the verify step.
    """
    data = request.get_json(silent=True) or {}
    username = (data.get('username') or '').strip()
    channel = (data.get('channel') or 'email').strip()

    if not username:
        raise AppError('VALIDATION', '请输入用户名')

    from app.models.iam import IamUser
    u = IamUser.query.filter_by(username=username, is_deleted=0, status=1).first()
    if not u:
        return success({'sent': False, 'message': '如果该账号存在，您将收到验证码。'})

    code = _generate_reset_code()
    target = ''

    if channel == 'phone' and u.mobile:
        target = u.mobile[:3] + '****' + u.mobile[-4:]
        # Production: send SMS via gateway. Dev: log it.
        import logging
        logging.getLogger(__name__).info(
            "[PASSWORD_RESET] phone=%s code=%s user=%s", u.mobile, code, username)

    elif u.email:
        channel = 'email'
        target = u.email[:3] + '***@' + (u.email.split('@')[1] if '@' in u.email else '***')
        try:
            from app.services.mail_sender import send_mail
            ok, msg = send_mail(
                u.email, '密码重置验证码 - 智能招聘系统',
                f'<p>{u.real_name}，您好：</p>'
                f'<p>您的密码重置验证码为：<b style="font-size:24px;letter-spacing:4px">{code}</b></p>'
                f'<p>验证码 <b>5分钟内</b> 有效。如非本人操作请忽略。</p>',
                mail_type='other')
            if not ok:
                # Email failed, fall through to admin contact
                raise Exception(msg)
        except Exception as exc:
            import logging
            logging.getLogger(__name__).warning("forgot-password email failed: %s", exc)
            admins = IamUser.query.filter_by(role_code='admin', status=1, is_deleted=0).all()
            admin_names = '、'.join([a.real_name for a in admins]) if admins else '系统管理员'
            return success({
                'sent': False, 'message': f'邮件发送失败，请联系管理员重置密码：{admin_names}',
                'contactAdmin': True,
            })
    else:
        admins = IamUser.query.filter_by(role_code='admin', status=1, is_deleted=0).all()
        admin_names = '、'.join([a.real_name for a in admins]) if admins else '系统管理员'
        return success({
            'sent': False, 'message': f'该账号未配置邮箱/手机号，请联系管理员重置密码：{admin_names}',
            'contactAdmin': True,
        })

    _save_reset_token(u.user_id, channel, target, code)

    from app.services.config_service import append_audit_log
    append_audit_log(str(u.user_id), '密码重置', '申请验证码',
                     f"用户 {u.real_name}({username}) 通过{channel}申请密码重置")

    return success({
        'sent': True, 'channel': channel, 'target': target,
        'message': f'验证码已发送至您的{ "邮箱" if channel == "email" else "手机" } {target}',
        'expiresIn': 300,
    })


@bp.route('/verify-reset-code', methods=['POST'])
def verify_reset_code():
    """POST /api/auth/verify-reset-code — verify code and set new password.

    Body: { username, code, newPassword }
    """
    data = request.get_json(silent=True) or {}
    username = (data.get('username') or '').strip()
    code = (data.get('code') or '').strip()
    new_pwd = (data.get('newPassword') or '').strip()

    if not username or not code or not new_pwd:
        raise AppError('VALIDATION', '请填写完整信息')
    if len(new_pwd) < 6:
        raise AppError('VALIDATION', '新密码至少6位')

    from app.models.iam import IamUser
    from app.models.auxiliary import PasswordResetToken
    from app.extensions import db

    u = IamUser.query.filter_by(username=username, is_deleted=0, status=1).first()
    if not u:
        raise AppError('NOT_FOUND', '用户不存在')

    # Find a valid pending token
    now = datetime.now(timezone.utc)
    reset_row = PasswordResetToken.query.filter(
        PasswordResetToken.user_id == u.user_id,
        PasswordResetToken.token == code,
        PasswordResetToken.status == 'pending',
        PasswordResetToken.is_deleted == 0,
        PasswordResetToken.expires_at > now,
    ).order_by(PasswordResetToken.id.desc()).first()

    if not reset_row:
        raise AppError('INVALID_CODE', '验证码错误或已过期，请重新获取')

    u.password_hash = _hash_password(new_pwd)
    u.must_change_password = 0
    u.password_updated_at = now
    reset_row.status = 'used'
    reset_row.used_at = now

    # Expire other pending tokens for this user
    PasswordResetToken.query.filter(
        PasswordResetToken.user_id == u.user_id,
        PasswordResetToken.status == 'pending',
        PasswordResetToken.is_deleted == 0,
    ).update({'status': 'expired'})

    db.session.commit()

    from app.services.config_service import append_audit_log
    append_audit_log(str(u.user_id), '密码重置', '验证码重置成功',
                     f"用户 {u.real_name}({username}) 通过验证码重置密码成功")

    return success({'reset': True, 'message': '密码重置成功，请使用新密码登录'})


@bp.route('/register', methods=['POST'])
def register():
    """POST /api/auth/register — 自助注册。验证码校验通过后创建用户。

    第一个注册者自动成为管理员，后续注册者为基层员工。
    """
    data = request.get_json(silent=True) or {}
    target = (data.get('email') or '').strip()
    code = (data.get('code') or '').strip()
    real_name = (data.get('realName') or '').strip()
    mobile = (data.get('mobile') or '').strip()
    password = (data.get('password') or '').strip()
    import re

    if not target or not code or not real_name or not password:
        raise AppError('VALIDATION', '请填写完整信息')
    if len(password) < 6:
        raise AppError('VALIDATION', '密码至少6位')

    is_phone = bool(re.match(r'^1[3-9]\d{9}$', target))
    is_email = '@' in target

    from app.models.iam import IamUser
    from app.extensions import db
    if is_email:
        if IamUser.query.filter_by(email=target, is_deleted=0).first():
            raise AppError('DUPLICATE', '该邮箱已被注册')
    elif is_phone:
        if IamUser.query.filter_by(mobile=target, is_deleted=0).first():
            raise AppError('DUPLICATE', '该手机号已被注册')
    else:
        raise AppError('VALIDATION', '请输入有效的邮箱或手机号')

    # 验证验证码
    now = datetime.now(timezone.utc)
    from app.models.auxiliary import PasswordResetToken
    valid = PasswordResetToken.query.filter(
        PasswordResetToken.token == code,
        PasswordResetToken.target == target,
        PasswordResetToken.status == 'pending',
        PasswordResetToken.is_deleted == 0,
        PasswordResetToken.expires_at > now,
    ).first()
    if not valid:
        raise AppError('INVALID_CODE', '验证码错误或已过期')

    valid.status = 'used'
    valid.used_at = now

    # 第一个注册者 → 管理员
    user_count = IamUser.query.filter_by(is_deleted=0).count()
    role_code = 'admin' if user_count == 0 else 'employee'

    pwd_hash = _hash_password(password)

    # 生成用户名
    username = target.split('@')[0] if is_email else f"user{target[-4:]}"
    base = username
    n = 1
    while IamUser.query.filter_by(username=username, is_deleted=0).first():
        n += 1
        username = f'{base}{n}'

    max_id = db.session.query(db.func.max(IamUser.user_id)).scalar() or 0
    u = IamUser(
        user_id=max_id + 1,
        username=username, real_name=real_name,
        email=target if is_email else None,
        mobile=mobile or target if is_phone else (mobile or None),
        role_code=role_code,
        password_hash=pwd_hash, must_change_password=0, status=1,
    )
    db.session.add(u)
    db.session.commit()

    from app.services.config_service import append_audit_log
    append_audit_log(str(u.user_id), '注册', '自助注册',
                     f"{real_name}({target}) 自助注册，角色={role_code}")

    return success({
        'registered': True,
        'user': {
            'username': username, 'realName': real_name,
            'role': role_code, 'roleLabel': '管理员' if role_code == 'admin' else '基层员工',
        },
        'message': '注册成功' + ('，您已成为系统管理员' if role_code == 'admin' else ''),
    })


@bp.route('/setup-status', methods=['GET'])
def setup_status():
    """GET /api/auth/setup-status — check if system needs first-time admin setup."""
    from app.models.iam import IamUser
    admin_count = IamUser.query.filter_by(
        role_code='admin', status=1, is_deleted=0).count()
    return success({'needsSetup': admin_count == 0, 'hasAdmin': admin_count > 0})


@bp.route('/setup', methods=['POST'])
def first_time_setup():
    """POST /api/auth/setup — one-time: create the first admin account.
    Only works when no admin exists yet.
    """
    from app.models.iam import IamUser
    from app.extensions import db

    existing = IamUser.query.filter_by(role_code='admin', status=1, is_deleted=0).count()
    if existing > 0:
        raise AppError('FORBIDDEN', '系统已完成初始化，请使用管理员账号登录')

    data = request.get_json(silent=True) or {}
    username = (data.get('username') or 'admin').strip()
    real_name = (data.get('realName') or '系统管理员').strip()
    password = (data.get('password') or '').strip()

    if not username or not password:
        raise AppError('VALIDATION', '请填写用户名和密码')
    if len(password) < 6:
        raise AppError('VALIDATION', '密码至少6位')

    pwd_hash = _hash_password(password)

    max_id = db.session.query(db.func.max(IamUser.user_id)).scalar() or 0
    u = IamUser(
        user_id=max(100, max_id + 1),
        username=username, real_name=real_name,
        role_code='admin', password_hash=pwd_hash,
        must_change_password=0, status=1,
    )
    db.session.add(u)
    db.session.commit()

    from app.services.config_service import append_audit_log
    append_audit_log('系统', '系统初始化', '创建首个管理员',
                     f"首次设置: 创建管理员 {real_name}({username})")

    return success({
        'created': True,
        'user': {'username': username, 'realName': real_name, 'role': 'admin'},
        'message': '系统初始化完成，请登录',
    })


@bp.route('/logout', methods=['POST'])
def logout():
    """Clear the auth cookie."""
    resp = make_response(success({'ok': True}))
    resp.set_cookie('hr_token', '', httponly=True, path='/', max_age=0)
    return resp


# ── User management (admin only) ────────────────────────────────────────

def _require_admin():
    role = getattr(g, 'current_role', '')
    if role != 'admin':
        raise AppError('FORBIDDEN', '仅管理员可操作', 403)


def _user_to_dict(u):
    from app.services.name_resolver import resolve_dept_name, resolve_position_name
    return {
        'userId': u.user_id,
        'employeeNo': u.employee_no or '',
        'username': u.username,
        'realName': u.real_name,
        'deptId': u.dept_id,
        'deptName': resolve_dept_name(u.dept_id) if u.dept_id else '—',
        'positionId': u.position_id,
        'positionName': resolve_position_name(u.position_id) if u.position_id else '—',
        'roleCode': u.role_code,
        'email': u.email or '',
        'mobile': u.mobile or '',
        'feishuOpenId': u.feishu_open_id or '',
        'status': u.status,
        'statusLabel': '启用' if u.status == 1 else '停用',
    }


@bp.route('/pending-accounts')
def pending_accounts():
    """GET /api/auth/pending-accounts — admin: candidates accepted/onboarded without login account."""
    _require_admin()
    from app.extensions import db
    from app.models.iam import IamUser, IamDept, IamPosition
    from app.models.candidate import Candidate, Resume
    from app.models.process import RecruitProcess
    from app.models.demand import RecruitDemand

    # Find hired candidates with process_status 6 (accepted) or 8 (onboarded)
    # who don't have a matching IamUser (by mobile or email)
    rows = (
        db.session.query(Candidate, RecruitProcess, RecruitDemand)
        .join(Resume, Resume.candidate_id == Candidate.id)
        .join(RecruitProcess, RecruitProcess.resume_id == Resume.id)
        .join(RecruitDemand, RecruitDemand.id == RecruitProcess.demand_id)
        .filter(Candidate.status == 'hired')
        .filter(Candidate.is_deleted == 0)
        .filter(Resume.is_deleted == 0)
        .filter(RecruitProcess.is_deleted == 0)
        .filter(RecruitProcess.process_status.in_([6, 8]))
        .filter(RecruitDemand.is_deleted == 0)
        .order_by(Candidate.id.desc())
        .limit(100)
        .all()
    )

    # Filter out candidates who already have an IamUser account
    result = []
    for c, rp, demand in rows:
        # Check if any IamUser matches this candidate by mobile or email
        has_account = False
        if c.mobile:
            exists = IamUser.query.filter(
                IamUser.mobile == c.mobile, IamUser.is_deleted == 0
            ).first()
            if exists:
                has_account = True
        if not has_account and c.email:
            exists = IamUser.query.filter(
                IamUser.email == c.email, IamUser.is_deleted == 0
            ).first()
            if exists:
                has_account = True
        if has_account:
            continue

        dept_name = ''
        if demand.dept_id:
            d = IamDept.query.filter_by(dept_id=demand.dept_id, status=1).first()
            dept_name = d.dept_name if d else ''
        pos_name = ''
        if demand.position_id:
            p = IamPosition.query.filter_by(position_id=demand.position_id, status=1).first()
            pos_name = p.position_name if p else ''

        result.append({
            'candidateId': c.id,
            'candidateName': c.candidate_name,
            'mobile': c.mobile or '',
            'email': c.email or '',
            'deptId': demand.dept_id,
            'deptName': dept_name,
            'positionId': demand.position_id,
            'positionName': pos_name,
            'processStatus': rp.process_status,
            'statusLabel': '已入职' if rp.process_status == 8 else '已接受Offer',
        })

    return success(result)


@bp.route('/users')
def list_users():
    """GET /api/auth/users — admin: list all users with filters."""
    _require_admin()
    from app.models.iam import IamUser
    from app.extensions import db

    keyword = (request.args.get('keyword') or '').strip()
    role = (request.args.get('role') or '').strip()
    status = request.args.get('status', type=int)
    page = max(1, request.args.get('page', 1, type=int))
    page_size = min(100, max(1, request.args.get('pageSize', 20, type=int)))

    q = IamUser.query.filter(IamUser.is_deleted == 0)
    if keyword:
        kw = f'%{keyword}%'
        q = q.filter(db.or_(
            IamUser.username.like(kw),
            IamUser.real_name.like(kw),
            IamUser.mobile.like(kw),
        ))
    if role:
        q = q.filter(IamUser.role_code == role)
    if status is not None:
        q = q.filter(IamUser.status == status)

    total = q.count()
    rows = q.order_by(IamUser.user_id).offset((page - 1) * page_size).limit(page_size).all()

    return success_list([_user_to_dict(u) for u in rows], total, page, page_size)


@bp.route('/users', methods=['POST'])
def create_user():
    """POST /api/auth/users — admin: create a new user."""
    _require_admin()
    from app.models.iam import IamUser
    from app.extensions import db
    import re

    data = request.get_json(silent=True) or {}
    employee_no = (data.get('employeeNo') or '').strip()
    real_name = (data.get('realName') or '').strip()
    mobile = (data.get('mobile') or '').strip()
    role_code = (data.get('roleCode') or 'employee').strip()
    dept_id = data.get('deptId')
    position_id = data.get('positionId')
    email = (data.get('email') or '').strip()

    if not employee_no:
        # Auto-generate: YYYY + 4-digit sequence (20260001, 20260002, ...)
        # Query ALL rows (including soft-deleted) because the UNIQUE constraint spans all.
        yyyy = str(datetime.now(timezone.utc).year)
        latest = IamUser.query.filter(
            IamUser.employee_no.like(f'{yyyy}%'),
        ).order_by(IamUser.employee_no.desc()).first()
        if latest and latest.employee_no and len(latest.employee_no) == 8:
            seq = int(latest.employee_no[4:]) + 1
        else:
            seq = 1
        employee_no = f'{yyyy}{seq:04d}'
        # Ensure the generated number doesn't collide with any existing row
        while IamUser.query.filter_by(employee_no=employee_no).first():
            seq += 1
            employee_no = f'{yyyy}{seq:04d}'
    if not real_name:
        raise AppError('VALIDATION', '请输入真实姓名')
    if mobile and not re.match(r'^1[3-9]\d{9}$', mobile):
        raise AppError('VALIDATION', '手机号格式不正确')
    if role_code not in ['admin', 'hr', 'dept_head', 'director', 'employee', 'interviewer']:
        raise AppError('VALIDATION', f'无效的角色: {role_code}')

    if IamUser.query.filter_by(employee_no=employee_no).first():
        raise AppError('DUPLICATE', f'工号 {employee_no} 已存在')
    if mobile and IamUser.query.filter_by(mobile=mobile, is_deleted=0).first():
        raise AppError('DUPLICATE', f'手机号 {mobile} 已存在')
    username = employee_no  # username = employee_no

    # 固定初始密码 — 首次登录强制修改
    password = '123456'
    password_hash = _hash_password(password)

    # Find next user_id
    max_user = db.session.query(db.func.max(IamUser.user_id)).filter(
        IamUser.is_deleted == 0).scalar() or 0

    u = IamUser(
        user_id=max_user + 1 if max_user > 0 else 10,
        employee_no=employee_no, username=username,
        real_name=real_name,
        dept_id=dept_id if dept_id else None,
        position_id=position_id if position_id else None,
        role_code=role_code,
        email=email or None,
        mobile=mobile or None,
        password_hash=password_hash,
        must_change_password=1,
        status=1,
    )
    db.session.add(u)
    db.session.commit()

    from app.services.config_service import append_audit_log
    append_audit_log('系统', '用户管理', '创建用户',
                     f"创建用户 {real_name}({username}), 角色={role_code}")

    return success({**_user_to_dict(u), 'initialPassword': password})


@bp.route('/users/<int:user_id>', methods=['PUT'])
def update_user(user_id):
    """PUT /api/auth/users/<id> — admin: update user info."""
    _require_admin()
    from app.models.iam import IamUser
    from app.extensions import db
    import re

    u = IamUser.query.filter_by(user_id=user_id, is_deleted=0).first()
    if not u:
        raise AppError('NOT_FOUND', f'用户 {user_id} 不存在')

    data = request.get_json(silent=True) or {}
    if 'realName' in data:
        u.real_name = (data['realName'] or '').strip()
    if 'mobile' in data:
        mobile = (data['mobile'] or '').strip()
        if mobile and not re.match(r'^1[3-9]\d{9}$', mobile):
            raise AppError('VALIDATION', '手机号格式不正确')
        existing = IamUser.query.filter(IamUser.mobile == mobile, IamUser.user_id != user_id, IamUser.is_deleted == 0).first()
        if existing:
            raise AppError('DUPLICATE', f'手机号 {mobile} 已被其他用户使用')
        u.mobile = mobile or None
    if 'roleCode' in data:
        u.role_code = data['roleCode']
    if 'deptId' in data:
        u.dept_id = data['deptId'] or None
    if 'positionId' in data:
        u.position_id = data['positionId'] or None
    if 'email' in data:
        u.email = (data['email'] or '').strip() or None

    db.session.commit()
    from app.services.config_service import append_audit_log
    append_audit_log('系统', '用户管理', '编辑用户',
                     f"编辑用户 {u.real_name}({u.username})")

    return success(_user_to_dict(u))


@bp.route('/users/<int:user_id>/status', methods=['PUT'])
def toggle_user_status(user_id):
    """PUT /api/auth/users/<id>/status — admin: toggle user active/disabled."""
    _require_admin()
    from app.models.iam import IamUser
    from app.extensions import db

    u = IamUser.query.filter_by(user_id=user_id, is_deleted=0).first()
    if not u:
        raise AppError('NOT_FOUND', f'用户 {user_id} 不存在')
    if u.user_id == getattr(g, 'current_user_id', None):
        raise AppError('FORBIDDEN', '不能操作自己的账号')

    u.status = 0 if u.status == 1 else 1
    db.session.commit()

    from app.services.config_service import append_audit_log
    action = '启用' if u.status == 1 else '停用'
    append_audit_log('系统', '用户管理', f'{action}用户',
                     f"{action}用户 {u.real_name}({u.username})")

    return success({'userId': u.user_id, 'status': u.status, 'statusLabel': '启用' if u.status == 1 else '停用'})


@bp.route('/users/<int:user_id>', methods=['DELETE'])
def delete_user(user_id):
    """DELETE /api/auth/users/{id} — admin: hard-delete user (only if unreferenced)."""
    _require_admin()
    from app.models.iam import IamUser
    from app.models.internal import Employee
    from app.extensions import db

    u = IamUser.query.filter_by(user_id=user_id, is_deleted=0).first()
    if not u:
        raise AppError('NOT_FOUND', f'用户 {user_id} 不存在')
    if u.user_id == getattr(g, 'current_user_id', None):
        raise AppError('FORBIDDEN', '不能删除自己的账号')

    # Check if user is referenced by approval identity
    from app.models.iam import RecruitApprovalIdentity
    approval_ref = RecruitApprovalIdentity.query.filter_by(
        user_id=user_id, is_deleted=0, status=1).first()
    if approval_ref:
        raise AppError('HAS_REFERENCES',
                       f'用户「{u.real_name}」被审批身份配置引用，无法删除，请先停用')

    real_name = u.real_name
    username = u.username
    emp_no = u.employee_no

    # Also delete the associated Employee record if it exists
    emp = Employee.query.filter_by(user_id=user_id, is_deleted=0).first()
    if emp:
        db.session.delete(emp)

    db.session.delete(u)
    db.session.commit()

    from app.services.config_service import append_audit_log
    append_audit_log('系统', '用户管理', '删除用户',
                     f"删除用户 {real_name}({username})，工号 {emp_no}")

    return success({'deleted': True, 'userId': user_id})


@bp.route('/users/<int:user_id>/reset-password', methods=['PUT'])
def reset_user_password(user_id):
    """PUT /api/auth/users/<id>/reset-password — admin: reset password to random 8-char."""
    _require_admin()
    from app.models.iam import IamUser
    from app.extensions import db

    u = IamUser.query.filter_by(user_id=user_id, is_deleted=0).first()
    if not u:
        raise AppError('NOT_FOUND', f'用户 {user_id} 不存在')

    new_pwd = '123456'
    u.password_hash = _hash_password(new_pwd)
    u.must_change_password = 1
    u.password_updated_at = None
    db.session.commit()

    from app.services.config_service import append_audit_log
    append_audit_log('系统', '用户管理', '重置密码',
                     f"重置用户 {u.real_name}({u.username}) 的密码为初始密码")

    return success({'userId': u.user_id, 'newPassword': new_pwd})


@bp.route('/users/batch', methods=['POST'])
def batch_create_users():
    """POST /api/auth/users/batch — admin: batch create users from JSON array.

    Body: { users: [{employeeNo, realName, mobile?, roleCode?, deptId?, positionId?, email?}, ...] }
    Returns created users with initial passwords.
    """
    _require_admin()
    from app.models.iam import IamUser
    from app.extensions import db
    import re

    data = request.get_json(silent=True) or {}
    users_data = data.get('users') or []

    if not users_data:
        raise AppError('VALIDATION', '请提供用户列表')
    if len(users_data) > 200:
        raise AppError('VALIDATION', '单次最多导入200个用户')

    results = []
    errors = []
    max_user = db.session.query(db.func.max(IamUser.user_id)).filter(
        IamUser.is_deleted == 0).scalar() or 0

    for i, u in enumerate(users_data):
        row = i + 1
        emp_no = (u.get('employeeNo') or '').strip()
        name = (u.get('realName') or '').strip()
        mobile = (u.get('mobile') or '').strip()
        role = (u.get('roleCode') or 'employee').strip()
        dept = u.get('deptId')
        pos = u.get('positionId')
        email = (u.get('email') or '').strip()

        if not name: errors.append(f'第{row}行: 姓名为空'); continue
        if not emp_no:
            # Auto-generate employee_no — same logic as create_user
            yyyy = str(datetime.now(timezone.utc).year)
            last = IamUser.query.filter(
                IamUser.employee_no.like(f'{yyyy}%'),
            ).order_by(IamUser.employee_no.desc()).first()
            if last and last.employee_no and len(last.employee_no) == 8:
                s = int(last.employee_no[4:]) + 1
            else:
                s = 1
            emp_no = f'{yyyy}{s:04d}'
            while IamUser.query.filter_by(employee_no=emp_no).first():
                s += 1
                emp_no = f'{yyyy}{s:04d}'
        if IamUser.query.filter_by(employee_no=emp_no).first():
            errors.append(f'第{row}行: 工号 {emp_no} 已存在'); continue
        if mobile and not re.match(r'^1[3-9]\d{9}$', mobile):
            errors.append(f'第{row}行: 手机号格式不正确'); continue

        pwd = '123456'
        pwd_hash = _hash_password(pwd)

        max_user += 1
        new_u = IamUser(
            user_id=max_user, employee_no=emp_no,
            username=emp_no, real_name=name,
            mobile=mobile or None, email=email or None,
            dept_id=dept or None, position_id=pos or None,
            role_code=role, password_hash=pwd_hash,
            must_change_password=1, status=1,
        )
        db.session.add(new_u)
        results.append({
            'employeeNo': emp_no, 'realName': name,
            'roleCode': role, 'initialPassword': pwd,
        })

    if errors and not results:
        raise AppError('VALIDATION', '; '.join(errors[:5]))

    db.session.commit()
    from app.services.config_service import append_audit_log
    append_audit_log('系统', '用户管理', '批量导入',
                     f"批量导入 {len(results)} 个用户" + (f"，{len(errors)} 条失败" if errors else ""))

    return success({
        'created': len(results),
        'errors': errors,
        'users': results,
    })


@bp.route('/positions')
def get_positions():
    """GET /api/auth/positions — 岗位列表。?all=1 返回全部（含停用），否则仅启用。"""
    try:
        from app.models.iam import IamPosition
        q = IamPosition.query.filter_by(is_deleted=0)
        if request.args.get('all') != '1':
            q = q.filter_by(status=1)
        positions = q.all()
        return success([{'id': p.position_id, 'name': p.position_name, 'deptId': p.dept_id, 'status': p.status} for p in positions])
    except Exception:
        return success([
            {'id': 1, 'name': '高级Java工程师', 'deptId': 1, 'status': 1},
            {'id': 2, 'name': '前端工程师', 'deptId': 1, 'status': 1},
            {'id': 3, 'name': '产品经理', 'deptId': 2, 'status': 1},
            {'id': 4, 'name': '运营总监', 'deptId': 3, 'status': 1},
            {'id': 5, 'name': '数据分析师', 'deptId': 4, 'status': 1},
        ])


# ── Position CRUD ──

@bp.route('/positions', methods=['POST'])
def create_position():
    """POST /api/auth/positions — admin: create position."""
    _require_admin()
    from app.models.iam import IamPosition
    from app.extensions import db
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    if not name:
        raise AppError('VALIDATION', '岗位名称不能为空')

    max_pos = db.session.query(db.func.max(IamPosition.position_id)).filter(
        IamPosition.is_deleted == 0).scalar() or 100
    new_id = max_pos + 1

    p = IamPosition(
        position_id=new_id,
        position_name=name,
        dept_id=data.get('deptId') or None,
        status=1,
    )
    db.session.add(p)
    db.session.commit()
    return success({'id': p.position_id, 'name': p.position_name, 'deptId': p.dept_id, 'status': p.status})


@bp.route('/positions/<int:position_id>', methods=['PUT'])
def update_position(position_id):
    """PUT /api/auth/positions/{id} — admin: update position."""
    _require_admin()
    from app.models.iam import IamPosition
    from app.extensions import db
    p = IamPosition.query.filter_by(position_id=position_id, is_deleted=0).first()
    if not p:
        raise AppError('NOT_FOUND', f'岗位 {position_id} 不存在')
    data = request.get_json(silent=True) or {}
    if data.get('name'):
        p.position_name = data['name'].strip()
    if 'deptId' in data:
        p.dept_id = data['deptId'] or None
    db.session.commit()
    return success({'id': p.position_id, 'name': p.position_name, 'deptId': p.dept_id})


@bp.route('/positions/<int:position_id>', methods=['DELETE'])
def delete_position(position_id):
    """DELETE /api/auth/positions/{id} — admin: delete (only if unreferenced)."""
    _require_admin()
    from app.models.iam import IamPosition, IamUser
    from app.models.demand import RecruitDemand
    from app.extensions import db

    p = IamPosition.query.filter_by(position_id=position_id, is_deleted=0).first()
    if not p:
        raise AppError('NOT_FOUND', f'岗位 {position_id} 不存在')

    user_count = IamUser.query.filter_by(position_id=position_id, is_deleted=0, status=1).count()
    demand_count = RecruitDemand.query.filter_by(position_id=position_id, is_deleted=0).count()

    refs = []
    if user_count > 0:
        refs.append(f'{user_count} 个用户')
    if demand_count > 0:
        refs.append(f'{demand_count} 个招聘需求')
    if refs:
        raise AppError('HAS_REFERENCES', f'岗位被引用（{", ".join(refs)}），无法删除，请先停用')

    p.soft_delete()
    db.session.commit()
    return success({'deleted': True, 'id': position_id})


@bp.route('/positions/<int:position_id>/status', methods=['PUT'])
def toggle_position_status(position_id):
    """PUT /api/auth/positions/{id}/status — admin: enable/disable position."""
    _require_admin()
    from app.models.iam import IamPosition
    from app.extensions import db

    p = IamPosition.query.filter_by(position_id=position_id, is_deleted=0).first()
    if not p:
        raise AppError('NOT_FOUND', f'岗位 {position_id} 不存在')

    body = request.get_json(silent=True) or {}
    new_status = body.get('status') if 'status' in body else (0 if p.status == 1 else 1)
    p.status = new_status
    db.session.commit()
    return success({'id': p.position_id, 'name': p.position_name, 'status': p.status})


# ── Interviewer list (non-admin accessible) ──────────────────────────────

@bp.route('/interviewers')
def list_interviewers():
    """GET /api/auth/interviewers — 面试官下拉列表（HR/部门负责人/总监可用）."""
    if g.current_role not in {'admin', 'hr', 'dept_head', 'director'}:
        raise AppError('FORBIDDEN', '无权查看面试官列表', 403)
    from app.models.iam import IamUser
    q = IamUser.query.filter(
        IamUser.is_deleted == 0,
        IamUser.status == 1,
        IamUser.role_code.in_(['interviewer', 'temp_interviewer', 'dept_head', 'hr', 'admin']),
    )
    rows = q.order_by(IamUser.user_id).all()
    return success([{'id': str(u.user_id), 'name': u.real_name} for u in rows])
