"""Enterprise mailbox sync service — real IMAP pipeline.

Auto-detection of IMAP server based on domain MX records.

Pulls unread emails from configured recruit mailboxes, detects resume
attachments (or resume-like bodies), runs the resume ingest pipeline
(extract → DeepSeek parse → dedup → DB), and records sync state.

Used by:
    - tasks/email_sync.py (Celery periodic task)
    - POST /api/config/email-accounts/<id>/sync (手动刷新)
"""
import imaplib
import logging
from datetime import datetime
from email import message_from_bytes
from email.header import decode_header
from email.utils import parseaddr

log = logging.getLogger(__name__)

# Cap per sync run to avoid hammering the LLM / mailbox on first sync
_MAX_EMAILS_PER_SYNC = 50


# ===========================================================================
# Credential handling
# ===========================================================================

# Known MX → IMAP mapping
_MX_IMAP_MAP = {
    'mxbiz1.qq.com':  ('腾讯企业邮箱', 'imap.exmail.qq.com', 993),
    'mxbiz2.qq.com':  ('腾讯企业邮箱', 'imap.exmail.qq.com', 993),
    'mx.qiye.163.com':('网易企业邮', 'imap.qiye.163.com', 993),
    'mx.qiye.aliyun.com':('阿里企业邮', 'imap.qiye.aliyun.com', 993),
    'aspmx.l.google.com':('Gmail / Google Workspace', 'imap.gmail.com', 993),
    'mx1.hc.aliyun.com':('阿里云邮箱', 'imap.qiye.aliyun.com', 993),
}

# Domain → IMAP fallback (no MX lookup)
_DOMAIN_IMAP_FALLBACK = {
    'qq.com':          ('QQ邮箱', 'imap.qq.com', 993),
    '163.com':         ('163邮箱', 'imap.163.com', 993),
    'gmail.com':       ('Gmail', 'imap.gmail.com', 993),
    'outlook.com':     ('Outlook', 'outlook.office365.com', 993),
    'hotmail.com':     ('Outlook', 'outlook.office365.com', 993),
    'aliyun.com':      ('阿里企业邮', 'imap.qiye.aliyun.com', 993),
    'exmail.qq.com':   ('腾讯企业邮箱', 'imap.exmail.qq.com', 993),
}


# MX 记录指纹（子串匹配）→ (服务商, IMAP 主机)
# 覆盖主流企业邮箱服务商；按顺序首个命中生效
_MX_FINGERPRINTS = [
    (('exmail.qq.com', 'mxbiz'),        ('腾讯企业邮', 'imap.exmail.qq.com')),
    (('qiye.163', 'ym.163'),            ('网易企业邮', 'imap.qiye.163.com')),
    (('mxhichina', 'qiye.aliyun', 'aliyun'), ('阿里企业邮', 'imap.qiye.aliyun.com')),
    (('outlook', 'office365', 'protection.outlook'), ('Microsoft 365', 'outlook.office365.com')),
    (('google', 'gmail', 'googlemail'), ('Gmail / Google Workspace', 'imap.gmail.com')),
    (('zoho',),                          ('Zoho Mail', 'imap.zoho.com')),
    (('263.net', '263xmail'),            ('263 企业邮箱', 'imap.263.net')),
    (('sina',),                          ('新浪企业邮箱', 'imap.ex.sina.com')),
    (('feishu', 'larksuite'),            ('飞书邮箱', 'imap.feishu.cn')),
]

# 域名本身包含的关键字 → (服务商, IMAP 主机)（MX 查询失败时的兜底）
_DOMAIN_KEYWORD_FALLBACK = [
    (('exmail.qq',),   ('腾讯企业邮', 'imap.exmail.qq.com')),
    (('qiye.163',),    ('网易企业邮', 'imap.qiye.163.com')),
    (('qq',),          ('QQ邮箱', 'imap.qq.com')),
    (('163',),         ('163邮箱', 'imap.163.com')),
    (('126',),         ('126邮箱', 'imap.126.com')),
    (('gmail', 'googlemail'), ('Gmail', 'imap.gmail.com')),
    (('outlook', 'hotmail', 'live'), ('Outlook', 'outlook.office365.com')),
    (('aliyun',),      ('阿里企业邮', 'imap.qiye.aliyun.com')),
    (('zoho',),        ('Zoho Mail', 'imap.zoho.com')),
    (('sina',),        ('新浪邮箱', 'imap.sina.com')),
    (('139',),         ('139邮箱', 'imap.139.com')),
]


def resolve_mail_server(email_address):
    """通过 MX 记录自动识别企业邮箱服务商，返回收件服务器配置建议。

    流程：拆域名 → dnspython 查 MX（短超时，任何异常都不抛出）→
    指纹匹配 → 域名关键字兜底 → imap.<domain> 通用猜测。

    返回 dict:
        {provider, imap_host, imap_port: 993, encryption: 'SSL/TLS',
         confidence: 'high'|'medium'|'low', detection: 'mx'|'domain'|'pattern'|'unknown'}
    """
    result = {
        'provider': '未知', 'imap_host': '', 'imap_port': 993,
        'encryption': 'SSL/TLS', 'confidence': 'low', 'detection': 'unknown',
    }
    domain = email_address.split('@')[-1].strip().lower() if '@' in (email_address or '') else ''
    if not domain:
        return result

    # 1. MX 记录查询（3-5 秒短超时；DNS 失败绝不抛出）
    mx_hosts = []
    try:
        import dns.resolver
        resolver = dns.resolver.Resolver()
        resolver.lifetime = 5.0
        resolver.timeout = 3.0
        answers = resolver.resolve(domain, 'MX')
        mx_hosts = [str(r.exchange).rstrip('.').lower()
                    for r in sorted(answers, key=lambda r: r.preference)]
    except Exception as exc:
        log.info("MX lookup failed for %s: %s", domain, exc)

    for mx in mx_hosts:
        for keywords, (provider, host) in _MX_FINGERPRINTS:
            if any(k in mx for k in keywords):
                result.update(provider=provider, imap_host=host,
                              confidence='high', detection='mx')
                return result

    # 2. 域名关键字兜底（MX 查询失败或未命中时）
    for keywords, (provider, host) in _DOMAIN_KEYWORD_FALLBACK:
        if any(k in domain for k in keywords):
            result.update(provider=provider, imap_host=host,
                          confidence='medium', detection='domain')
            return result

    # 3. 通用猜测 imap.<domain>
    result.update(provider=f'{domain} 邮箱', imap_host=f'imap.{domain}',
                  confidence='low', detection='pattern')
    return result


def detect_imap_server(email_address):
    """Auto-detect IMAP server from email domain.

    Returns dict with {provider, imap_host, imap_port}.
    First checks MX records, then falls back to known domain patterns,
    finally suggests common IMAP servers.
    """
    import socket
    domain = email_address.split('@')[-1].lower() if '@' in email_address else ''

    # 1. Check domain fallback first (fast, no DNS)
    if domain in _DOMAIN_IMAP_FALLBACK:
        provider, host, port = _DOMAIN_IMAP_FALLBACK[domain]
        return {'provider': provider, 'imap_host': host, 'imap_port': port, 'detection': 'domain'}

    # 2. Try MX record lookup (2s timeout — detect 接口被前端输入防抖高频触发，
    #    DNS 异常时不能长时间阻塞请求线程)
    try:
        import dns.resolver
        resolver = dns.resolver.Resolver()
        resolver.lifetime = 2.0
        resolver.timeout = 1.0
        answers = resolver.resolve(domain, 'MX')
        for rdata in sorted(answers, key=lambda r: r.preference):
            mx = str(rdata.exchange).rstrip('.').lower()
            if mx in _MX_IMAP_MAP:
                provider, host, port = _MX_IMAP_MAP[mx]
                return {'provider': provider, 'imap_host': host, 'imap_port': port, 'detection': 'mx'}
    except Exception:
        pass

    # 3. Common patterns: try imap.<domain>
    common_try = f'imap.{domain}'
    try:
        socket.getaddrinfo(common_try, 993)
        return {'provider': f'{domain} 邮箱', 'imap_host': common_try, 'imap_port': 993, 'detection': 'pattern'}
    except Exception:
        pass

    # 4. Try mail.<domain>
    common_try = f'mail.{domain}'
    try:
        socket.getaddrinfo(common_try, 993)
        return {'provider': f'{domain} 邮箱', 'imap_host': common_try, 'imap_port': 993, 'detection': 'pattern'}
    except Exception:
        pass

    return {'provider': '未知', 'imap_host': '', 'imap_port': 993, 'detection': 'unknown'}


def _get_password(account):
    """Decrypt the stored IMAP password/authorization code.

    New rows store AES-256-GCM encrypted values (hex). Legacy rows may hold
    plaintext — fall back transparently.
    """
    raw = account.password_encrypted or ''
    if not raw:
        return ''
    # "enc:" prefix marks AES-256-GCM encrypted hex
    if raw.startswith('enc:'):
        try:
            from flask import current_app
            from app.services.crypto_utils import decrypt
            return decrypt(raw[4:], current_app.config['SECRET_KEY'])
        except Exception as exc:
            log.warning("Password decrypt failed for account %s, "
                        "trying plaintext fallback: %s", account.id, exc)
    # Fallback: treat as plaintext
    return raw


# ===========================================================================
# IMAP connection
# ===========================================================================

def test_connection(account):
    """Try IMAP connect + login + logout. Returns (ok: bool, message: str)."""
    host = account.imap_host
    port = account.imap_port or 993
    if not host:
        return False, '未配置 IMAP 服务器地址'
    password = _get_password(account)
    if not password:
        return False, '未配置邮箱密码/授权码'
    try:
        conn = imaplib.IMAP4_SSL(host, port, timeout=15)
        try:
            conn.login(account.email_address, password)
        finally:
            try:
                conn.logout()
            except Exception:
                pass
        return True, '连接成功'
    except imaplib.IMAP4.error as exc:
        return False, f'登录失败: {exc}'
    except Exception as exc:
        return False, f'连接失败: {exc}'


def _connect(account):
    """Open an authenticated IMAP connection, selecting the monitor folder."""
    host = account.imap_host
    port = account.imap_port or 993
    try:
        password = _get_password(account)
    except Exception as exc:
        raise RuntimeError(f'获取邮箱密码失败: {exc}')
    if not host or not password:
        raise RuntimeError('邮箱未配置 IMAP 服务器或密码/授权码')

    conn = imaplib.IMAP4_SSL(host, port, timeout=30)
    conn.login(account.email_address, password)

    folder = account.monitor_folder or 'INBOX'
    status, _ = conn.select(f'"{folder}"', readonly=False)
    if status != 'OK':
        # Fallback to INBOX when the custom folder doesn't exist
        log.warning("Folder %s not selectable on %s, falling back to INBOX",
                    folder, account.email_address)
        conn.select('INBOX', readonly=False)
    return conn


# ===========================================================================
# Email parsing helpers
# ===========================================================================

def _decode_str(value):
    """Decode an RFC-2047 encoded header value to str."""
    if not value:
        return ''
    parts = []
    for chunk, charset in decode_header(value):
        if isinstance(chunk, bytes):
            for enc in (charset, 'utf-8', 'gb18030', 'latin-1'):
                if not enc:
                    continue
                try:
                    chunk = chunk.decode(enc)
                    break
                except (UnicodeDecodeError, LookupError):
                    continue
            else:
                chunk = chunk.decode('utf-8', errors='ignore')
        parts.append(chunk)
    return ''.join(parts)


def _iter_attachments(msg):
    """Yield (filename, payload_bytes) for every attachment part."""
    for part in msg.walk():
        disposition = str(part.get('Content-Disposition') or '')
        filename = part.get_filename()
        if not filename and 'attachment' not in disposition.lower():
            continue
        if not filename:
            continue
        filename = _decode_str(filename)
        payload = part.get_payload(decode=True)
        if payload:
            yield filename, payload


def _extract_body(msg):
    """Extract plain-text body (first text/plain part, html stripped fallback)."""
    plain, html = '', ''
    for part in msg.walk():
        ctype = part.get_content_type()
        disposition = str(part.get('Content-Disposition') or '')
        if 'attachment' in disposition.lower():
            continue
        if ctype == 'text/plain' and not plain:
            payload = part.get_payload(decode=True)
            if payload:
                charset = part.get_content_charset() or 'utf-8'
                try:
                    plain = payload.decode(charset, errors='ignore')
                except LookupError:
                    plain = payload.decode('utf-8', errors='ignore')
        elif ctype == 'text/html' and not html:
            payload = part.get_payload(decode=True)
            if payload:
                charset = part.get_content_charset() or 'utf-8'
                try:
                    html = payload.decode(charset, errors='ignore')
                except LookupError:
                    html = payload.decode('utf-8', errors='ignore')
    if plain.strip():
        return plain
    if html.strip():
        from app.services.resume_service import _strip_html
        return _strip_html(html)
    return ''


# ===========================================================================
# Main sync entry
# ===========================================================================

def sync_mail_account(account_id):
    """Sync one mail account: pull unread emails, ingest resumes.

    Returns summary dict: {status, account_id, email, new_emails,
                           resumes_ingested, candidates, details}
    """
    from app.extensions import db
    from app.models.auxiliary import RecruitMailAccount
    from app.services import resume_service

    account = RecruitMailAccount.active().filter_by(id=account_id).first()
    if not account:
        return {'status': 'error', 'error': 'Account not found', 'account_id': account_id}
    if account.status != 1:
        return {'status': 'error', 'error': 'Account not active', 'account_id': account_id}

    acct_name = account.email_address or account.account_name or f'account#{account.id}'
    result = {
        'status': 'ok', 'account_id': account.id, 'email': acct_name,
        'new_emails': 0, 'resumes_ingested': 0, 'candidates': [], 'details': [],
    }

    conn = None
    try:
        conn = _connect(account)
        status, data = conn.search(None, 'UNSEEN')
        if status != 'OK':
            raise RuntimeError(f'IMAP search failed: {status}')

        msg_ids = data[0].split()[-_MAX_EMAILS_PER_SYNC:]
        result['new_emails'] = len(msg_ids)
        log.info("Mailbox %s: %d unread email(s) to process", acct_name, len(msg_ids))

        for num in msg_ids:
            detail = _process_one_message(conn, num, account, resume_service)
            if detail:
                result['details'].append(detail)
                if detail.get('ingested'):
                    result['resumes_ingested'] += 1
                    result['candidates'].append(detail.get('candidate'))

        account.last_sync_time = datetime.now()
        db.session.commit()

    except Exception as exc:
        log.error("Mailbox sync failed for %s: %s", acct_name, exc, exc_info=True)
        db.session.rollback()
        result['status'] = 'error'
        result['error'] = str(exc)
    finally:
        if conn is not None:
            try:
                conn.close()
            except Exception:
                pass
            try:
                conn.logout()
            except Exception:
                pass

    _audit_sync(account, result)
    return result


def _match_demand_for_position(position):
    """按岗位名在已通过需求中模糊匹配（position_name / jd_content）。

    Returns RecruitDemand or None.
    """
    if not position:
        return None
    try:
        from app.models.demand import RecruitDemand
        from sqlalchemy import or_

        q = RecruitDemand.query.filter(
            RecruitDemand.is_deleted == 0,
            RecruitDemand.demand_status == 2,  # 已通过
        )
        like = f'%{position}%'
        rows = q.filter(or_(
            RecruitDemand.position_name.like(like),
            RecruitDemand.jd_content.like(like),
        )).all()
        if rows:
            return rows[0]

        # 反向匹配：岗位名包含需求 position_name（"Java开发工程师" ⊇ "Java开发"）
        for d in q.filter(RecruitDemand.position_name.isnot(None)).all():
            pn = (d.position_name or '').strip()
            if pn and len(pn) >= 2 and pn in position:
                return d
    except Exception as exc:
        log.warning("demand match for position '%s' failed: %s", position, exc)
    return None


def _link_to_demand(demand, candidate_name):
    """复用 link_candidate_to_demand 逻辑建 RecruitProcess + ResumeMatch。"""
    try:
        from app.services.demand_service import link_candidate_to_demand
        return link_candidate_to_demand(demand.demand_no, candidate_name)
    except Exception as exc:
        log.warning("auto-link to demand %s failed: %s", demand.demand_no, exc)
        return {'linked': False, 'reason': str(exc)}


def _mark_seen(conn, num):
    try:
        conn.store(num, '+FLAGS', '\\Seen')
    except Exception as exc:
        log.warning("mark \\Seen failed for msg %s: %s", num, exc)


def _process_one_message(conn, num, account, resume_service):
    """Fetch and process a single email. Returns a detail dict or None.

    无论成败都写 detail；处理完毕的邮件一律标记 \\Seen，避免下轮重拉死循环。
    """
    from app.extensions import db

    status, msg_data = conn.fetch(num, '(RFC822)')
    if status != 'OK' or not msg_data or not msg_data[0]:
        return None

    msg = message_from_bytes(msg_data[0][1])
    subject = _decode_str(msg.get('Subject'))
    sender = parseaddr(_decode_str(msg.get('From')))[1]

    detail = {'subject': subject[:80], 'from': sender, 'ingested': False}

    # 1. 从主题解析应聘岗位 + 匹配已通过需求（提取失败留空，不阻塞入库）
    target_position = resume_service.extract_target_position(subject)
    demand = _match_demand_for_position(target_position)
    if target_position:
        detail['target_position'] = target_position
    if demand:
        detail['target_demand'] = demand.demand_no

    attachments = [
        (fn, payload) for fn, payload in _iter_attachments(msg)
        if resume_service.is_resume_attachment(fn)
    ]

    try:
        if attachments:
            for fn, payload in attachments:
                try:
                    r = resume_service.ingest_resume(
                        payload, fn,
                        source_channel='邮箱',
                        mail_account_id=account.id,
                        mail_subject=subject,
                        target_position=target_position,
                        target_demand_id=demand.id if demand else None,
                    )
                    detail.update(
                        ingested=True, file=fn,
                        candidate=r['candidate_name'],
                        candidate_no=r['candidate_no'],
                        engine=r['parse_engine'],
                    )
                    # 命中需求 → 自动建 RecruitProcess + ResumeMatch
                    if demand:
                        link = _link_to_demand(demand, r['candidate_name'])
                        detail['linked'] = bool(link.get('linked'))
                        if link.get('matchScore') is not None:
                            detail['match_score'] = link['matchScore']
                    _mark_seen(conn, num)
                    break  # one resume per email is enough
                except ValueError as exc:
                    # 附件类型不支持 / 无法提取文本：标记已读，不再重拉
                    log.warning("Attachment %s skipped: %s", fn, exc)
                    detail['note'] = str(exc)
                    _mark_seen(conn, num)
                except Exception as exc:
                    # 其他入库失败：记录并标记已读，避免每轮死循环重拉
                    log.error("Attachment %s ingest failed: %s", fn, exc, exc_info=True)
                    db.session.rollback()
                    detail['note'] = f'入库失败: {exc}'
                    _mark_seen(conn, num)
        else:
            # No resume attachment — check whether the body itself is a resume
            body = _extract_body(msg)
            if body and resume_service.looks_like_resume(subject, body) and len(body) > 150:
                try:
                    r = resume_service.ingest_resume(
                        body.encode('utf-8'),
                        f'{subject[:40] or "email-resume"}.txt',
                        source_channel='邮箱',
                        mail_account_id=account.id,
                        raw_text=body,
                        mail_subject=subject,
                        target_position=target_position,
                        target_demand_id=demand.id if demand else None,
                    )
                    detail.update(
                        ingested=True, file='(邮件正文)',
                        candidate=r['candidate_name'],
                        candidate_no=r['candidate_no'],
                        engine=r['parse_engine'],
                    )
                    if demand:
                        link = _link_to_demand(demand, r['candidate_name'])
                        detail['linked'] = bool(link.get('linked'))
                        if link.get('matchScore') is not None:
                            detail['match_score'] = link['matchScore']
                    _mark_seen(conn, num)
                except ValueError as exc:
                    log.warning("Body not ingestable (%s): %s", subject[:40], exc)
                    detail['note'] = f'正文无法解析: {exc}'
                    _mark_seen(conn, num)
                except Exception as exc:
                    log.error("Body ingest failed (%s): %s", subject[:40], exc, exc_info=True)
                    db.session.rollback()
                    detail['note'] = f'正文解析失败: {exc}'
                    _mark_seen(conn, num)
            else:
                detail['note'] = '非简历邮件，跳过'
                _mark_seen(conn, num)
    except Exception as exc:
        log.error("Message processing failed: %s", exc, exc_info=True)
        detail['note'] = str(exc)
        _mark_seen(conn, num)

    return detail


def _is_due(account, now=None):
    """Whether an account is due for sync based on its sync_freq (minutes)."""
    if account.last_sync_time is None:
        return True
    now = now or datetime.now()
    freq_min = account.sync_freq or 30
    elapsed = (now - account.last_sync_time).total_seconds() / 60
    return elapsed >= freq_min


def sync_all_accounts(respect_freq=False):
    """Sync every active mail account. Returns aggregate summary.

    Args:
        respect_freq: when True (periodic Celery beat), skip accounts whose
            per-account sync_freq has not elapsed since last_sync_time.
            Manual refresh from the API passes False and always syncs.
    """
    from app.models.auxiliary import RecruitMailAccount

    accounts = RecruitMailAccount.active().filter(
        RecruitMailAccount.status == 1,
    ).all()

    if respect_freq:
        due_accounts = [a for a in accounts if _is_due(a)]
        skipped = len(accounts) - len(due_accounts)
        if skipped:
            log.info("Email sync tick: %d account(s) skipped (sync_freq not reached)", skipped)
        accounts = due_accounts

    summary = {
        'status': 'ok', 'accounts_checked': len(accounts),
        'new_emails': 0, 'resumes_ingested': 0, 'details': [],
    }
    for acct in accounts:
        r = sync_mail_account(acct.id)
        summary['new_emails'] += r.get('new_emails', 0)
        summary['resumes_ingested'] += r.get('resumes_ingested', 0)
        summary['details'].append(r)
        if r.get('status') == 'error':
            summary['status'] = 'partial'
    if summary['accounts_checked'] == 0:
        summary['status'] = 'ok'
    return summary


def _audit_sync(account, result):
    """Write an audit log entry for the sync run (best-effort)."""
    try:
        from app.services.config_service import append_audit_log
        if result.get('status') == 'error':
            detail = f"{result.get('email')} 同步失败: {result.get('error', '')[:100]}"
        else:
            detail = (f"{result.get('email')} 拉取 {result.get('new_emails', 0)} 封邮件，"
                      f"识别入库 {result.get('resumes_ingested', 0)} 封简历")
        append_audit_log('系统', '邮件', '邮箱同步', detail)
    except Exception as exc:
        log.warning("Audit log for mail sync failed: %s", exc)
