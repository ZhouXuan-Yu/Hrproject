"""Config API: /api/config/*"""
import logging

from flask import Blueprint, request, g
from app.utils.response import success, success_list, error

log = logging.getLogger(__name__)

bp = Blueprint('config', __name__)


@bp.route('/email-accounts/detect', methods=['POST'])
def detect_email_server():
    """POST /api/config/email-accounts/detect — auto-detect IMAP server from email."""
    from app.services.email_sync_service import detect_imap_server
    body = request.get_json(silent=True) or {}
    email = body.get('email', '')
    if not email or '@' not in email:
        return error('BAD_REQUEST', '请提供有效的邮箱地址')
    result = detect_imap_server(email)
    return success(result)


# 进程内短缓存：{email: (timestamp, result)}，10 分钟过期
_RESOLVE_CACHE: dict = {}
_RESOLVE_CACHE_TTL = 600


@bp.route('/email-accounts/resolve')
def resolve_email_server():
    """GET /api/config/email-accounts/resolve?email=... — MX 记录识别邮箱服务商。

    DNS 查询失败时返回兜底结果，绝不因 DNS 异常返回 500。
    """
    import time
    from app.services.email_sync_service import resolve_mail_server
    email = (request.args.get('email') or '').strip()
    if not email or '@' not in email:
        return error('BAD_REQUEST', '请提供有效的邮箱地址')

    now = time.time()
    cached = _RESOLVE_CACHE.get(email.lower())
    if cached and now - cached[0] < _RESOLVE_CACHE_TTL:
        return success(cached[1])

    try:
        result = resolve_mail_server(email)
    except Exception as exc:  # 兜底：任何异常都不应让接口 500
        log.warning("resolve_mail_server failed for %s: %s", email, exc)
        result = {'provider': '未知', 'imap_host': '', 'imap_port': 993,
                  'encryption': 'SSL/TLS', 'confidence': 'low', 'detection': 'unknown'}

    _RESOLVE_CACHE[email.lower()] = (now, result)
    return success(result)


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


def _run_sync_in_background(app, account_id=None):
    """无 Celery/Redis 环境的降级：后台线程执行同步。

    每个 IMAP 连接约 15s 超时；线程异步执行不阻塞 API 响应。
    """
    import threading
    import time

    def _job():
        t0 = time.time()
        with app.app_context():
            try:
                from app.services.email_sync_service import (
                    sync_all_accounts, sync_mail_account,
                )
                if account_id:
                    result = sync_mail_account(account_id)
                    log.info("Email sync (single) completed in %.1fs: %s", time.time()-t0, result.get('status'))
                else:
                    result = sync_all_accounts()
                    log.info("Email sync (all) completed in %.1fs: status=%s new=%d ingested=%d",
                             time.time()-t0, result.get('status'),
                             result.get('new_emails'), result.get('resumes_ingested'))
            except Exception as exc:
                log.error("Background email sync failed: %s", exc, exc_info=True)

    t = threading.Thread(target=_job, name='email-sync', daemon=True)
    t.start()
    return f'thread-{t.ident}'


def _enqueue_sync(account_id=None):
    """优先投递 Celery 任务；无 Redis/Celery 时降级后台线程。返回 (mode, task_id)。"""
    try:
        if account_id:
            from tasks.email_sync import sync_single_mailbox
            task = sync_single_mailbox.delay(account_id)
        else:
            from tasks.email_sync import sync_all_mailboxes
            task = sync_all_mailboxes.delay()
        return 'celery', str(task.id)
    except Exception as exc:
        log.warning("Celery enqueue failed (%s), falling back to background thread", exc)
        from flask import current_app
        return 'thread', _run_sync_in_background(current_app._get_current_object(), account_id)


@bp.route('/email-accounts/get-preview', methods=['POST'])
def get_sync_preview():
    """POST /api/config/email-accounts/get-preview — 获取邮箱预览数据（不真正收取邮件）。"""
    try:
        from app.services.config_service import get_email_accounts
        accounts = get_email_accounts()
        if not accounts:
            return success({
                'accounts_checked': 0, 'new_emails': 0, 'resumes_ingested': 0,
                'details': []
            })
        # 返回各邮箱状态和最近检查时间
        details = []
        for acct in accounts:
            details.append({
                'email': acct.get('address', ''),
                'status': acct.get('status', '未知'),
                'last_sync': acct.get('lastSync', '—'),
                'new_emails': 0,
                'resumes_ingested': 0
            })
        return success({
            'accounts_checked': len(accounts),
            'new_emails': 0,
            'resumes_ingested': 0,
            'details': details,
            '_mock': True,
        })
    except Exception as exc:
        log.error("get_sync_preview failed: %s", exc, exc_info=True)
        return success({
            'accounts_checked': 0, 'new_emails': 0, 'resumes_ingested': 0,
            'details': []
        })


@bp.route('/email-accounts/sync', methods=['POST'])
def sync_all_email_accounts():
    """POST /api/config/email-accounts/sync — 手动刷新：异步同步所有启用邮箱。

    立即返回 accepted（无 Celery 时代码依然自洽）；前端提示"同步已开始，稍后刷新"。
    """
    try:
        mode, task_id = _enqueue_sync()
        return success({
            'accepted': True, 'mode': mode, 'taskId': task_id,
            'message': '同步已开始，请稍后刷新查看结果',
        })
    except Exception as exc:
        log.error("sync_all_email_accounts failed: %s", exc, exc_info=True)
        import traceback
        log.error(traceback.format_exc())
        return error('SYNC_FAILED', str(exc))


@bp.route('/email-accounts/<account_id>/sync', methods=['POST'])
def sync_email_account(account_id):
    """POST /api/config/email-accounts/{account_id}/sync — 异步手动刷新单个邮箱。"""
    mode, task_id = _enqueue_sync(account_id)
    return success({
        'accepted': True, 'mode': mode, 'taskId': task_id,
        'message': '同步已开始，请稍后刷新查看结果',
    })


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


@bp.route('/api-keys/test', methods=['POST'])
def test_api_key():
    """POST /api/config/api-keys/test — live connectivity test for a stored key."""
    from app.services.config_service import test_api_key as _test, append_audit_log
    data = request.get_json(silent=True) or {}
    key_name = data.get('key_name', '')
    if not key_name:
        return success({'ok': False, 'supported': False, 'message': '缺少 key_name'})
    result = _test(key_name)
    append_audit_log('系统', '配置', '测试密钥连接',
                     f"测试 {key_name}: {'成功' if result.get('ok') else '失败'}")
    return success(result)


@bp.route('/tencent-meeting/status')
def tencent_meeting_status():
    """GET /api/config/tencent-meeting/status — configured flag only, no values."""
    from app.services.config_service import get_tencent_meeting_status
    return success(get_tencent_meeting_status())


@bp.route('/feishu/status')
def feishu_status():
    """GET /api/config/feishu/status — pair-configured flag only, no values."""
    from app.services.config_service import get_feishu_status
    return success(get_feishu_status())
