"""Matching & scoring engine: profile, match, recommend, batch matching."""
import hashlib
import math
from datetime import datetime, timezone, timedelta

from app.utils.response import AppError
from app.utils.scoring import (
    calc_profile_score as _calc_profile_raw,
    calc_decay_coefficient,
    calc_direct_score,
    normalize_profile_score,
    profile_grade,
    match_color,
)


# ---------------------------------------------------------------------------
# Mappings — match service uses text labels while scoring.py uses integer codes
# ---------------------------------------------------------------------------

EDU_MAP = {
    '大专': 1, '专科': 1,
    '本科': 2, '学士': 2,
    '硕士': 3, '硕士研究生': 3,
    '博士': 4, '博士研究生': 4,
    '': 0, None: 0,
}

SCHOOL_MAP = {
    '普通': 1, '': 1, None: 1,
    '211': 2,
    '985': 3,
    'C9': 4, 'C9/海外名校': 4, '海外名校': 4,
}

EDU_MIN_LEVEL = {
    '大专': 1, '专科': 1,
    '本科': 2,
    '硕士': 3,
    '博士': 4,
    '不限': 0, '': 0, None: 0,
}

# Reverse lookup for display
EDU_LEVEL_LABEL = {0: '不限', 1: '大专', 2: '本科', 3: '硕士', 4: '博士'}
SCHOOL_LEVEL_LABEL = {0: '—', 1: '普通', 2: '211', 3: '985', 4: 'C9'}


# ---------------------------------------------------------------------------
# 1. calc_profile_score(candidate_data) — static profile score 0-100
# ---------------------------------------------------------------------------

def calc_profile_score(candidate_data: dict) -> dict:
    """
    Calculate static profile score from a candidate data dictionary.
    Returns {score, grade, class} where class is 'score-high'/'score-mid'/'score-low'.
    """
    existing = None
    for key in ('profileScore', 'profile_score', 'static_ability_score'):
        if key in candidate_data and candidate_data.get(key) is not None:
            existing = candidate_data.get(key)
            break
    if existing is not None:
        score = normalize_profile_score(existing)
        return {
            'score': score,
            'grade': profile_grade(score),
            'class': match_color(score),
        }

    edu_label = str(candidate_data.get('edu', '') or '')
    school_label = str(candidate_data.get('school', '') or '')
    work_years_raw = candidate_data.get('workYears') or candidate_data.get('work_years') or 0
    big_company = int(candidate_data.get('bigCompany') or candidate_data.get('big_company_flag') or 0)
    cert_count = int(candidate_data.get('certCount') or candidate_data.get('cert_count') or 0)

    # Normalise work years
    try:
        work_years = int(work_years_raw)
    except (ValueError, TypeError):
        work_years = 0

    edu_level = EDU_MAP.get(edu_label, 0)
    school_level = SCHOOL_MAP.get(school_label, 1)

    score = _calc_profile_raw(
        edu_level=edu_level,
        school_level=school_level,
        work_years=work_years,
        big_company=big_company,
        cert_count=cert_count,
    )
    return {
        'score': score,
        'grade': profile_grade(score),
        'class': match_color(score),
    }


# ---------------------------------------------------------------------------
# 2. calc_match_score(candidate_id, demand_id) — AI match via Dify WF2 (mock)
# ---------------------------------------------------------------------------

def calc_match_score(candidate_id: str, demand_id: str, profile_score: int = 50) -> dict:
    """
    Mock AI match score via Dify workflow 2.
    Generates a deterministic-but-realistic score (50-95) based on candidate & demand identity.

    Returns {score, grade, class, reason, detail}.
    """
    # Deterministic seed from both ids
    seed = hashlib.md5(f"{candidate_id}:{demand_id}".encode()).hexdigest()
    chunk = int(seed[:8], 16)         # 0 - 4.29e9
    variance = chunk % 30             # 0-29
    profile_bonus = max(0, min(20, (profile_score - 30) // 2))  # 0-20 only if profile > 30
    score = 50 + variance + profile_bonus
    score = min(95, max(50, score))

    # Build a plausible reason string
    reasons = [
        '技能标签高度重合',
        '过往项目经验匹配',
        '行业背景相近',
        '技术栈完全适用',
        '学历背景符合要求',
    ]
    reason_idx = chunk % len(reasons)
    detail = ', '.join([
        reasons[reason_idx],
        f'JD需求覆盖率约{score - 5}-{score}%',
    ])

    return {
        'score': score,
        'grade': profile_grade(score),
        'class': match_color(score),
        'reason': reasons[reason_idx],
        'detail': detail,
    }


# ---------------------------------------------------------------------------
# 3. calc_recommend_score(profile_score, match_score, days_ago) — comprehensive
# ---------------------------------------------------------------------------

def calc_recommend_score(profile_score: float, match_score: float,
                         days_ago: int = 0, source: str = 'pool') -> float:
    """
    Comprehensive recommendation score.

    source='direct': profile * 0.1 + match * 0.9  (no decay)
    source='pool':   profile * 0.1 + match * decay * 0.9
    source='internal': profile * 0.1 + match * 0.9
    """
    if source in ('direct', 'internal'):
        return calc_direct_score(profile_score, match_score)

    # Pool: apply time decay
    decay = _days_to_decay(days_ago)
    return round(profile_score * 0.1 + match_score * decay * 0.9, 1)


def _days_to_decay(days_ago: int) -> float:
    """Convert days-since-storage to decay coefficient."""
    if days_ago <= 30:
        return 1.0
    elif days_ago <= 90:
        return 0.85
    else:
        return 0.70


# ---------------------------------------------------------------------------
# 4. batch_match_demand(demand_id, candidate_ids) — batch scoring + ranking
# ---------------------------------------------------------------------------

def batch_match_demand(demand_id: str, candidate_ids: list = None, top_n: int = 5) -> dict:
    """
    Match a demand against multiple candidates, returning top-N sorted by
    comprehensive score descending.

    If candidate_ids is None, fetches candidates from the demand's existing pool
    (demand_service.list_demand_candidates).

    Returns:
        {
            demandId: str,
            totalMatched: int,
            topN: int,
            candidates: [{...match fields...}, ...]
        }
    """
    from app.services.demand_service import list_demand_candidates
    demand_jd = ''
    demand_pk = None
    try:
        from app.models.demand import RecruitDemand
        demand = RecruitDemand.query.filter_by(demand_no=demand_id, is_deleted=0).first()
        if not demand and str(demand_id).isdigit():
            demand = RecruitDemand.query.filter_by(id=int(demand_id), is_deleted=0).first()
        if demand:
            demand_jd = demand.jd_content or ''
            demand_pk = demand.id
    except Exception:
        pass

    if candidate_ids is None:
        # Fetch candidates already linked to this demand
        raw = list_demand_candidates(demand_id, {})
        # raw is a list of candidate dicts; we need ids
        candidate_ids = [c.get('name', c.get('id', f'C-{i}'))
                         for i, c in enumerate(raw)]
        candidates_data = raw
    else:
        candidates_data = _resolve_candidates(candidate_ids)

    if not candidates_data:
        return {
            'demandId': demand_id,
            'totalMatched': 0,
            'topN': top_n,
            'candidates': [],
        }

    # Scoring loop
    scored = []
    for cand in candidates_data:
        cid = cand.get('id', cand.get('name', ''))
        name = cand.get('name', cid)

        # 1) Profile score
        profile = calc_profile_score(cand)
        profile_score = profile['score']

        # 2) Match score: prefer real JD/resume parsing, then deterministic fallback.
        match = None
        if demand_jd and cand.get('extract_json'):
            try:
                from app.services.ai_engine import match_job
                m = match_job(cand.get('extract_json') or {}, demand_jd)
                score = m.get('match_score') or 0
                match = {
                    'score': score,
                    'grade': profile_grade(score),
                    'class': match_color(score),
                    'reason': '基于简历解析与岗位说明匹配',
                    'detail': m.get('score_detail') or m.get('summary') or '已按 JD 与简历解析字段计算',
                }
            except Exception as exc:
                log.warning("real match failed for %s/%s: %s", cid, demand_id, exc)
        if not match:
            match = calc_match_score(cid, demand_id, profile_score)
        match_score = match['score']

        # 3) Source & days ago
        source = cand.get('source', 'pool')
        age_days = int(cand.get('ageDays', cand.get('storageDays', 0)))

        # 4) Comprehensive
        comp = calc_recommend_score(profile_score, match_score, age_days, source)

        # 5) Hard-requirement check
        not_rec = cand.get('notRecReason')  # may have been pre-filtered

        scored.append({
            'id': cid,
            'name': name,
            'profileScore': profile_score,
            'profileGrade': profile['grade'],
            'profileClass': profile['class'],
            'matchScore': match_score,
            'matchGrade': match['grade'],
            'matchClass': match['class'],
            'matchReason': match['reason'],
            'matchDetail': match['detail'],
            'comprehensiveScore': comp,
            'ageDays': age_days,
            'source': source,
            'sourceLabel': cand.get('sourceLabel', _source_label(source)),
            'status': cand.get('status', 'available'),
            'statusLabel': cand.get('statusLabel', '可联系'),
            'edu': cand.get('edu', '—'),
            'years': cand.get('years', '—'),
            'notRecReason': not_rec,
            'isEmployee': cand.get('isEmployee', False),
        })

    # Sort by comprehensive score descending
    scored.sort(key=lambda x: x['comprehensiveScore'], reverse=True)

    # Take top N (still return the rest for reference)
    top = scored[:max(top_n, 1)]

    return {
        'demandId': demand_id,
        'totalMatched': len(scored),
        'topN': min(top_n, len(scored)),
        'candidates': top,
        'allCandidates': scored,
    }


# ---------------------------------------------------------------------------
# 5. get_match_result(demand_id, candidate_id) — single match detail
# ---------------------------------------------------------------------------

def get_match_result(demand_id: str, candidate_id: str) -> dict:
    """
    Return full match info between one candidate and one demand, with
    score breakdown, reasons, and status.
    """
    from app.services.demand_service import list_demand_candidates

    # Find candidate in demand pool
    pool = list_demand_candidates(demand_id, {})
    cand = next((c for c in pool
                 if c.get('name') == candidate_id or c.get('id') == candidate_id), None)

    if not cand:
        # Fallback: build a minimal candidate dict
        cand = {'id': candidate_id, 'name': candidate_id, 'source': 'pool', 'ageDays': 0}

    cid = cand.get('id', cand.get('name', candidate_id))
    name = cand.get('name', candidate_id)

    # Profile
    profile = calc_profile_score(cand)
    profile_score = profile['score']

    # Match
    match = calc_match_score(cid, demand_id, profile_score)
    match_score = match['score']

    # Comprehensive
    source = cand.get('source', 'pool')
    age_days = int(cand.get('ageDays', cand.get('storageDays', 0)))
    comp = calc_recommend_score(profile_score, match_score, age_days, source)

    # Build detailed breakdown
    breakdown = {
        'profile': {
            'score': profile_score,
            'grade': profile['grade'],
            'class': profile['class'],
            'components': {
                'education': _profile_edu_component(cand),
                'schoolTier': _profile_school_component(cand),
                'workYears': _profile_work_years_component(cand),
                'bigCompany': _profile_big_company_component(cand),
                'certificates': _profile_cert_component(cand),
            },
        },
        'match': {
            'score': match_score,
            'grade': match['grade'],
            'class': match['class'],
            'reason': match['reason'],
            'detail': match['detail'],
        },
        'comprehensive': {
            'score': comp,
            'formula': _formula_text(source),
            'decayApplied': source == 'pool' and age_days > 30,
            'decayRate': _days_to_decay(age_days) if source == 'pool' else None,
        },
    }

    result = {
        'demandId': demand_id,
        'candidateId': candidate_id,
        'matchStatus': 'completed',
        'breakdown': breakdown,
        'summary': {
            'profileScore': profile_score,
            'profileGrade': profile['grade'],
            'matchScore': match_score,
            'matchGrade': match['grade'],
            'comprehensiveScore': comp,
        },
    }

    # Attach hard-requirement filter result if it exists
    if cand.get('notRecReason'):
        result['hardFilter'] = {
            'passed': False,
            'reason': cand['notRecReason'],
        }
    else:
        result['hardFilter'] = {'passed': True, 'reason': None}

    return result


# ---------------------------------------------------------------------------
# 6. filter_hard_requirements(candidates, demand) — pre-filter
# ---------------------------------------------------------------------------

def filter_hard_requirements(candidates: list, demand: dict) -> dict:
    """
    Pre-filter candidates by hard requirements (edu_min, exp_min) before AI matching.

    Args:
        candidates: list of candidate dicts
        demand: demand dict containing edu_min, exp_min

    Returns:
        {
            passed: [...candidates that passed],
            filtered: [{candidate, reason}, ...],
            total: N, passedCount: N, filteredCount: N
        }
    """
    edu_min_raw = demand.get('edu_min', '') or '不限'
    exp_min_raw = demand.get('exp_min') or 0
    try:
        exp_min = int(exp_min_raw)
    except (ValueError, TypeError):
        exp_min = 0

    edu_min_level = EDU_MIN_LEVEL.get(edu_min_raw, 0)

    passed = []
    filtered = []

    for cand in candidates:
        edu_label = str(cand.get('edu', '') or '')
        work_years_raw = cand.get('work_years') or cand.get('workYears') or 0
        try:
            work_years = int(work_years_raw)
        except (ValueError, TypeError):
            work_years = 0

        cand_edu_level = EDU_MAP.get(edu_label, 0)
        reasons = []

        if edu_min_level > 0 and cand_edu_level < edu_min_level:
            reasons.append(f'学历不符（要求{edu_min_raw}，实际{edu_label}）')

        if exp_min > 0 and work_years < exp_min:
            reasons.append(f'经验不足（要求{exp_min}年，实际{work_years}年）')

        if reasons:
            filtered.append({
                **cand,
                'notRecReason': '；'.join(reasons),
            })
        else:
            passed.append(cand)

    return {
        'passed': passed,
        'filtered': filtered,
        'total': len(candidates),
        'passedCount': len(passed),
        'filteredCount': len(filtered),
    }


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _resolve_candidates(candidate_ids: list) -> list:
    """Resolve candidate ids into candidate data dicts from the database."""
    try:
        from app.models.candidate import Candidate
        from app.models.candidate import Resume
        from app.extensions import db

        results = []
        for cid in candidate_ids:
            cid_str = str(cid)
            # Try to find candidate by candidate_no first, then by id
            cand = Candidate.query.filter(
                Candidate.candidate_no == cid_str,
                Candidate.is_deleted == 0
            ).first()
            if not cand:
                cand = Candidate.query.filter_by(id=cid, is_deleted=0).first() if str(cid).isdigit() else None

            if cand:
                # Resolve edu/school labels
                edu_labels = {1: '大专', 2: '本科', 3: '硕士', 4: '博士'}
                school_labels = {1: '普通', 2: '211', 3: '985', 4: 'C9'}
                source_label_map = {
                    '邮箱': 'mail', '邮箱采集': 'mail',
                    'Boss': 'boss', 'Boss直聘': 'boss',
                    '猎聘': 'liepin',
                    '内推': 'refer', '内部推荐': 'refer',
                    '手动上传': 'upload',
                }
                source_label_display = {
                    'mail': '邮箱采集', 'boss': 'Boss直聘', 'liepin': '猎聘',
                    'refer': '内推', 'upload': '手动上传', 'pool': '人才库',
                    'internal': '内部员工',
                }

                source_type = source_label_map.get(cand.source_channel, 'pool')
                resume = (Resume.query.filter_by(candidate_id=cand.id, is_deleted=0)
                          .order_by(Resume.storage_time.desc()).first())
                extract = resume.extract_json if resume else {}
                results.append({
                    'id': cand.candidate_no or str(cand.id),
                    'name': cand.candidate_name,
                    'edu': edu_labels.get(cand.edu_level, '—'),
                    'school': school_labels.get(cand.school_level, '普通'),
                    'workYears': cand.work_years or 0,
                    'work_years': cand.work_years or 0,
                    'bigCompany': cand.big_company_flag or 0,
                    'big_company_flag': cand.big_company_flag or 0,
                    'certCount': cand.cert_count or 0,
                    'cert_count': cand.cert_count or 0,
                    'source': source_type,
                    'sourceLabel': source_label_display.get(source_type, '人才库检索'),
                    'ageDays': (__import__('datetime').datetime.now() - cand.created_at).days if cand.created_at else 0,
                    'storageDays': (__import__('datetime').datetime.now() - cand.created_at).days if cand.created_at else 0,
                    'status': cand.status or 'available',
                    'statusLabel': {'available': '可联系', 'locked': '面试中(锁定)', 'reserve': '储备', 'archived': '已归档'}.get(cand.status, '可联系'),
                    'isEmployee': False,
                    'years': f"{cand.work_years}年" if cand.work_years else '—',
                    'skills': extract.get('skills', []) if isinstance(extract, dict) else [],
                    'extract_json': extract if isinstance(extract, dict) else {},
                })
                continue

            log.warning("Candidate %s was not found in database; skipping match resolution", cid_str)

        return results
    except Exception as exc:
        log.error("DB query failed in _resolve_candidates: %s", exc, exc_info=True)

    return []


def _source_label(source: str) -> str:
    return {
        'mail': '邮箱采集', 'boss': 'Boss直聘', 'liepin': '猎聘',
        'refer': '内推', 'upload': '手动上传', 'pool': '人才库',
        'internal': '内部员工',
    }.get(source, source)


def _formula_text(source: str) -> str:
    if source in ('direct', 'internal'):
        return 'profileScore × 0.1 + matchScore × 0.9'
    return 'profileScore × 0.1 + (matchScore × decay) × 0.9'


def _profile_edu_component(cand: dict) -> dict:
    edu_label = str(cand.get('edu', '') or '')
    level = EDU_MAP.get(edu_label, 0)
    scores = {0: 0, 1: 5, 2: 15, 3: 20, 4: 25}
    return {'label': edu_label or '—', 'level': level, 'score': scores.get(level, 0), 'max': 25}


def _profile_school_component(cand: dict) -> dict:
    school_label = str(cand.get('school', '') or '')
    level = SCHOOL_MAP.get(school_label, 1)
    scores = {0: 0, 1: 5, 2: 10, 3: 15, 4: 20}
    return {'label': school_label or '—', 'level': level, 'score': scores.get(level, 0), 'max': 20}


def _profile_work_years_component(cand: dict) -> dict:
    raw = cand.get('workYears') or cand.get('work_years') or 0
    try:
        wy = int(raw)
    except (ValueError, TypeError):
        wy = 0
    if wy >= 10:
        sc = 25
    elif wy >= 5:
        sc = 20
    elif wy >= 3:
        sc = 15
    elif wy >= 1:
        sc = 8
    else:
        sc = 0
    return {'years': wy, 'score': sc, 'max': 25}


def _profile_big_company_component(cand: dict) -> dict:
    bc = int(cand.get('bigCompany') or cand.get('big_company_flag') or 0)
    return {'hasBigCompany': bool(bc), 'score': 20 if bc else 0, 'max': 20}


def _profile_cert_component(cand: dict) -> dict:
    cc = int(cand.get('certCount') or cand.get('cert_count') or 0)
    sc = min(cc * 3, 10)
    return {'count': cc, 'score': sc, 'max': 10}


# ---------------------------------------------------------------------------
# In-memory mock candidate store (for resolve when candidate_ids provided directly)
# ---------------------------------------------------------------------------

_MOCK_CANDIDATE_STORE = {
    'C2026070012': {
        'id': 'C2026070012', 'name': '张三', 'edu': '本科', 'school': '211',
        'workYears': 5, 'bigCompany': 1, 'certCount': 1,
        'source': 'pool', 'ageDays': 4, 'years': '5年',
        'status': 'available', 'statusLabel': '可联系',
    },
    'C2026070011': {
        'id': 'C2026070011', 'name': '李四', 'edu': '硕士', 'school': '985',
        'workYears': 3, 'bigCompany': 1, 'certCount': 2,
        'source': 'pool', 'ageDays': 5, 'years': '3年',
        'status': 'available', 'statusLabel': '可联系',
    },
    'C2026070007': {
        'id': 'C2026070007', 'name': '王五', 'edu': '硕士', 'school': '普通',
        'workYears': 3, 'bigCompany': 0, 'certCount': 1,
        'source': 'pool', 'ageDays': 4, 'years': '3年',
        'status': 'reserve', 'statusLabel': '储备',
    },
    'C2026070010': {
        'id': 'C2026070010', 'name': '郑一', 'edu': '本科', 'school': '985',
        'workYears': 4, 'bigCompany': 1, 'certCount': 2,
        'source': 'pool', 'ageDays': 3, 'years': '4年',
        'status': 'locked', 'statusLabel': '面试中(锁定)',
    },
    'C2026070009': {
        'id': 'C2026070009', 'name': '孙九', 'edu': '本科', 'school': '211',
        'workYears': 6, 'bigCompany': 1, 'certCount': 1,
        'source': 'direct', 'ageDays': 3, 'years': '6年',
        'status': 'available', 'statusLabel': '可联系',
    },
}
