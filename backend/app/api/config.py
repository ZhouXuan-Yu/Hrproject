"""Config API: /api/config/*"""
import logging

from flask import Blueprint, request, g
from app.utils.response import success, success_list, error

log = logging.getLogger(__name__)

bp = Blueprint('config', __name__)


@bp.route('/email-accounts')
def get_email_accounts():
    """GET /api/config/email-accounts"""
    from app.services.config_service import get_email_accounts
    data = get_email_accounts()
    return success(data)


@bp.route('/email-accounts', methods=['POST'])
def create_email_account():
    """POST /api/config/email-accounts"""
    from app.services.config_service import create_email_account, append_audit_log
    result = create_email_account(request.get_json(silent=True) or {})
    if result.get('created'):
        append_audit_log(
            operator_name=getattr(g, 'current_user_id', '系统'),
            module='配置',
            action='添加邮箱',
            detail=f"添加邮箱 {result.get('address', '')}"
        )
    return success(result)


@bp.route('/email-accounts/<account_id>', methods=['PUT'])
def update_email_account(account_id):
    """PUT /api/config/email-accounts/{account_id}"""
    from app.services.config_service import update_email_account, append_audit_log
    data = request.get_json(silent=True) or {}
    result = update_email_account(account_id, data)
    if result.get('deleted'):
        append_audit_log('系统', '配置', '删除邮箱', f'删除邮箱账号 #{account_id}')
    elif data.get('__test_conn'):
        append_audit_log('系统', '配置', '测试连接', f'测试邮箱 #{account_id}')
    return success(result)


@bp.route('/email-accounts/<account_id>', methods=['DELETE'])
def delete_email_account(account_id):
    """DELETE /api/config/email-accounts/{account_id}"""
    from app.services.config_service import delete_email_account, append_audit_log
    result = delete_email_account(account_id)
    if result.get('deleted'):
        append_audit_log('系统', '配置', '删除邮箱', f'删除邮箱账号 #{account_id}')
    return success(result)


@bp.route('/channels')
def get_channels():
    """GET /api/config/channels"""
    from app.services.config_service import get_channels
    data = get_channels()
    return success(data)


@bp.route('/channels', methods=['POST'])
def create_channel():
    """POST /api/config/channels"""
    from app.services.config_service import create_channel, append_audit_log
    result = create_channel(request.get_json(silent=True) or {})
    if result.get('created'):
        append_audit_log('系统', '配置', '新增渠道', f"新增渠道 {result.get('name', '')}")
    return success(result)


@bp.route('/channels/<code>', methods=['PUT'])
def update_channel(code):
    """PUT /api/config/channels/{code}"""
    from app.services.config_service import update_channel, append_audit_log
    result = update_channel(code, request.get_json(silent=True) or {})
    if result.get('updated'):
        append_audit_log('系统', '配置', '修改渠道', f"修改渠道 {code}")
    return success(result)


@bp.route('/score-rules')
def get_score_rules():
    """GET /api/config/score-rules"""
    from app.services.config_service import get_score_rules
    data = get_score_rules()
    return success(data)


@bp.route('/score-rules', methods=['PUT'])
def update_score_rules():
    """PUT /api/config/score-rules"""
    from app.services.config_service import update_score_rules, append_audit_log
    result = update_score_rules(request.get_json(silent=True) or {})
    if result.get('updated'):
        append_audit_log('系统', '配置', '修改打分规则', '更新了招聘打分权重与阈值')
    return success(result)


@bp.route('/notify-templates')
def get_notify_templates():
    """GET /api/config/notify-templates"""
    from app.services.config_service import get_notify_templates
    data = get_notify_templates()
    return success(data)


@bp.route('/notify-templates', methods=['POST'])
def create_notify_template():
    """POST /api/config/notify-templates"""
    from app.services.config_service import create_notify_template, append_audit_log
    result = create_notify_template(request.get_json(silent=True) or {})
    if result.get('created'):
        append_audit_log('系统', '配置', '新增模板', f"新增模板 {result.get('name', '')}")
    return success(result)


@bp.route('/notify-templates/<template_id>', methods=['PUT'])
def update_notify_template(template_id):
    """PUT /api/config/notify-templates/{template_id}"""
    from app.services.config_service import update_notify_template, append_audit_log
    result = update_notify_template(template_id, request.get_json(silent=True) or {})
    if result.get('updated'):
        append_audit_log('系统', '配置', '编辑模板', f"编辑模板 #{template_id}")
    return success(result)


@bp.route('/role-permissions')
def get_role_permissions():
    """GET /api/config/role-permissions"""
    from app.services.config_service import get_role_permissions
    data = get_role_permissions()
    return success(data)


@bp.route('/audit-logs')
def get_audit_logs():
    """GET /api/config/audit-logs"""
    from app.services.config_service import get_audit_logs
    data = get_audit_logs()
    return success(data)


# ── API Key management ──

_API_KEY_FIELDS = ['deepseek', 'feishu', 'dify']


@bp.route('/api-keys')
def get_api_keys():
    """GET /api/config/api-keys — returns mask values only, never raw keys."""
    from app.services.config_service import get_api_keys
    data = get_api_keys()
    return success(data)


@bp.route('/api-keys', methods=['PUT'])
def save_api_keys():
    """PUT /api/config/api-keys — encrypt and store API keys."""
    from app.services.config_service import save_api_keys, append_audit_log
    data = request.get_json(silent=True) or {}
    result = save_api_keys(data)
    if result.get('saved'):
        append_audit_log('系统', '配置', '更新密钥',
                         f"更新密钥: {', '.join(result.get('keys', []))}")
    return success(result)
