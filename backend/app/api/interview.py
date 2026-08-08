"""Interview API: /api/interview/*"""
from flask import Blueprint, request, g
from app.utils.response import success, success_list, error, AppError

bp = Blueprint('interview', __name__)


def _invalidate_interview_flow():
    from app.services.cache_service import invalidate_many
    invalidate_many(
        'interview:list', 'interview:alerts', 'interview:calendar', 'interview:detail',
        'demand:list', 'demand:detail', 'demand:candidates',
        'talent:list', 'talent:detail', 'talent:match',
        'dashboard:kpi', 'dashboard:funnel', 'dashboard:dept-progress',
        'dashboard:channel', 'dashboard:risk-alerts',
    )


@bp.route('/list')
def get_list():
    """GET /api/interview/list — paginated interview list."""
    from app.services.cache_service import cached
    from app.services.interview_service import list_interviews

    def load():
        data, total = list_interviews(request.args)
        return {'items': data, 'total': total}

    result = cached('interview:list', request.args, load, ttl=30)
    page = int(request.args.get('page', 1))
    page_size = int(request.args.get('pageSize', 20))
    return success_list(result['items'], result['total'], page, page_size)


@bp.route('/alerts')
def get_alerts():
    """GET /api/interview/alerts — interview alerts."""
    from app.services.cache_service import cached
    from app.services.interview_service import get_alerts
    data = cached('interview:alerts', request.args, get_alerts, ttl=15)
    return success(data)


@bp.route('/create', methods=['POST'])
def create():
    """POST /api/interview/create — create interview booking."""
    from app.services.cache_service import lock
    from app.services.interview_service import create_interview
    import hashlib
    import json
    body = request.get_json(silent=True) or {}
    digest = hashlib.sha256(
        json.dumps(body, sort_keys=True, ensure_ascii=False, default=str).encode('utf-8')
    ).hexdigest()[:24]
    with lock(f'interview:create:{digest}', ttl=5) as acquired:
        if not acquired:
            raise AppError('DUPLICATE_REQUEST', '面试安排正在处理中，请勿重复提交', 409)
        result = create_interview(body)
    _invalidate_interview_flow()
    return success(result)


@bp.route('/schedule', methods=['POST'])
def schedule():
    """POST /api/interview/schedule — schedule interview (batch)."""
    from app.services.interview_service import schedule_interview
    result = schedule_interview(request.get_json(silent=True) or {})
    _invalidate_interview_flow()
    return success(result)


@bp.route('/<interview_id>/evaluate', methods=['POST'])
def evaluate(interview_id):
    """POST /api/interview/{id}/evaluate — submit evaluation."""
    from app.services.interview_service import evaluate_interview
    result = evaluate_interview(interview_id, request.get_json(silent=True) or {})
    _invalidate_interview_flow()
    return success(result)


@bp.route('/<book_id>')
def get_detail(book_id):
    """GET /api/interview/{id} — single interview detail."""
    from app.services.cache_service import cached
    from app.services.interview_service import get_interview, _normalize_book_id
    normalized_id = _normalize_book_id(book_id)
    data = cached(
        'interview:detail',
        {'book_id': normalized_id},
        lambda: get_interview(normalized_id),
        ttl=30,
    )
    return success(data)


@bp.route('/<book_id>', methods=['DELETE'])
def cancel(book_id):
    """DELETE /api/interview/{id} — cancel interview with reason."""
    from app.services.interview_service import cancel_interview, _normalize_book_id
    body = request.get_json(silent=True) or {}
    result = cancel_interview(_normalize_book_id(book_id), reason=body.get('reason', ''))
    _invalidate_interview_flow()
    return success(result)


@bp.route('/<book_id>/complete', methods=['POST'])
def complete(book_id):
    """POST /api/interview/{id}/complete — mark interview as completed (scheduled -> evaluating)."""
    from app.services.interview_service import complete_interview, _normalize_book_id
    result = complete_interview(_normalize_book_id(book_id), request.get_json(silent=True) or {})
    _invalidate_interview_flow()
    return success(result)


@bp.route('/<book_id>/offer', methods=['POST'])
def send_offer(book_id):
    """POST /api/interview/{id}/offer — send offer after evaluation passed."""
    from app.services.interview_service import send_offer, _normalize_book_id
    result = send_offer(_normalize_book_id(book_id), request.get_json(silent=True) or {})
    _invalidate_interview_flow()
    return success(result)


@bp.route('/<book_id>/onboard', methods=['POST'])
def confirm_onboard(book_id):
    """POST /api/interview/{id}/onboard — confirm candidate onboard.

    Optional body: { user: {realName, mobile, email, deptId, positionId, employeeNo?, roleCode?} }
    When user data is provided, also creates IamUser + Employee records.
    """
    from app.services.interview_service import confirm_onboard, _normalize_book_id
    body = request.get_json(silent=True) or {}
    user_data = body.get('user') or None
    result = confirm_onboard(_normalize_book_id(book_id), user_data=user_data)
    _invalidate_interview_flow()
    return success(result)


@bp.route('/calendar')
def get_calendar():
    """GET /api/interview/calendar — calendar view with month or week_start query param."""
    from app.services.cache_service import cached
    from app.services.interview_service import get_calendar
    week_start = request.args.get('week_start')
    month = request.args.get('month')
    data = cached(
        'interview:calendar',
        request.args,
        lambda: get_calendar(week_start=week_start, month=month),
        ttl=30,
    )
    return success(data)
