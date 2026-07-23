"""Talent API: /api/talent/*"""
from flask import Blueprint, request, g
from app.utils.response import success, success_list, error, AppError

bp = Blueprint('talent', __name__)


@bp.route('/list')
def get_list():
    """GET /api/talent/list — paginated talent pool."""
    from app.services.talent_service import list_talent
    data, total = list_talent(request.args)
    page = int(request.args.get('page', 1))
    page_size = int(request.args.get('pageSize', 20))
    return success_list(data, total, page, page_size)


@bp.route('/<candidate_id>/note', methods=['PATCH'])
def update_note(candidate_id):
    """PATCH /api/talent/{id}/note — update candidate note."""
    from app.services.talent_service import update_note
    result = update_note(candidate_id, request.get_json(silent=True) or {})
    return success(result)


@bp.route('/match')
def get_match():
    """GET /api/talent/match — internal employee match results."""
    from app.services.talent_service import get_match_results
    result = get_match_results(request.args.get('demandId', ''))
    return success(result)


@bp.route('/match', methods=['POST'])
def create_match():
    """POST /api/talent/match — calculate match result for a candidate against a demand."""
    from app.services.match_service import get_match_result
    body = request.get_json(silent=True) or {}
    candidate_id = body.get('candidateId', '')
    demand_id = body.get('demandId', '')
    if not candidate_id or not demand_id:
        raise AppError('BAD_REQUEST', '缺少 candidateId 或 demandId 参数')
    result = get_match_result(demand_id, candidate_id)
    return success(result)


@bp.route('/candidate/<candidate_id>')
def get_candidate(candidate_id):
    """GET /api/talent/candidate/{id} — single candidate detail."""
    from app.services.talent_service import get_candidate_detail
    data = get_candidate_detail(candidate_id)
    return success(data)


@bp.route('/employee/<employee_id>')
def get_employee(employee_id):
    """GET /api/talent/employee/{id} — single employee detail."""
    from app.services.talent_service import get_employee_detail
    data = get_employee_detail(employee_id)
    return success(data)


@bp.route('/link', methods=['POST'])
def link_to_demand():
    """POST /api/talent/link — link candidates to demand."""
    from app.services.demand_service import link_candidate_to_demand
    body = request.get_json(silent=True) or {}
    demand_id = body.get('demandId') or ''
    names = body.get('names') or []
    if not demand_id or not names:
        raise AppError('BAD_REQUEST', '缺少 demandId 或 names 参数')
    results = []
    for name in names:
        r = link_candidate_to_demand(demand_id, name)
        results.append({'name': name, **r})
    return success({'linked': len(results), 'total': len(names), 'candidates': results})


@bp.route('/resume-file/<resume_id>')
def download_resume_file(resume_id):
    """GET /api/talent/resume-file/{resume_id} — 下载/预览简历原件。"""
    import os
    from flask import send_file, current_app
    from app.models.candidate import Resume
    from app.models.infrastructure import File

    resume = Resume.query.filter_by(id=resume_id, is_deleted=0).first()
    if not resume or not resume.resume_file_id:
        raise AppError('NOT_FOUND', '简历原件不存在')

    f = File.query.filter_by(id=resume.resume_file_id, is_deleted=0).first()
    if not f or not f.file_url or not os.path.exists(f.file_url):
        raise AppError('NOT_FOUND', '简历文件已被移除')

    # 防目录穿越：文件必须位于 uploads 目录内
    uploads_root = os.path.join(os.path.dirname(current_app.root_path), 'uploads')
    real_path = os.path.realpath(f.file_url)
    if not real_path.startswith(os.path.realpath(uploads_root)):
        raise AppError('FORBIDDEN', '非法文件路径')

    return send_file(real_path, as_attachment=False,
                     download_name=f.file_name or 'resume')


@bp.route('/upload-resume', methods=['POST'])
def upload_resume():
    """POST /api/talent/upload-resume — 手动上传简历（multipart）。

    文件经文本提取（pdfplumber/python-docx）→ DeepSeek 结构化解析 → 去重入库。
    """
    from app.services.resume_service import ingest_resume, SUPPORTED_EXTENSIONS
    import os

    file = request.files.get('file')
    if not file or not file.filename:
        raise AppError('BAD_REQUEST', '请选择要上传的简历文件')

    ext = os.path.splitext(file.filename)[1].lower()
    if ext not in SUPPORTED_EXTENSIONS and ext != '.doc':
        raise AppError('BAD_REQUEST',
                       f'不支持的文件格式 {ext}，支持 PDF/DOCX/TXT')

    file_bytes = file.read()
    if len(file_bytes) > 20 * 1024 * 1024:
        raise AppError('BAD_REQUEST', '文件大小不能超过 20MB')

    try:
        result = ingest_resume(file_bytes, file.filename, source_channel='手动上传')
    except ValueError as exc:
        raise AppError('BAD_REQUEST', str(exc))

    return success(result)


@bp.route('/ingest-log')
def get_ingest_log():
    """GET /api/talent/ingest-log — recent resume ingestion records for pipeline view."""
    from app.services.talent_service import get_ingest_log as _log
    limit = request.args.get('limit', 10)
    return success({'items': _log(limit)})


_TYPE_LABELS = {'invite': '面试邀请', 'offer': '录用通知', 'entry': '入职指引',
                'test': '测试邮件', 'other': '系统邮件'}


@bp.route('/mail-log')
def get_mail_log():
    """GET /api/talent/mail-log — 系统外发邮件看板：哪个邮箱发了哪些邮件到哪些邮箱。"""
    from app.models.auxiliary import MailLog
    limit = min(int(request.args.get('limit', 50)), 200)
    rows = (MailLog.query.filter_by(is_deleted=0)
            .order_by(MailLog.id.desc()).limit(limit).all())
    items = [{
        'id': r.id,
        'sender': r.sender_email,
        'recipient': r.recipient,
        'subject': r.subject,
        'type': r.mail_type,
        'typeLabel': _TYPE_LABELS.get(r.mail_type, '系统邮件'),
        'ok': bool(r.status),
        'error': r.error_msg,
        'time': r.created_at.strftime('%m-%d %H:%M') if r.created_at else '',
    } for r in rows]
    return success({'items': items})


@bp.route('/candidate/<candidate_id>/contact-info')
def get_candidate_contact_info(candidate_id):
    """GET /api/talent/candidate/<id>/contact-info — full mobile/email for contact action."""
    from app.services.talent_service import get_candidate_contact
    from app.services.config_service import append_audit_log
    data = get_candidate_contact(candidate_id)
    if data is None:
        raise AppError('NOT_FOUND', f'候选人不存在: {candidate_id}')
    append_audit_log('系统', '人才库', '查看联系方式',
                     f"查看候选人 {data.get('name', candidate_id)} 的联系方式")
    return success(data)


@bp.route('/contact', methods=['POST'])
def contact_candidate():
    """POST /api/talent/contact - record and optionally send candidate contact."""
    from app.services.talent_service import update_note, get_candidate_contact
    body = request.get_json(silent=True) or {}
    candidate_id = body.get('candidateId') or ''
    names = body.get('names') or []
    method = body.get('method', 'system_record')
    channel = (body.get('channel') or method or '').lower()
    draft = (body.get('draft') or '').strip()
    subject = (body.get('subject') or '').strip()

    if names:
        contacts = [{'name': name, 'note': f'[contact] HR via {method}'} for name in names]
        return success({'recorded': True, 'count': len(contacts), 'contacts': contacts})

    if candidate_id:
        contact = get_candidate_contact(candidate_id)
        if contact is None:
            raise AppError('NOT_FOUND', f'candidate not found: {candidate_id}')

        send_result = {'attempted': False, 'sent': False, 'message': 'recorded only'}
        if 'email' in channel or 'mail' in channel:
            if not contact.get('email'):
                send_result = {'attempted': True, 'sent': False, 'message': 'candidate email is empty'}
            elif not draft:
                send_result = {'attempted': True, 'sent': False, 'message': 'draft is empty'}
            else:
                import html
                from app.services.mail_sender import send_mail
                account_id = _sender_account_id_for_candidate(candidate_id)
                title = subject or f"Recruiting follow-up - {body.get('position') or body.get('demandPosition') or ''}".strip()
                html_body = '<div style="font-family:sans-serif;line-height:1.8">' + html.escape(draft).replace('\n', '<br>') + '</div>'
                ok, msg = send_mail(contact['email'], title, html_body,
                                    text_body=draft, account_id=account_id, mail_type='other')
                send_result = {'attempted': True, 'sent': bool(ok), 'message': msg, 'recipient': contact['email']}
        elif 'feishu' in channel or '飞书' in channel:
            if not draft:
                send_result = {'attempted': True, 'sent': False, 'message': 'draft is empty'}
            else:
                open_id = (body.get('openId') or body.get('feishu') or '').strip()
                try:
                    from app.services.feishu_client import search_user, send_text_message
                    if not open_id:
                        found = search_user(contact.get('email') or contact.get('mobile') or contact.get('name') or candidate_id)
                        open_id = found.get('open_id') or ''
                    if not open_id:
                        send_result = {'attempted': True, 'sent': False, 'message': 'feishu open_id not found'}
                    else:
                        r = send_text_message(open_id, draft)
                        send_result = {
                            'attempted': True,
                            'sent': bool(r.get('success')),
                            'message': r.get('error') or 'sent',
                            'messageId': r.get('message_id'),
                            'recipient': open_id,
                        }
                except Exception as exc:
                    send_result = {'attempted': True, 'sent': False, 'message': str(exc)}

        note_text = f"[contact] HR via {method}; sent={send_result.get('sent')}; msg={send_result.get('message')}"
        update_note(candidate_id, {'note': note_text})
        return success({'recorded': True, 'sent': send_result.get('sent'),
                        'send': send_result, 'contact': {'id': candidate_id, 'note': note_text}})

    raise AppError('BAD_REQUEST', 'missing candidateId or names')


def _sender_account_id_for_candidate(candidate_no):
    try:
        from app.models.candidate import Candidate, Resume
        c = Candidate.query.filter_by(candidate_no=candidate_no, is_deleted=0).first()
        if not c:
            return None
        r = (Resume.query.filter_by(candidate_id=c.id, is_deleted=0)
             .order_by(Resume.storage_time.desc()).first())
        return r.mail_account_id if r and r.mail_account_id else None
    except Exception:
        return None
