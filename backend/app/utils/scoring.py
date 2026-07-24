"""Scoring utilities: profile score, match score, decay, comprehensive ranking."""
from datetime import datetime, timezone


def calc_decay_coefficient(storage_time, now=None):
    """
    Calculate time-decay coefficient for a resume.
    <= 30 days: 1.0
    30-90 days: 0.85
    > 90 days: 0.70
    """
    if now is None:
        now = datetime.now(timezone.utc)
    if storage_time.tzinfo is None:
        storage_time = storage_time.replace(tzinfo=timezone.utc)

    days = (now - storage_time).days
    if days <= 30:
        return 1.0
    elif days <= 90:
        return 0.85
    else:
        return 0.70


def _years_score_flat(work_years):
    """Legacy absolute scale (smoothed — 0 年不再恒 0，分段更平缓)."""
    if work_years >= 10:
        return 25
    if work_years >= 8:
        return 23
    if work_years >= 5:
        return 20
    if work_years >= 4:
        return 18
    if work_years >= 3:
        return 15
    if work_years >= 2:
        return 11
    if work_years >= 1:
        return 8
    return 2


def _years_score_relative(work_years, exp_min):
    """Score work years relative to the demand's minimum requirement (max 25)."""
    if exp_min <= 0:
        # 应届/不限经验岗：年限不再一刀切为 0，给基础分
        return _years_score_flat(work_years)
    ratio = work_years / exp_min
    if ratio >= 1.5:
        return 25
    if ratio >= 1.2:
        return 22
    if ratio >= 1.0:
        return 20
    if ratio >= 0.7:
        return 14
    if ratio >= 0.4:
        return 8
    return 4


def calc_profile_score(edu_level=0, school_level=0, work_years=0, big_company=0,
                       cert_count=0, exp_min=None, skill_count=0):
    """
    Static profile score using hard rules (0-100 scale).

    Edu: 0=none, 1=college, 2=bachelor, 3=master, 4=phd
    School: 0=none, 1=ordinary, 2=211, 3=985, 4=C9/overseas-top
    WorkYears: actual integer
    BigCompany: 0=no, 1=yes
    CertCount: integer count
    exp_min (optional): 目标岗位最低年限。传入后年限项按相对达成度打分；
        exp_min=0（应届岗）时年限权重按技能/在校表现折算，不再恒低。
    skill_count (optional): 技能数量，应届上下文下参与年限项折算。
    """
    score = 0
    # Education (max 25)
    edu_scores = {0: 0, 1: 5, 2: 15, 3: 20, 4: 25}
    score += edu_scores.get(edu_level, 0)
    # School tier (max 20)
    school_scores = {0: 0, 1: 5, 2: 10, 3: 15, 4: 20}
    score += school_scores.get(school_level, 0)
    # Work years (max 25)
    if exp_min is not None and exp_min <= 0:
        # 应届岗上下文：年限项由在校层次 + 技能储备折算，不再恒 0
        school_bonus = {0: 0, 1: 4, 2: 8, 3: 12, 4: 15}.get(school_level, 4)
        skill_score = min(int(skill_count or 0) * 2, 10)
        score += min(25, school_bonus + skill_score)
    elif exp_min is not None:
        score += _years_score_relative(work_years or 0, exp_min)
    else:
        score += _years_score_flat(work_years or 0)
    # Big company (max 20)
    score += 20 if big_company else 0
    # Certificates (max 10)
    score += min(cert_count * 3, 10)
    raw = min(score, 100)
    # 画像分是候选人基础画像，不作为一票否决硬规则。
    # 输出压到 50-99：50-69 保底、70-85 中等、86-99 优秀。
    return min(99, max(50, round(50 + raw * 0.49)))


def normalize_profile_score(score, default=50):
    """Normalize persisted profile scores into the product scale: 50-99."""
    try:
        value = float(score)
    except (TypeError, ValueError):
        value = float(default)
    if value <= 0:
        value = float(default)
    return min(99, max(50, round(value)))


def candidate_profile_score(candidate):
    """Return a consistent profile score for Candidate ORM rows."""
    existing = getattr(candidate, 'static_ability_score', None)
    if existing is not None and float(existing or 0) > 0:
        return normalize_profile_score(existing)
    return calc_profile_score(
        edu_level=getattr(candidate, 'edu_level', 0) or 0,
        school_level=getattr(candidate, 'school_level', 0) or 0,
        work_years=getattr(candidate, 'work_years', 0) or 0,
        big_company=getattr(candidate, 'big_company_flag', 0) or 0,
        cert_count=getattr(candidate, 'cert_count', 0) or 0,
    )


def calc_recommend_score(profile_score, match_score, storage_time, now=None):
    """
    Comprehensive recommendation score.
    Pool search: profileScore * 0.1 + matchScore * decay * 0.9
    """
    decay = calc_decay_coefficient(storage_time, now)
    return round(profile_score * 0.1 + match_score * decay * 0.9, 1)


def calc_direct_score(profile_score, match_score):
    """
    Direct application score (no decay).
    profileScore * 0.1 + matchScore * 0.9
    """
    return round(profile_score * 0.1 + match_score * 0.9, 1)


def profile_grade(score):
    """Convert numeric score to letter grade."""
    if score >= 95:
        return 'A+'
    elif score >= 85:
        return 'A'
    elif score >= 80:
        return 'A-'
    elif score >= 75:
        return 'B+'
    elif score >= 70:
        return 'B'
    elif score >= 65:
        return 'B-'
    elif score >= 60:
        return 'C+'
    else:
        return 'C'


def match_color(score):
    """Score color: >=80 green, 60-79 orange, <60 gray."""
    if score >= 80:
        return 'score-high'
    elif score >= 60:
        return 'score-mid'
    else:
        return 'score-low'
