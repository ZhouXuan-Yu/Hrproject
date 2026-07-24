"""Candidate confirmation service — tokenized accept/reject links + emails.

闭环链路：
  安排面试 → 邮件发送面试邀请（含确认链接）→ 候选人 H5 点击接受/拒绝 → 数据回流
  发放 Offer → 邮件发送 Offer（含确认链接）→ 候选人接受 → 自动入职单 + 入职包邮件

Token 为 JWT（purpose=candidate-confirm），无需候选人登录。
"""
import logging
import os
import html as html_utils
import re
from datetime import datetime, timedelta

import jwt

log = logging.getLogger(__name__)

_TOKEN_PURPOSE = 'candidate-confirm'
_INTERVIEW_TTL_HOURS = 96   # 面试确认链接有效期
_OFFER_TTL_HOURS = 7 * 24   # Offer 确认链接有效期


# ===========================================================================
# Token
# ===========================================================================

def generate_confirm_token(kind, ref, ttl_hours=None):
    """Generate a signed confirm token. kind: 'interview' | 'offer'."""
    from flask import current_app
    ttl = ttl_hours or (_INTERVIEW_TTL_HOURS if kind == 'interview' else _OFFER_TTL_HOURS)
    payload = {
        'purpose': _TOKEN_PURPOSE,
        'kind': kind,
        'ref': str(ref),
        'exp': datetime.utcnow() + timedelta(hours=ttl),
        'iat': datetime.utcnow(),
    }
    return jwt.encode(payload, current_app.config['JWT_SECRET_KEY'], algorithm='HS256')


def verify_confirm_token(token):
    """Verify token and return payload. Raises AppError on failure."""
    from flask import current_app
    from app.utils.response import AppError
    try:
        payload = jwt.decode(token, current_app.config['JWT_SECRET_KEY'],
                             algorithms=['HS256'])
    except jwt.ExpiredSignatureError:
        raise AppError('TOKEN_EXPIRED', '确认链接已过期，请联系 HR 重新发送')
    except jwt.InvalidTokenError:
        raise AppError('TOKEN_INVALID', '确认链接无效')
    if payload.get('purpose') != _TOKEN_PURPOSE:
        raise AppError('TOKEN_INVALID', '确认链接无效')
    return payload


def build_confirm_url(token):
    base = os.environ.get('PUBLIC_BASE_URL', 'http://127.0.0.1:5000').rstrip('/')
    return f'{base}/confirm/{token}'


def _public_url_notice():
    base = os.environ.get('PUBLIC_BASE_URL', 'http://127.0.0.1:5000')
    if '127.0.0.1' in base or 'localhost' in base:
        return '<p style="color:#b45309;font-size:12px">当前系统未配置公网地址，此确认链接仅可在部署服务器本机或内网测试访问。</p>'
    return ''


# ===========================================================================
# Data helpers
# ===========================================================================

def _candidate_contact(resume_id):
    """Resolve (candidate_name, email, candidate) from a resume id."""
    from app.models.candidate import Candidate, Resume
    resume = Resume.query.filter_by(id=resume_id, is_deleted=0).first()
    if not resume:
        return None, None, None
    cand = Candidate.query.filter_by(id=resume.candidate_id, is_deleted=0).first()
    if not cand:
        return None, None, None
    return cand.candidate_name, cand.email, cand


def _sender_account_id(resume_id):
    """发件邮箱 = 招聘基础配置中采集该简历的邮箱（Resume.mail_account_id）。

    约面邀请 / Offer / 入职包邮件统一从"接收该简历的邮箱"发出。
    查不到时返回 None，由 mail_sender 回退到第一个启用的邮箱账号，
    保证发件方始终是招聘配置里的邮箱而不是其他来源。
    """
    try:
        from app.models.candidate import Resume
        r = Resume.query.filter_by(id=resume_id, is_deleted=0).first()
        if r and r.mail_account_id:
            return r.mail_account_id
    except Exception:
        pass
    return None


def _position_label(demand_id):
    from app.models.demand import RecruitDemand
    d = RecruitDemand.query.filter_by(id=demand_id, is_deleted=0).first()
    if not d:
        return '招聘岗位'
    return f'岗位 {d.demand_no}' + (f'（{d.work_city}）' if d.work_city else '')


def _template_matches(template, kind):
    text = ' '.join([
        str(getattr(template, 'template_type', '') or ''),
        str(getattr(template, 'template_name', '') or ''),
        str(getattr(template, 'subject', '') or ''),
    ]).lower()
    keywords = {
        'interview': ('interview', 'invite', '面试', '邀请'),
        'offer': ('offer', '录用', '入职'),
        'reject': ('reject', 'fail', '未通过', '不通过', '婉拒', '淘汰', '结果'),
        'remind': ('remind', '提醒'),
    }
    return any(key in text for key in keywords.get(kind, (kind,)))


def _render_text(template_text, context):
    rendered = template_text or ''
    aliases = {
        'candidate': ('candidate', 'name', '候选人', '候选人姓名'),
        'company': ('company', '公司'),
        'position': ('position', '岗位', '应聘岗位'),
        'time': ('time', '时间', '面试时间'),
        'method': ('method', '方式', '面试方式'),
        'round': ('round', '轮次', '面试轮次'),
        'hr': ('hr', 'HR', 'hr'),
        'confirm_url': ('confirm_url', 'url', '链接', '确认链接'),
        'comment': ('comment', '评价', '原因'),
    }
    for key, names in aliases.items():
        value = str(context.get(key, '') or '')
        for name in names:
            rendered = rendered.replace('{{' + name + '}}', value)
            rendered = rendered.replace('{' + name + '}', value)
    return rendered


def _plain_text_to_html(text):
    escaped = html_utils.escape(text or '')
    return '<br>'.join(escaped.splitlines())


def _render_notify_template(kind, default_subject, default_html, context):
    """Render DB notification template when configured, otherwise use default."""
    try:
        from app.models.auxiliary import NotifyTemplate
        templates = NotifyTemplate.active().filter(NotifyTemplate.status == 1).order_by(
            NotifyTemplate.updated_at.desc()
        ).all()
        template = next((t for t in templates if _template_matches(t, kind)), None)
        if not template:
            return default_subject, default_html

        subject = _render_text(template.subject or default_subject, context)
        body = _render_text(template.body or '', context)
        if not body:
            return subject, default_html
        if not re.search(r'<[a-zA-Z][\s\S]*?>', body):
            body = _plain_text_to_html(body)
        confirm_url = context.get('confirm_url')
        if kind in ('interview', 'offer') and confirm_url and str(confirm_url) not in body:
            label = '确认面试安排' if kind == 'interview' else '查看并确认 Offer'
            safe_url = html_utils.escape(str(confirm_url), quote=True)
            body += f"""
            <p style="text-align:center;margin:24px 0">
              <a href="{safe_url}" style="background:#4F6EF7;color:#fff;padding:12px 32px;border-radius:8px;text-decoration:none;font-weight:bold">{label}</a>
            </p>"""
        html = f"""
        <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#333;line-height:1.7">
          {body}
        </div>"""
        return subject, html
    except Exception as exc:
        log.warning("Render notify template failed: %s", exc)
        return default_subject, default_html


def _mail_position_label(demand_id):
    try:
        from app.models.demand import RecruitDemand
        d = RecruitDemand.query.filter_by(id=demand_id, is_deleted=0).first()
        if d and getattr(d, 'position_name', None):
            city = f'（{d.work_city}）' if getattr(d, 'work_city', None) else ''
            return f'{d.position_name}{city}'
    except Exception:
        pass
    return _position_label(demand_id)


# ===========================================================================
# Confirm page data
# ===========================================================================

def get_confirm_page_data(payload):
    """Build the view-model for the H5 confirm page."""
    from app.utils.response import AppError

    kind = payload['kind']
    ref = payload['ref']

    if kind == 'interview':
        from app.models.interview import InterviewBook
        book = InterviewBook.query.filter_by(id=int(ref), is_deleted=0).first()
        if not book:
            raise AppError('NOT_FOUND', '面试预约不存在')
        name, email, _ = _candidate_contact(book.resume_id)
        invite = book.invite_json or {}
        type_labels = {1: '飞书视频', 2: '腾讯会议', 3: '线上视频', 4: '线下面试'}
        return {
            'kind': 'interview',
            'title': '面试邀请确认',
            'candidateName': name or '候选人',
            'position': _position_label(book.demand_id),
            'time': book.book_time.strftime('%Y年%m月%d日 %H:%M') if book.book_time else '待协商',
            'method': type_labels.get(book.interview_type, '线上面试'),
            'meetingUrl': book.meeting_url or '',
            'address': book.address or '',
            'round': f'第{book.interview_round or 1}轮面试',
            'already': invite.get('candidate_confirm'),
            'deadline': (datetime.utcfromtimestamp(payload['exp'])).strftime('%Y-%m-%d %H:%M'),
        }

    if kind == 'offer':
        from app.models.hire import Offer
        offer = Offer.query.filter_by(offer_no=ref, is_deleted=0).first()
        if not offer:
            raise AppError('NOT_FOUND', 'Offer 不存在')
        name, email, _ = _candidate_contact(offer.resume_id)
        salary = offer.salary_json or {}
        salary_text = salary.get('text') or salary.get('base') or '详见 Offer 正文'
        status_labels = {2: 'accepted', 3: 'rejected'}
        return {
            'kind': 'offer',
            'title': '录用 Offer 确认',
            'candidateName': name or '候选人',
            'position': _position_label(offer.demand_id),
            'salary': str(salary_text),
            'offerContent': offer.offer_content or '',
            'deadline': offer.valid_deadline.strftime('%Y-%m-%d %H:%M') if offer.valid_deadline else '—',
            'already': status_labels.get(offer.offer_status),
        }

    raise AppError('TOKEN_INVALID', '未知的确认类型')


# ===========================================================================
# Apply candidate action
# ===========================================================================

def apply_confirm_action(payload, action, reason=''):
    """Apply candidate's accept/reject. Returns result dict."""
    from app.utils.response import AppError
    from app.extensions import db

    if action not in ('accept', 'reject'):
        raise AppError('BAD_REQUEST', '无效操作')

    kind = payload['kind']
    ref = payload['ref']

    # ---- 面试确认 ----
    if kind == 'interview':
        from app.models.interview import InterviewBook
        book = InterviewBook.query.filter_by(id=int(ref), is_deleted=0).first()
        if not book:
            raise AppError('NOT_FOUND', '面试预约不存在')

        invite = dict(book.invite_json or {})
        if invite.get('candidate_confirm'):
            return {'ok': True, 'already': True,
                    'message': '您已确认过本场面试，无需重复操作'}

        invite['candidate_confirm'] = action
        invite['candidate_confirm_at'] = datetime.now().isoformat(timespec='seconds')
        if reason:
            invite['candidate_reject_reason'] = reason
        book.invite_json = invite
        db.session.commit()

        log.info("Interview %s candidate %s", ref, action)
        return {'ok': True, 'already': False,
                'message': '已确认参加面试，请准时出席' if action == 'accept'
                           else '已反馈无法参加，HR 将与您重新协商时间'}

    # ---- Offer 确认 ----
    if kind == 'offer':
        from app.services import hire_service
        from app.models.hire import Offer
        offer = Offer.query.filter_by(offer_no=ref, is_deleted=0).first()
        if not offer:
            raise AppError('NOT_FOUND', 'Offer 不存在')
        if offer.offer_status == 2:
            return {'ok': True, 'already': True, 'message': '您已接受该 Offer'}
        if offer.offer_status == 3:
            return {'ok': True, 'already': True, 'message': '该 Offer 已标记为拒绝'}

        if action == 'accept':
            hire_service.accept_offer(ref)
            entry_pack = _generate_entry_pack(offer)
            _send_entry_pack_email(offer, entry_pack)
            return {'ok': True, 'already': False,
                    'message': '已接受 Offer！入职材料清单已发送至您的邮箱，HR 将与您确认入职时间'}
        else:
            hire_service.reject_offer(ref, reason=reason or '候选人通过确认链接拒绝')
            return {'ok': True, 'already': False,
                    'message': '已反馈拒绝该 Offer，感谢您的参与'}

    raise AppError('TOKEN_INVALID', '未知的确认类型')


# ===========================================================================
# Outbound emails
# ===========================================================================

def send_interview_invite_email(book):
    """Send interview invite email with confirm link. Returns (ok, msg)."""
    from app.services.mail_sender import send_mail

    name, email, _ = _candidate_contact(book.resume_id)
    if not email:
        return False, '候选人无邮箱，跳过邮件邀请'

    token = generate_confirm_token('interview', book.id)
    url = build_confirm_url(token)
    type_labels = {1: '飞书视频', 2: '腾讯会议', 3: '线上视频', 4: '线下面试'}
    time_str = book.book_time.strftime('%Y年%m月%d日 %H:%M') if book.book_time else '待协商'
    method = type_labels.get(book.interview_type, '线上面试')
    position = _mail_position_label(book.demand_id)

    html = f"""
    <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#333">
      <h2 style="color:#4F6EF7">面试邀请</h2>
      <p>{name or '候选人'} 您好：</p>
      <p>诚邀您参加 <b>{position}</b> 的面试，安排如下：</p>
      <table style="border-collapse:collapse;margin:12px 0">
        <tr><td style="padding:6px 16px 6px 0;color:#888">时间</td><td><b>{time_str}</b></td></tr>
        <tr><td style="padding:6px 16px 6px 0;color:#888">方式</td><td>{method}</td></tr>
        {f'<tr><td style="padding:6px 16px 6px 0;color:#888">会议链接</td><td><a href="{book.meeting_url}">{book.meeting_url}</a></td></tr>' if book.meeting_url else ''}
        {f'<tr><td style="padding:6px 16px 6px 0;color:#888">地点</td><td>{book.address}</td></tr>' if book.address else ''}
      </table>
      <p>请在 <b>{(datetime.now() + timedelta(hours=_INTERVIEW_TTL_HOURS)).strftime('%m月%d日 %H:%M')}</b> 前点击下面按钮确认是否参加：</p>
      <p style="text-align:center;margin:24px 0">
        <a href="{url}" style="background:#4F6EF7;color:#fff;padding:12px 32px;border-radius:8px;text-decoration:none;font-weight:bold">确认面试安排</a>
      </p>
      <p style="color:#999;font-size:12px">如按钮无法点击，请复制链接到浏览器打开：{url}</p>
      {_public_url_notice()}
    </div>"""

    default_subject = f'【面试邀请】{position} - {time_str}'
    context = {
        'candidate': name or '候选人',
        'company': os.environ.get('COMPANY_NAME', 'XX公司'),
        'position': position,
        'time': time_str,
        'method': method,
        'round': f'第{book.interview_round or 1}轮面试',
        'confirm_url': url,
        'hr': os.environ.get('HR_DISPLAY_NAME', 'HR'),
    }
    subject, html = _render_notify_template('interview', default_subject, html, context)

    ok, msg = send_mail(email, subject, html,
                        account_id=_sender_account_id(book.resume_id), mail_type='invite')
    _record_invite_sent(book, ok, msg, email)
    return ok, msg


def send_offer_email(offer):
    """Send offer letter email with confirm link. Returns (ok, msg)."""
    from app.services.mail_sender import send_mail

    name, email, _ = _candidate_contact(offer.resume_id)
    if not email:
        return False, '候选人无邮箱，跳过 Offer 邮件'

    token = generate_confirm_token('offer', offer.offer_no)
    url = build_confirm_url(token)
    position = _mail_position_label(offer.demand_id)
    deadline = offer.valid_deadline.strftime('%Y年%m月%d日') if offer.valid_deadline else '—'
    content_html = (offer.offer_content or '').replace('\n', '<br>')

    html = f"""
    <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#333">
      <h2 style="color:#4F6EF7">录用通知书</h2>
      <p>{name or '候选人'} 您好：</p>
      <p>恭喜您通过面试！我们正式向您发出 <b>{position}</b> 的录用邀请：</p>
      <div style="background:#f6f8ff;border-radius:8px;padding:16px;margin:12px 0">{content_html}</div>
      <p>请在 <b>{deadline}</b> 前点击下面按钮确认是否接受：</p>
      <p style="text-align:center;margin:24px 0">
        <a href="{url}" style="background:#22a06b;color:#fff;padding:12px 32px;border-radius:8px;text-decoration:none;font-weight:bold">查看并确认 Offer</a>
      </p>
      <p style="color:#999;font-size:12px">如按钮无法点击，请复制链接到浏览器打开：{url}</p>
      {_public_url_notice()}
    </div>"""

    default_subject = f'【录用通知】{position} Offer'
    context = {
        'candidate': name or '候选人',
        'company': os.environ.get('COMPANY_NAME', 'XX公司'),
        'position': position,
        'time': deadline,
        'method': '邮件',
        'confirm_url': url,
        'hr': os.environ.get('HR_DISPLAY_NAME', 'HR'),
    }
    subject, html = _render_notify_template('offer', default_subject, html, context)

    return send_mail(email, subject, html,
                     account_id=_sender_account_id(offer.resume_id), mail_type='offer')


def send_interview_reject_email(book, comment=''):
    """Send interview rejection email after a failed evaluation."""
    from app.services.mail_sender import send_mail

    name, email, _ = _candidate_contact(book.resume_id)
    if not email:
        return False, '候选人无邮箱，跳过面试结果通知'

    position = _mail_position_label(book.demand_id)
    html = f"""
    <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#333;line-height:1.7">
      <h2 style="color:#4F6EF7">面试结果通知</h2>
      <p>{name or '候选人'} 您好：</p>
      <p>感谢您参加 <b>{position}</b> 的面试。很遗憾，本次面试暂未通过。</p>
      <p>感谢您对我们的关注与投入，后续如有更匹配的机会，我们也会再次联系您。</p>
    </div>"""
    default_subject = f'【面试结果通知】{position}'
    context = {
        'candidate': name or '候选人',
        'company': os.environ.get('COMPANY_NAME', 'XX公司'),
        'position': position,
        'time': book.book_time.strftime('%Y-%m-%d %H:%M') if book.book_time else '',
        'method': '邮件',
        'round': f'第{book.interview_round or 1}轮面试',
        'comment': comment,
        'hr': os.environ.get('HR_DISPLAY_NAME', 'HR'),
    }
    subject, html = _render_notify_template('reject', default_subject, html, context)
    return send_mail(email, subject, html,
                     account_id=_sender_account_id(book.resume_id), mail_type='reject')


def send_offer_reminder_email(offer, days_left, deadline):
    """Offer 倒计时提醒邮件（每天一次，告知剩余确认天数）。

    Returns (ok, msg, to_addr)。复用与 Offer 邮件相同的发件账号规则。
    """
    from app.services.mail_sender import send_mail

    name, email, _ = _candidate_contact(offer.resume_id)
    if not email:
        return False, '候选人无邮箱，跳过倒计时提醒', None

    token = generate_confirm_token('offer', offer.offer_no)
    url = build_confirm_url(token)
    position = _position_label(offer.demand_id)
    deadline_str = deadline.strftime('%Y年%m月%d日 %H:%M') if deadline else '—'

    html = f"""
    <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#333">
      <h2 style="color:#d97706">Offer 确认倒计时提醒</h2>
      <p>{name or '候选人'} 您好：</p>
      <p>您收到的 <b>{position}</b> 录用 Offer 还未确认，
         距离截止还剩 <b style="color:#d97706">{days_left} 天</b>（{deadline_str}）。</p>
      <p>逾期未确认将视为放弃本次录用机会，请尽快点击下面按钮查看并确认：</p>
      <p style="text-align:center;margin:24px 0">
        <a href="{url}" style="background:#d97706;color:#fff;padding:12px 32px;border-radius:8px;text-decoration:none;font-weight:bold">查看并确认 Offer</a>
      </p>
      <p style="color:#999;font-size:12px">如按钮无法点击，请复制链接到浏览器打开：{url}</p>
    </div>"""

    ok, msg = send_mail(email, f'【倒计时提醒】{position} Offer 确认还剩 {days_left} 天', html,
                        account_id=_sender_account_id(offer.resume_id), mail_type='offer')
    return ok, msg, email


# ===========================================================================
# Entry pack（入职包）
# ===========================================================================

def _generate_entry_pack(offer):
    """生成入职材料清单，写入 Entry.checklist_json。返回清单 dict。"""
    from app.extensions import db
    from app.models.hire import Entry

    name, _, _ = _candidate_contact(offer.resume_id)
    position = _position_label(offer.demand_id)
    pack = {
        'candidate': name,
        'position': position,
        'generated_at': datetime.now().isoformat(timespec='seconds'),
        'items': [
            {'name': '身份证原件及复印件', 'required': True},
            {'name': '学历证书、学位证书原件及复印件', 'required': True},
            {'name': '与原单位解除劳动关系证明（离职证明）', 'required': True},
            {'name': '一寸免冠照片 2 张', 'required': True},
            {'name': '银行卡复印件（工资卡）', 'required': True},
            {'name': '体检报告（三个月内有效）', 'required': False},
            {'name': '社保/公积金转移接续材料', 'required': False},
        ],
        'notes': '请按清单准备入职材料，具体入职时间由 HR 与您电话确认。',
    }

    try:
        entry = (Entry.query.filter_by(resume_id=offer.resume_id, is_deleted=0)
                 .order_by(Entry.id.desc()).first())
        if entry:
            entry.checklist_json = pack
            db.session.commit()
    except Exception as exc:
        log.warning("Entry pack persist failed: %s", exc)
    return pack


def _send_entry_pack_email(offer, pack):
    """候选人接受 Offer 后发送入职包邮件。best-effort。"""
    from app.services.mail_sender import send_mail

    _, email, _ = _candidate_contact(offer.resume_id)
    if not email:
        return False, '候选人无邮箱'

    items_html = ''.join(
        f'<li>{"<b>[必带]</b> " if i["required"] else "[建议] "}{i["name"]}</li>'
        for i in pack['items']
    )
    html = f"""
    <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#333">
      <h2 style="color:#22a06b">入职材料清单</h2>
      <p>{pack.get('candidate') or '候选人'} 您好，欢迎加入！</p>
      <p>您即将入职 <b>{pack.get('position', '')}</b>，请提前准备以下材料：</p>
      <ul style="line-height:2">{items_html}</ul>
      <p>{pack.get('notes', '')}</p>
    </div>"""
    return send_mail(email, f'【入职指引】{pack.get("position", "")} 入职材料清单', html,
                     account_id=_sender_account_id(offer.resume_id), mail_type='entry')


def _record_invite_sent(book, ok, msg, email):
    """把邀请邮件发送结果写入 invite_json。best-effort。"""
    try:
        from app.extensions import db
        invite = dict(book.invite_json or {})
        invite.setdefault('email_log', []).append({
            'to': email, 'ok': ok, 'msg': msg,
            'at': datetime.now().isoformat(timespec='seconds'),
        })
        book.invite_json = invite
        db.session.commit()
    except Exception as exc:
        log.warning("invite email log persist failed: %s", exc)
