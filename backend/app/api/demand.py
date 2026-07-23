"""Demand API: /api/demand/*"""
from flask import Blueprint, request, g
from app.utils.response import success, success_list, error, AppError

bp = Blueprint('demand', __name__)


@bp.route('/list')
def get_list():
    """GET /api/demand/list — paginated demand list."""
    from app.services.demand_service import list_demands
    data, total = list_demands(request.args)
    page = int(request.args.get('page', 1))
    page_size = int(request.args.get('pageSize', 20))
    return success_list(data, total, page, page_size)


@bp.route('/create', methods=['POST'])
def create():
    """POST /api/demand/create — create a new demand."""
    from app.services.demand_service import create_demand
    result = create_demand(request.get_json(silent=True) or {})
    return success(result)


@bp.route('/<demand_id>', methods=['PATCH'])
def update(demand_id):
    """PATCH /api/demand/{id} — partial update."""
    from app.services.demand_service import update_demand
    result = update_demand(demand_id, request.get_json(silent=True) or {})
    return success(result)


@bp.route('/<demand_id>/close', methods=['POST'])
def close(demand_id):
    """POST /api/demand/{id}/close — close a demand."""
    from app.services.demand_service import close_demand
    result = close_demand(demand_id)
    return success(result)


@bp.route('/<demand_id>')
def get_detail(demand_id):
    """GET /api/demand/{id} — demand detail with approval nodes."""
    from app.services.demand_service import get_demand_detail
    data = get_demand_detail(demand_id)
    return success(data)


@bp.route('/<demand_id>/candidates')
def get_candidates(demand_id):
    """GET /api/demand/{id}/candidates — candidates linked to this demand."""
    from app.services.demand_service import list_demand_candidates
    data = list_demand_candidates(demand_id, request.args)
    return success(data)


@bp.route('/<demand_id>/candidates/<name>/detail')
def get_candidate_detail(demand_id, name):
    """GET /api/demand/{id}/candidates/{name}/detail — single candidate match detail."""
    from app.services.match_service import get_match_result
    data = get_match_result(demand_id, name)
    return success(data)


@bp.route('/<demand_id>/candidates/<name>/link', methods=['POST'])
def link_candidate(demand_id, name):
    """POST /api/demand/{id}/candidates/{name}/link — link candidate to demand."""
    from app.services.demand_service import link_candidate_to_demand
    result = link_candidate_to_demand(demand_id, name)
    return success(result)


@bp.route('/<demand_id>/match', methods=['POST'])
def match_candidates(demand_id):
    """POST /api/demand/{id}/match — trigger batch matching and return results."""
    from app.services.match_service import batch_match_demand, filter_hard_requirements
    body = request.get_json(silent=True) or {}
    candidate_ids = body.get('candidateIds')
    top_n = int(body.get('topN', 5))

    if body.get('includeTalentPool'):
        from app.models.candidate import Candidate
        rows = Candidate.query.filter(
            Candidate.is_deleted == 0,
            Candidate.black_flag == 0,
            Candidate.status.in_(('available', 'reserve')),
        ).all()
        candidate_ids = [c.candidate_no or str(c.id) for c in rows]
        match_result = batch_match_demand(demand_id, candidate_ids, top_n)
        from app.services.demand_service import link_candidate_to_demand
        linked = []
        for item in match_result.get('candidates', []):
            try:
                r = link_candidate_to_demand(demand_id, item.get('name') or item.get('id'))
                linked.append({'name': item.get('name'), **r})
            except Exception as exc:
                linked.append({'name': item.get('name'), 'linked': False, 'reason': str(exc)})
        match_result['linked'] = linked
        return success(match_result)

    if body.get('applyHardFilter'):
        from app.services.demand_service import list_demand_candidates
        from app.services.demand_service import get_demand_detail
        demand_detail = get_demand_detail(demand_id)
        raw_candidates = list_demand_candidates(demand_id, {})
        filter_result = filter_hard_requirements(raw_candidates, demand_detail)

        passed_ids = [c.get('id', c.get('name')) for c in filter_result['passed']]
        match_result = batch_match_demand(demand_id, passed_ids or candidate_ids, top_n)
        match_result['hardFilter'] = {
            'total': filter_result['total'],
            'passedCount': filter_result['passedCount'],
            'filteredCount': filter_result['filteredCount'],
            'filtered': filter_result['filtered'],
        }
    else:
        match_result = batch_match_demand(demand_id, candidate_ids, top_n)

    return success(match_result)


@bp.route('/<demand_id>/approve', methods=['POST'])
def approve_node(demand_id):
    """POST /api/demand/{id}/approve — approve the current approval node.

    Expects JSON body: { "level": 1, "opinion": "ok" }
    """
    from app.services.approval_service import approve
    body = request.get_json(silent=True) or {}
    level = body.get('level')
    approve_user_id = getattr(g, 'current_user_id', 1)
    current_role = getattr(g, 'current_role', 'employee')
    opinion = body.get('opinion', '')

    if not level:
        return error('BAD_REQUEST', '缺少审批层级 level', 400)

    numeric_id = _resolve_demand_id(demand_id)
    result = approve(numeric_id, int(level), int(approve_user_id), opinion, current_role)
    return success(result)


@bp.route('/<demand_id>/reject', methods=['POST'])
def reject_node(demand_id):
    """POST /api/demand/{id}/reject — reject an approval node.

    Expects JSON body: { "level": 1, "opinion": "不合适" }
    """
    from app.services.approval_service import reject
    body = request.get_json(silent=True) or {}
    level = body.get('level')
    approve_user_id = getattr(g, 'current_user_id', 1)
    current_role = getattr(g, 'current_role', 'employee')
    opinion = body.get('opinion', '')

    if not level:
        return error('BAD_REQUEST', '缺少审批层级 level', 400)

    numeric_id = _resolve_demand_id(demand_id)
    result = reject(numeric_id, int(level), int(approve_user_id), opinion, current_role)
    return success(result)


# ── New endpoints for Task 4 / Task 7 ──

@bp.route('/<demand_id>', methods=['DELETE'])
def delete_demand(demand_id):
    """DELETE /api/demand/{id} — soft-delete a demand (only draft/rejected)."""
    from app.services.demand_service import delete_demand
    result = delete_demand(demand_id)
    return success(result)


@bp.route('/<demand_id>/submit', methods=['POST'])
def submit_for_approval(demand_id):
    """POST /api/demand/{id}/submit — submit demand for approval (draft -> approval)."""
    from app.services.demand_service import submit_for_approval
    result = submit_for_approval(demand_id)
    return success(result)


def _resolve_demand_id(demand_id):
    """Resolve a demand identifier (demand_no or numeric id) to numeric id."""
    try:
        return int(demand_id)
    except (ValueError, TypeError):
        from app.models.demand import RecruitDemand
        demand = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if demand:
            return demand.id
        raise AppError('NOT_FOUND', f'需求 {demand_id} 不存在')
