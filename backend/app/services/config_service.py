"""Config service: email accounts, channels, scoring, templates, roles, audit logs.

DB-first with mock fallback pattern. Every query function tries the DB first,
and falls back to hardcoded mock data when _mock_enabled() returns True.
"""
import logging
from datetime import datetime

import requests

from app.utils.response import AppError
from app.services.crypto_utils import encrypt, decrypt

log = logging.getLogger(__name__)


def _mock_enabled():
    from app.utils.response import should_mock_fallback
    return should_mock_fallback()


def _encrypt_mail_password(raw):
    """Encrypt a mailbox password/authorization code for storage.

    Returns None when raw is empty (field left unchanged by callers).
    """
    if not raw:
        return None
    from flask import current_app
    # "enc:" 前缀与 email_sync_service._get_password 的解密约定一致
    return 'enc:' + encrypt(raw, current_app.config['SECRET_KEY'])


# ── In-memory channel cost mapping ──
_CHANNEL_COST_MAP: dict[str, str] = {
    'BOSS': '¥8,000',
    'LIEPIN': '¥12,000',
    'EMAIL': '¥0',
    'NEITUI': '¥3,000',
}

# Translates RecruitChannel.channel_name → frontend channel code
_CHANNEL_CODE_MAP: dict[str, str] = {
    'Boss直聘': 'BOSS',
    '猎聘': 'LIEPIN',
    '邮箱采集': 'EMAIL',
    '内部推荐': 'NEITUI',
}

# Reverse: frontend code → channel_name
_REVERSE_CHANNEL_CODE: dict[str, str] = {v: k for k, v in _CHANNEL_CODE_MAP.items()}

# Type label overrides for specific channels (preserves mock behavior)
_CHANNEL_TYPE_OVERRIDE: dict[str, str] = {
    'BOSS': '招聘平台',
    'LIEPIN': '猎头平台',
    'EMAIL': '自动管道',
    'NEITUI': '内部渠道',
}

# Generic channel type label mapping (fallback)
_CHANNEL_TYPE_LABELS: dict[int, str] = {
    1: '官网渠道',
    2: '第三方平台',
    3: '内部渠道',
}

# Sync frequency display labels
_MAIL_FREQ_LABELS: dict[int, str] = {
    0: '手动',
    15: '每 15 分钟',
    30: '每 30 分钟',
    60: '每 60 分钟',
    120: '每 2 小时',
    360: '每 6 小时',
    1440: '每天',
}

# Reverse: display label → minutes
_MAIL_FREQ_REVERSE: dict[str, int] = {v: k for k, v in _MAIL_FREQ_LABELS.items()}

# Status-to-role mapping for role_permissions (from enums.py)
_ROLE_LABELS = {
    'admin': '管理员', 'hr': 'HR 专员', 'interviewer': '面试官',
    'temp_interviewer': '临时面试官', 'dept_head': '部门负责人',
    'employee': '基层员工', 'no_recruit': '无权限员工',
}
_ROLE_MENUS_MAP = {
    'admin': '全部 6 项', 'hr': '看板/需求/人才库/面试',
    'dept_head': '看板/需求管理', 'employee': '看板/需求管理',
    'interviewer': '看板/面试计划', 'temp_interviewer': '看板/面试计划',
    'no_recruit': '侧边栏隐藏',
}
_ROLE_DATA_SCOPE = {
    'admin': '全量无隔离', 'hr': '全公司',
    'dept_head': '本部门', 'employee': '仅自己的需求',
    'interviewer': '仅自己场次', 'temp_interviewer': '仅本次场次',
    'no_recruit': '—',
}
_ROLE_OPS = {
    'admin': '全部', 'hr': 'CRUD + 发Offer',
    'dept_head': '审批需求', 'employee': '提交需求',
    'interviewer': '填评价', 'temp_interviewer': '填评价',
    'no_recruit': '—',
}
_ROLE_BADGE_CLASS = {
    'admin': 'role-admin', 'hr': 'role-hr',
    'interviewer': 'role-interviewer', 'temp_interviewer': 'role-interviewer',
    'dept_head': '', 'employee': '', 'no_recruit': '',
}


# ════════════════════════════════════════════════════════════════════
#  Score rules
# ════════════════════════════════════════════════════════════════════

def get_score_rules():
    """Return score rule config — DB first, mock fallback."""
    try:
        from app.models.infrastructure import ScoreRule
        rule = ScoreRule.active().filter(ScoreRule.status == 1).first()
        if rule and rule.weight_json:
            w = rule.weight_json
            return {
                'id': rule.id,
                'profileWeight': float(w.get('profileWeight', 0.10)),
                'matchWeight': float(w.get('matchWeight', 0.90)),
                'decay30': float(w.get('decay30', 1.0)),
                'decay90': float(w.get('decay90', 0.85)),
                'decayOver90': float(w.get('decayOver90', 0.70)),
                'passLine': float(rule.pool_min_score or 60),
                'topCount': int(w.get('topCount', 5)),
                'searchRange': w.get('searchRange', '近 3 个月'),
                'autoInviteMinScore': (
                    float(rule.auto_invite_min_score)
                    if rule.auto_invite_min_score is not None
                    else None
                ),
                'scoreScene': rule.score_scene,
            }
    except Exception as exc:
        log.error("DB query failed in get_score_rules: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return {
        'id': 1,
        'profileWeight': 0.10, 'matchWeight': 0.90,
        'decay30': 1.0, 'decay90': 0.85, 'decayOver90': 0.70,
        'passLine': 60, 'topCount': 5, 'searchRange': '近 3 个月',
    }


def update_score_rules(data):
    """Update score rules — DB first, mock fallback."""
    try:
        from app.models.infrastructure import ScoreRule
        from app.extensions import db

        rule = ScoreRule.active().first()
        if not rule:
            rule = ScoreRule(score_scene=1, weight_json={}, pool_min_score=60, status=1)
            db.session.add(rule)

        # weight_json fields
        w = dict(rule.weight_json) if rule.weight_json else {}
        for key in ('profileWeight', 'matchWeight', 'decay30', 'decay90',
                    'decayOver90', 'topCount', 'searchRange'):
            if key in data:
                w[key] = data[key]

        if w:
            rule.weight_json = w
        if 'passLine' in data:
            rule.pool_min_score = float(data['passLine'])
        if 'autoInviteMinScore' in data:
            val = data['autoInviteMinScore']
            rule.auto_invite_min_score = float(val) if val is not None else None
        if 'scoreScene' in data:
            rule.score_scene = int(data['scoreScene'])

        db.session.commit()
        return {**data, 'updated': True, 'id': rule.id}
    except Exception as exc:
        log.error("DB write failed in update_score_rules: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise

    log.info("[MOCK] update_score_rules called with: %s", data)
    return {**data, 'updated': True}


# ════════════════════════════════════════════════════════════════════
#  Email accounts
# ════════════════════════════════════════════════════════════════════

def get_email_accounts():
    """Return email account list — DB first, mock fallback."""
    try:
        from app.models.auxiliary import RecruitMailAccount
        rows = RecruitMailAccount.active().all()
        if rows:
            return [_mail_account_to_dict(r) for r in rows]
    except Exception as exc:
        log.error("DB query failed in get_email_accounts: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return _mock_email_accounts() if _mock_enabled() else []


def _format_sync_freq(minutes: int) -> str:
    if minutes in _MAIL_FREQ_LABELS:
        return _MAIL_FREQ_LABELS[minutes]
    return f'每 {minutes} 分钟'


def _mail_account_to_dict(account):
    freq_minutes = getattr(account, 'sync_freq', 30) or 30
    freq_label = _format_sync_freq(freq_minutes)
    enabled = account.status == 1
    last_sync_dt = getattr(account, 'last_sync_time', None) or account.updated_at
    last_sync = last_sync_dt.strftime('%m-%d %H:%M') if last_sync_dt else '—'

    return {
        'id': account.id,
        'address': account.email_address,
        'type': getattr(account, 'mail_type', None) or '企业邮箱',
        'freq': freq_label,
        'status': '正常' if enabled else '异常',
        'statusColor': 'done' if enabled else 'warn',
        'lastSync': last_sync,
        # Extra detail fields for edit modal
        'proto': 'IMAP（推荐）',
        'server': account.imap_host or '',
        'port': str(account.imap_port) if account.imap_port else '993',
        'ssl': 'SSL/TLS',
        'folder': account.monitor_folder or 'INBOX',
        'syncFreqMin': freq_minutes,
    }


def _mock_email_accounts():
    return [
        {'id': 1, 'address': 'hr-recruit@company.com', 'type': '企业邮箱', 'freq': '每 30 分钟',
         'status': '正常', 'statusColor': 'done', 'lastSync': '07-14 14:30'},
        {'id': 2, 'address': 'hr-recruit@qq.com', 'type': 'QQ 邮箱', 'freq': '每 60 分钟',
         'status': '异常', 'statusColor': 'warn', 'lastSync': '07-13 09:00'},
    ]


# 邮箱地址格式校验（后端权威校验，与前端保持一致）
_EMAIL_RE = r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}'


def _validate_email_address(address):
    """Raise VALIDATION_ERROR when the address is not a valid email format."""
    import re as _re
    if not address:
        raise AppError('VALIDATION_ERROR', '邮箱地址不能为空')
    if not _re.fullmatch(_EMAIL_RE, str(address).strip()):
        raise AppError('VALIDATION_ERROR', f'邮箱地址格式不正确：{address}')


def create_email_account(data):
    """Create a new email account — DB first, mock fallback.

    Reuses soft-deleted accounts instead of failing on duplicate.
    """
    address = (data.get('address') or '').strip()
    _validate_email_address(address)

    try:
        from app.models.auxiliary import RecruitMailAccount
        from app.extensions import db

        # Check for existing account (including soft-deleted)
        existing = RecruitMailAccount.query.filter_by(email_address=address).first()
        if existing:
            if existing.is_deleted:
                # Reactivate soft-deleted account: apply new config, reset state
                existing.is_deleted = 0
                existing.imap_host = data.get('server') or existing.imap_host
                existing.imap_port = int(data.get('port')) if data.get('port') else existing.imap_port
                existing.password_encrypted = _encrypt_mail_password(data.get('pass')) or existing.password_encrypted
                existing.mail_type = data.get('type') or existing.mail_type
                existing.monitor_folder = data.get('folder') or existing.monitor_folder or 'INBOX'
                existing.status = 1
                existing.last_sync_time = None
                db.session.commit()
                return {'created': True, 'id': existing.id, 'address': address, 'reactivated': True}
            else:
                raise AppError('VALIDATION_ERROR', f'邮箱 {address} 已存在，请勿重复添加')

        freq_label = data.get('freq', '每 30 分钟')
        freq_minutes = _MAIL_FREQ_REVERSE.get(freq_label, 30)

        account = RecruitMailAccount(
            email_address=address,
            account_name=data.get('name', address.split('@')[0]),
            imap_host=data.get('server'),
            imap_port=int(data.get('port')) if data.get('port') else None,
            owner_user_id=data.get('owner_user_id'),
            status=data.get('status', 1),
            monitor_folder=data.get('folder', 'INBOX'),
            mail_type=data.get('type'),
            sync_freq=freq_minutes,
            password_encrypted=_encrypt_mail_password(data.get('pass')),
        )
        db.session.add(account)
        db.session.commit()

        # Real IMAP connection test when requested
        if data.get('__test_conn'):
            from app.services.email_sync_service import test_connection
            ok, msg = test_connection(account)
            account.last_sync_time = datetime.now() if ok else None
            db.session.commit()
            if not ok:
                return {'created': True, 'id': account.id, 'address': address,
                        'test_ok': False, 'test_msg': msg}

        return {'created': True, 'id': account.id, 'address': address}
    except AppError:
        raise
    except Exception as exc:
        from sqlalchemy.exc import IntegrityError
        if isinstance(exc, IntegrityError):
            db.session.rollback()
            log.error("Integrity error in create_email_account: %s", exc)
            raise AppError('VALIDATION_ERROR',
                           f'邮箱 {address} 已存在或数据冲突，请检查后重试')
        log.error("DB write failed in create_email_account: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise

    log.info("[MOCK] create_email_account called with: %s", data)
    return {'created': True, 'address': address}


def update_email_account(account_id, data):
    """Update or soft-delete an email account."""
    if 'address' in data and data['address']:
        _validate_email_address(str(data['address']).strip())
    try:
        from app.models.auxiliary import RecruitMailAccount
        from app.extensions import db

        account = RecruitMailAccount.active().filter_by(id=account_id).first()
        if not account:
            raise AppError('NOT_FOUND', f'邮箱账号不存在: {account_id}')

        if data.get('_delete'):
            account.soft_delete()
            db.session.commit()
            return {'deleted': True, 'account_id': int(account_id)}

        if 'status' in data:
            raw = data['status']
            account.status = 1 if raw in ('正常', '启用', 1, True, '1') else 0
        if 'address' in data:
            account.email_address = data['address']
        if 'server' in data:
            account.imap_host = data['server']
        if 'port' in data and data['port']:
            account.imap_port = int(data['port'])
        if 'folder' in data:
            account.monitor_folder = data['folder']
        if 'type' in data:
            account.mail_type = data['type']
        if 'freq' in data:
            account.sync_freq = _MAIL_FREQ_REVERSE.get(data['freq'], 30)
        if 'pass' in data and data['pass']:
            account.password_encrypted = _encrypt_mail_password(data['pass'])
        if '__test_conn' in data:
            from app.services.email_sync_service import test_connection
            ok, msg = test_connection(account)
            account.last_sync_time = datetime.now() if ok else account.last_sync_time
            db.session.commit()
            return {'updated': True, 'id': int(account_id), 'test_ok': ok, 'test_msg': msg}

        db.session.commit()
        return {'updated': True, 'id': int(account_id)}
    except AppError:
        raise
    except Exception as exc:
        log.error("DB write failed in update_email_account: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise

    log.info("[MOCK] update_email_account called: id=%s data=%s", account_id, data)
    return {'updated': True, 'id': int(account_id)}


def delete_email_account(account_id):
    """Soft-delete an email account — DB first, mock fallback."""
    try:
        from app.models.auxiliary import RecruitMailAccount
        from app.extensions import db

        account = RecruitMailAccount.query.filter_by(id=account_id).first()
        if not account:
            raise AppError('NOT_FOUND', f'邮箱账号不存在: {account_id}')

        account.soft_delete()
        db.session.commit()
        return {'deleted': True, 'account_id': int(account_id)}
    except AppError:
        raise
    except Exception as exc:
        log.error("DB delete failed in delete_email_account: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise

    log.info("[MOCK] delete_email_account called: id=%s", account_id)
    return {'deleted': True, 'account_id': int(account_id)}


# ════════════════════════════════════════════════════════════════════
#  Channels
# ════════════════════════════════════════════════════════════════════

def get_channels():
    """Return channel list — DB first, mock fallback."""
    try:
        from app.models.infrastructure import RecruitChannel
        rows = RecruitChannel.active().all()
        if rows:
            return [_channel_to_dict(r) for r in rows]
    except Exception as exc:
        log.error("DB query failed in get_channels: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return _mock_channels() if _mock_enabled() else []


def _channel_to_dict(channel):
    name = channel.channel_name
    code = _CHANNEL_CODE_MAP.get(name, name)
    type_label = _CHANNEL_TYPE_OVERRIDE.get(
        code,
        _CHANNEL_TYPE_LABELS.get(channel.channel_type, '未知'),
    )
    cost = _CHANNEL_COST_MAP.get(code, '¥0')
    status_label = '启用' if channel.status == 1 else '停用'
    return {
        'id': channel.id,
        'code': code,
        'name': name,
        'type': type_label,
        'cost': cost,
        'status': status_label,
    }


def _mock_channels():
    return [
        {'id': 1, 'code': 'BOSS', 'name': 'Boss直聘', 'type': '招聘平台', 'cost': '¥8,000', 'status': '启用'},
        {'id': 2, 'code': 'LIEPIN', 'name': '猎聘', 'type': '猎头平台', 'cost': '¥12,000', 'status': '启用'},
        {'id': 3, 'code': 'EMAIL', 'name': '邮箱采集', 'type': '自动管道', 'cost': '¥0', 'status': '启用'},
        {'id': 4, 'code': 'NEITUI', 'name': '内部推荐', 'type': '内部渠道', 'cost': '¥3,000', 'status': '启用'},
    ]


def create_channel(data):
    """Create a new channel — DB first, mock fallback."""
    name = data.get('name')
    if not name:
        raise AppError('VALIDATION_ERROR', '渠道名称不能为空')
    try:
        from app.models.infrastructure import RecruitChannel
        from app.extensions import db
        ch_type = 2  # default: third-party platform
        if data.get('type') in ('内部渠道', '内推'):
            ch_type = 3
        elif data.get('type') in ('官网渠道',):
            ch_type = 1
        channel = RecruitChannel(
            channel_name=name,
            channel_type=ch_type,
            status=data.get('status', 1),
        )
        db.session.add(channel)
        db.session.commit()
        if data.get('cost'):
            _CHANNEL_COST_MAP[name.upper()] = str(data['cost'])
        return {'created': True, 'id': channel.id, 'name': name}
    except Exception as exc:
        log.error("DB write failed in create_channel: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return {'created': True, 'name': name}


def update_channel(code, data):
    """Update a channel config — DB first, mock fallback."""
    name = _REVERSE_CHANNEL_CODE.get(code, code)

    try:
        from app.models.infrastructure import RecruitChannel
        from app.extensions import db

        channel = RecruitChannel.active().filter(
            RecruitChannel.channel_name == name
        ).first()
        if not channel:
            raise AppError('NOT_FOUND', f'渠道不存在: {code}')

        if 'cost' in data:
            _CHANNEL_COST_MAP[code] = str(data['cost'])

        if 'status' in data:
            raw = data['status']
            channel.status = 1 if raw in ('启用', 1, True, '1') else 0

        if 'name' in data:
            channel.channel_name = data['name']
            _CHANNEL_CODE_MAP[data['name']] = code
            _REVERSE_CHANNEL_CODE[code] = data['name']

        db.session.commit()
        return {'updated': True, 'code': code}
    except AppError:
        raise
    except Exception as exc:
        log.error("DB write failed in update_channel: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise

    log.info("[MOCK] update_channel called: code=%s data=%s", code, data)
    return {'updated': True, 'code': code}


# ════════════════════════════════════════════════════════════════════
#  Notification templates
# ════════════════════════════════════════════════════════════════════

_NOTIFY_TEMPLATES = [
    {'id': 1, 'name': '面试邀请通知', 'type': '面试', 'method': '飞书 + 短信', 'updated': '07-10',
     'subject': '面试邀请 - {{position}}', 'body': '{{candidate}} 您好，诚邀您参加 {{position}} 的面试'},
    {'id': 2, 'name': 'Offer 发送模板', 'type': 'Offer', 'method': '邮件', 'updated': '07-08',
     'subject': 'Offer Letter - {{position}}', 'body': '恭喜您通过面试，正式 Offer 请查收附件'},
    {'id': 3, 'name': '未通过通知', 'type': '淘汰', 'method': '短信', 'updated': '07-01',
     'subject': '面试结果通知', 'body': '感谢您参加 {{position}} 面试，本次未能匹配'},
    {'id': 4, 'name': '面试提醒（前一天）', 'type': '提醒', 'method': '飞书 + 短信', 'updated': '06-28',
     'subject': '面试提醒', 'body': '明天 {{time}} 有 {{position}} 面试，请准时参加'},
]


def get_notify_templates():
    """Return notification templates — DB first, mock fallback."""
    try:
        from app.models.auxiliary import NotifyTemplate
        rows = NotifyTemplate.active().filter(NotifyTemplate.status == 1).order_by(
            NotifyTemplate.updated_at.desc()
        ).all()
        if rows:
            result = []
            for t in rows:
                result.append({
                    'id': t.id,
                    'name': t.template_name,
                    'type': t.template_type,
                    'method': t.send_method or '—',
                    'subject': t.subject or '',
                    'body': t.body or '',
                    'updated': t.updated_at.strftime('%m-%d') if t.updated_at else '—',
                })
            return result
    except Exception as exc:
        log.error("DB query failed in get_notify_templates: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return _NOTIFY_TEMPLATES if _mock_enabled() else []


def create_notify_template(data):
    """Create a new notification template."""
    try:
        from app.models.auxiliary import NotifyTemplate
        from app.extensions import db

        t = NotifyTemplate(
            template_name=data.get('name', ''),
            template_type=data.get('type', '通用'),
            send_method=data.get('method', ''),
            subject=data.get('subject', ''),
            body=data.get('body', ''),
            status=1,
        )
        db.session.add(t)
        db.session.commit()
        return {'created': True, 'id': t.id, 'name': t.template_name}
    except Exception as exc:
        log.error("DB write failed in create_notify_template: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return {'created': True, 'name': data.get('name', '')}


def update_notify_template(template_id, data):
    """Update an existing notification template."""
    try:
        from app.models.auxiliary import NotifyTemplate, AuditLog
        from app.extensions import db

        t = NotifyTemplate.active().filter_by(id=template_id).first()
        if not t:
            raise AppError('NOT_FOUND', f'模板不存在: {template_id}')

        for field in ('name', 'type', 'method', 'subject', 'body'):
            key = 'template_name' if field == 'name' else \
                  'template_type' if field == 'type' else \
                  'send_method' if field == 'method' else field
            if field in data:
                setattr(t, key, data[field])

        db.session.commit()
        return {'updated': True, 'id': int(template_id)}
    except AppError:
        raise
    except Exception as exc:
        log.error("DB write failed in update_notify_template: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return {'updated': True, 'id': int(template_id)}


# ════════════════════════════════════════════════════════════════════
#  Role permissions
# ════════════════════════════════════════════════════════════════════

def get_role_permissions():
    """Return role permission matrix from enums."""
    return [
        {
            'role': _ROLE_LABELS.get(rk, rk),
            'badgeClass': _ROLE_BADGE_CLASS.get(rk, ''),
            'menus': _ROLE_MENUS_MAP.get(rk, '—'),
            'dataScope': _ROLE_DATA_SCOPE.get(rk, '—'),
            'ops': _ROLE_OPS.get(rk, '—'),
        }
        for rk in ['admin', 'hr', 'dept_head', 'employee', 'interviewer', 'no_recruit']
    ]


# ════════════════════════════════════════════════════════════════════
#  Audit logs
# ════════════════════════════════════════════════════════════════════

def get_audit_logs(limit=50):
    """Return audit logs — DB first, mock fallback."""
    try:
        from app.models.auxiliary import AuditLog
        rows = AuditLog.active().order_by(
            AuditLog.operate_time.desc()
        ).limit(limit).all()
        if rows:
            return [_audit_log_to_dict(r) for r in rows]
    except Exception as exc:
        log.error("DB query failed in get_audit_logs: %s", exc, exc_info=True)
        if not _mock_enabled():
            raise
    return _mock_audit_logs() if _mock_enabled() else []


def append_audit_log(operator_name, module, action, detail=''):
    """Write a new audit log entry — DB first, silent fail on error."""
    try:
        from app.models.auxiliary import AuditLog
        from app.extensions import db
        log_entry = AuditLog(
            operator_name=operator_name,
            module=module,
            action=action,
            detail=detail,
            operate_time=datetime.now(),
        )
        db.session.add(log_entry)
        db.session.commit()
    except Exception as exc:
        log.error("DB write failed in append_audit_log: %s", exc, exc_info=True)


def _audit_log_to_dict(al):
    return {
        'id': al.id,
        'time': al.operate_time.strftime('%m-%d %H:%M') if al.operate_time else '—',
        'user': al.operator_name,
        'module': al.module,
        'action': al.action,
        'detail': al.detail or '',
    }


def _mock_audit_logs():
    return [
        {'id': 1, 'time': '07-14 14:30', 'user': '张HR', 'module': '面试', 'action': '发起面试',
         'detail': '张三 → 高级Java工程师初试，面试官李面试官'},
        {'id': 2, 'time': '07-14 11:20', 'user': '李面试官', 'module': '面试', 'action': '提交评价',
         'detail': '郑一·前端终面·通过'},
        {'id': 3, 'time': '07-14 10:05', 'user': '张HR', 'module': '需求', 'action': '新建需求',
         'detail': 'DM2026070005 高级Java工程师·技术部·2人'},
        {'id': 4, 'time': '07-14 09:00', 'user': '系统', 'module': '邮件', 'action': '自动同步',
         'detail': 'hr-recruit@company.com 拉取 3 封邮件，识别 2 封简历'},
    ]


# ════════════════════════════════════════════════════════════════════
#  AI capabilities (static config, no DB)
# ════════════════════════════════════════════════════════════════════

def get_ai_capabilities():
    """Return AI capability matrix — this is static config, kept as-is."""
    return [
        {'ability': '简历 AI 解析 + 画像生成 + 标签打标', 'page': '邮件管理 / 人才库上传',
         'trigger': '邮箱定时同步 or 手动上传 PDF/DOCX', 'workflow': '① 简历画像解析', 'status': 'done'},
        {'ability': '审批通过自动匹配（内外并行）', 'page': '需求管理',
         'trigger': '三步审批全部通过后系统自动触发', 'workflow': '② 人岗匹配打分', 'status': 'done'},
        {'ability': 'AI 辅助联系话术', 'page': '需求详情 / 面试计划弹窗',
         'trigger': '约面前生成电话/邮件/飞书联系话术，人工确认意向后记录结果',
         'workflow': '需新增候选人沟通工作流', 'status': 'done'},
        {'ability': 'AI 面试评价草稿', 'page': '面试计划', 'trigger': '面试结束后自动生成',
         'workflow': '③ 面试问题生成（扩展）', 'status': 'warn'},
        {'ability': '简历去重合并', 'page': '人才库', 'trigger': '上传/同步时自动检测跨渠道重复',
         'workflow': '① 画像解析（扩展）', 'status': 'warn'},
        {'ability': '简历识别 + 垃圾过滤', 'page': '邮件管理', 'trigger': '收邮件时预处理，过滤非简历邮件',
         'workflow': '需新增分类器', 'status': 'warn'},
        {'ability': 'Offer 草稿与审批辅助', 'page': '面试计划 → Offer管理',
         'trigger': '填写审批信息「张三，薪资18K」→ 生成草稿，审批后发送',
         'workflow': '需新增 Offer 工作流', 'status': 'done'},
        {'ability': '入职包草稿与推送辅助', 'page': '面试计划 → 入职管理',
         'trigger': '候选人接受 Offer → 系统生成入职材料清单，HR 确认后推送',
         'workflow': '需新增入职工作流', 'status': 'done'},
        {'ability': '招聘风险预警', 'page': '招聘看板', 'trigger': '页面加载时自动分析',
         'workflow': '规则引擎 + AI 异常检测', 'status': 'draft'},
    ]


# ── API Key management ──

_API_KEY_DISPLAY = {
    'deepseek': {'label': 'DeepSeek API Key', 'desc': 'AI 简历解析、匹配打分、JD生成',
                 'link': 'https://platform.deepseek.com/api_keys', 'link_text': '去 DeepSeek 开放平台获取 Key'},
    'feishu_app_id': {'label': '飞书 App ID',  'desc': '消息通知、审批推送（与 App Secret 配对使用）',
                      'link': 'https://open.feishu.cn/app', 'link_text': '去飞书开放平台创建应用，获取 App ID 与 App Secret'},
    'feishu':   {'label': '飞书 App Secret',  'desc': '消息通知、审批推送（与 App ID 配对使用）',
                 'link': 'https://open.feishu.cn/app', 'link_text': '去飞书开放平台创建应用，获取 App ID 与 App Secret'},
    'dify':     {'label': 'Dify API Key',     'desc': 'Dify 工作流引擎（兼容保留）'},
    'tencent_appid':     {'label': '腾讯会议 AppId',       'desc': '腾讯会议开放平台应用 AppId'},
    'tencent_secretid':  {'label': '腾讯会议 SecretId',    'desc': '腾讯会议开放平台 SecretId'},
    'tencent_secretkey': {'label': '腾讯会议 SecretKey',   'desc': '腾讯会议开放平台 SecretKey（加密存储）'},
    'tencent_userid':    {'label': '腾讯会议主持人 UserID', 'desc': '创建会议时的主持人 userid（必填）'},
}

# Tencent Meeting credential key names stored in ApiKeyConfig
TENCENT_MEETING_KEY_NAMES = (
    'tencent_appid', 'tencent_secretid', 'tencent_secretkey', 'tencent_userid',
)


def get_api_keys():
    """Return masked API key entries — never expose raw values."""
    from app.extensions import db
    from app.models.auxiliary import ApiKeyConfig

    result = {}
    for key_name, display in _API_KEY_DISPLAY.items():
        row = ApiKeyConfig.active().filter_by(key_name=key_name).first()
        masked = '••••••••'
        has_value = False
        if row and row.value_encrypted:
            masked = _mask_value(row.value_encrypted)
            has_value = True
        result[key_name] = {
            'key_name': key_name,
            'label': display['label'],
            'desc': display['desc'],
            'masked': masked,
            'has_value': has_value,
            'link': display.get('link'),
            'link_text': display.get('link_text'),
        }
    return result


def save_api_keys(data):
    """Encrypt and persist API keys."""
    from flask import current_app
    from app.extensions import db
    from app.models.auxiliary import ApiKeyConfig

    saved_keys = []
    secret = current_app.config['SECRET_KEY']
    for key_name in _API_KEY_DISPLAY:
        raw = data.get(key_name, '')
        if not raw:
            continue
        # Don't re-encrypt if the user sent the masked placeholder
        if raw.strip() == '••••••••':
            continue

        row = ApiKeyConfig.active().filter_by(key_name=key_name).first()
        encrypted = encrypt(raw, secret)
        if row:
            row.value_encrypted = encrypted
        else:
            row = ApiKeyConfig(
                key_name=key_name,
                value_encrypted=encrypted,
                display_label=_API_KEY_DISPLAY[key_name]['label'],
            )
            db.session.add(row)
        saved_keys.append(key_name)
    if saved_keys:
        db.session.commit()
    return {'saved': True, 'keys': saved_keys}


def get_decrypted_api_key(key_name):
    """Decrypt the stored API key. Used internally by services (e.g. deepseek_client).
    Returns None if not configured."""
    from flask import current_app
    from app.extensions import db
    from app.models.auxiliary import ApiKeyConfig

    row = ApiKeyConfig.active().filter_by(key_name=key_name).first()
    if not row or not row.value_encrypted:
        return None
    secret = current_app.config['SECRET_KEY']
    try:
        return decrypt(row.value_encrypted, secret)
    except Exception:
        return None


def test_api_key(key_name):
    """Test whether an API key actually works against its provider.

    Resolution order mirrors real usage: environment variable first, then the
    encrypted DB value. Returns a safe dict (never contains the key itself):

        {"ok": bool, "supported": bool, "message": str,
         "source": "env"|"db"|None, "tested_at": iso8601}
    """
    import os

    result = {'ok': False, 'supported': True, 'message': '', 'source': None,
              'tested_at': datetime.now().isoformat(timespec='seconds')}

    if key_name not in ('deepseek', 'feishu', 'dify'):
        result['supported'] = False
        result['message'] = '该密钥暂不支持连通性测试'
        return result

    if key_name == 'dify':
        result['supported'] = False
        result['message'] = 'Dify 为兼容保留配置，暂不支持连通性测试'
        return result

    if key_name == 'deepseek':
        key = os.getenv('DEEPSEEK_API_KEY') or get_decrypted_api_key('deepseek')
        result['source'] = 'env' if os.getenv('DEEPSEEK_API_KEY') else ('db' if key else None)
        if not key:
            result['message'] = '未配置密钥，请先保存'
            return result
        from config import Config
        base = (getattr(Config, 'DEEPSEEK_BASE_URL', '') or 'https://api.deepseek.com').rstrip('/')
        try:
            resp = requests.get(f'{base}/models',
                                headers={'Authorization': f'Bearer {key}'}, timeout=10)
        except requests.RequestException as exc:
            result['message'] = f'网络请求失败：{exc.__class__.__name__}'
            return result
        if resp.status_code == 200:
            result['ok'] = True
            result['message'] = '连接成功，密钥可用'
        elif resp.status_code in (401, 403):
            result['message'] = '密钥无效或已过期（HTTP %d）' % resp.status_code
        else:
            result['message'] = f'服务返回 HTTP {resp.status_code}'
        return result

    # feishu: app_id / secret 均为环境变量优先，其次取配置页加密集群
    app_id = os.getenv('FEISHU_APP_ID') or get_decrypted_api_key('feishu_app_id') or ''
    secret = os.getenv('FEISHU_APP_SECRET') or get_decrypted_api_key('feishu')
    result['source'] = 'env' if os.getenv('FEISHU_APP_SECRET') else ('db' if secret else None)
    if not secret:
        result['message'] = '未配置 App Secret，请先保存'
        return result
    if not app_id:
        result['message'] = '未配置 App ID，请先保存'
        return result
    try:
        resp = requests.post(
            'https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal',
            json={'app_id': app_id, 'app_secret': secret}, timeout=10)
        body = resp.json()
    except requests.RequestException as exc:
        result['message'] = f'网络请求失败：{exc.__class__.__name__}'
        return result
    except ValueError:
        result['message'] = f'服务返回非 JSON 响应（HTTP {resp.status_code}）'
        return result
    if body.get('code') == 0:
        result['ok'] = True
        result['message'] = '连接成功，已获取 tenant_access_token'
    else:
        result['message'] = f"飞书返回错误：{body.get('msg', '未知错误')}（code={body.get('code')}）"
    return result


def get_tencent_meeting_credentials():
    """Return decrypted Tencent Meeting credentials as a dict.

    Values are None when not configured. Used internally by
    ``tencent_meeting_client`` — never expose via API.
    """
    return {k: get_decrypted_api_key(k) for k in TENCENT_MEETING_KEY_NAMES}


def get_tencent_meeting_status():
    """Return whether all four Tencent Meeting credentials are configured.

    Safe for API responses — contains booleans only, no values.
    """
    from app.models.auxiliary import ApiKeyConfig

    fields = {}
    for key_name in TENCENT_MEETING_KEY_NAMES:
        row = ApiKeyConfig.active().filter_by(key_name=key_name).first()
        fields[key_name] = bool(row and row.value_encrypted)
    return {'configured': all(fields.values()), 'fields': fields}


def get_feishu_status():
    """Return whether the Feishu credential pair (App ID + App Secret) is configured.

    Resolution order mirrors real usage: environment variables first, then the
    encrypted DB values. Safe for API responses — booleans only, no values.
    """
    import os

    app_id = bool(os.getenv('FEISHU_APP_ID') or get_decrypted_api_key('feishu_app_id'))
    app_secret = bool(os.getenv('FEISHU_APP_SECRET') or get_decrypted_api_key('feishu'))
    return {
        'configured': app_id and app_secret,
        'fields': {'app_id': app_id, 'app_secret': app_secret},
    }


def _mask_value(encrypted: str) -> str:
    """Show first 4 + last 4 chars with mask dots."""
    if len(encrypted) <= 8:
        return '••••••••'
    return encrypted[:4] + '••••••••' + encrypted[-4:]
