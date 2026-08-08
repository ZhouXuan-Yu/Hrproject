"""Scoring utilities: profile score, match score, decay, comprehensive ranking."""
from datetime import datetime, timezone
import logging
import threading

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Score rules — loaded lazily from ScoreRule DB table via config_service.
# The config page writes these; the matching engine reads them here.
# ---------------------------------------------------------------------------

_score_rules_cache = None
_score_rules_cache_time = None
_score_rules_cache_lock = threading.Lock()
_SCORE_RULES_CACHE_TTL = 60  # seconds


def _default_score_rules():
    """Return the factory-default scoring weights."""
    return {
        'profileWeight': 0.10,
        'matchWeight': 0.90,
        'decay30': 1.0,
        'decay90': 0.85,
        'decayOver90': 0.70,
        'passLine': 60,
        'topCount': 5,
    }


def _load_score_rules():
    """Load scoring rules from DB (cached for 60s). Falls back to defaults."""
    global _score_rules_cache, _score_rules_cache_time
    now = datetime.now(timezone.utc)

    # Fast path: return cached value if still fresh
    if _score_rules_cache is not None and _score_rules_cache_time is not None:
        if (now - _score_rules_cache_time).total_seconds() < _SCORE_RULES_CACHE_TTL:
            return _score_rules_cache

    with _score_rules_cache_lock:
        # Double-check inside lock
        if _score_rules_cache is not None and _score_rules_cache_time is not None:
            if (now - _score_rules_cache_time).total_seconds() < _SCORE_RULES_CACHE_TTL:
                return _score_rules_cache

        try:
            from app.services.config_service import get_score_rules
            rules = get_score_rules()
            if rules and isinstance(rules, dict):
                _score_rules_cache = rules
                _score_rules_cache_time = now
                log.info("Score rules loaded from DB: %s", rules)
                return rules
        except Exception as exc:
            log.warning("Failed to load score rules from DB: %s", exc)

        _score_rules_cache = _default_score_rules()
        _score_rules_cache_time = now
        return _score_rules_cache


def calc_decay_coefficient(storage_time, now=None, rules=None):
    """
    Calculate time-decay coefficient for a resume.

    Thresholds come from config (ScoreRule table), falling back to:
      <= 30 days: 1.0
      30-90 days: 0.85
      > 90 days: 0.70

    Pass rules= to override；omit to load from DB cache.
    """
    if rules is None:
        rules = _load_score_rules()

    decay30 = float(rules.get('decay30', 1.0))
    decay90 = float(rules.get('decay90', 0.85))
    decay_over = float(rules.get('decayOver90', 0.70))

    if now is None:
        now = datetime.now(timezone.utc)
    if storage_time.tzinfo is None:
        storage_time = storage_time.replace(tzinfo=timezone.utc)

    days = (now - storage_time).days
    if days <= 30:
        return decay30
    elif days <= 90:
        return decay90
    else:
        return decay_over


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
    Used as fallback when AI is unavailable, and as 40% component when AI is used.

    Edu: 0=none, 1=college, 2=bachelor, 3=master, 4=phd
    School: 0=none, 1=ordinary, 2=211, 3=985, 4=C9/overseas-top
    WorkYears: actual integer
    BigCompany: 0=no, 1=yes (reduced weight from 20 → 10)
    CertCount: integer count (reduced weight from 3/ea → 1/ea, max 5)

    exp_min (optional): 目标岗位最低年限。传入后年限项按相对达成度打分。
    skill_count (optional): 技能数量，应届上下文下参与年限项折算。
    """
    score = 0
    # Education (max 25)
    edu_scores = {0: 0, 1: 5, 2: 15, 3: 20, 4: 25}
    score += edu_scores.get(edu_level, 0)
    # School tier (max 15, was 20)
    school_scores = {0: 0, 1: 4, 2: 8, 3: 12, 4: 15}
    score += school_scores.get(school_level, 0)
    # Work years (max 25)
    if exp_min is not None and exp_min <= 0:
        school_bonus = {0: 0, 1: 4, 2: 8, 3: 12, 4: 15}.get(school_level, 4)
        skill_score = min(int(skill_count or 0) * 2, 10)
        score += min(25, school_bonus + skill_score)
    elif exp_min is not None:
        score += _years_score_relative(work_years or 0, exp_min)
    else:
        score += _years_score_flat(work_years or 0)
    # Big company (max 10, was 20 — reduced to avoid binary penalty)
    score += 10 if big_company else 0
    # Certificates (max 5, was 10 — per-cert weight 1 instead of 3)
    score += min(cert_count * 1, 5)
    raw = min(score, 80)
    # 画像分是候选人基础画像，不作为一票否决硬规则。
    # 硬规则总分上限 80，调整压缩系数：50–99
    return min(99, max(50, round(50 + raw * 0.61)))


def calc_profile_score_ai(candidate_data: dict) -> dict:
    """
    Profile score combining AI assessment (60%) + hard rules (40%).

    candidate_data should include keys from resume parsing:
        name, skills[], edu, school, work_years, big_company_flag, cert_count,
        extract_json (full resume parse result), summary

    Returns dict: {score, grade, class, ai_score, hard_score, ai_used, ai_detail}
    """
    # Hard rules baseline (always available)
    edu_label = str(candidate_data.get('edu', '') or '')
    school_label = str(candidate_data.get('school', '') or '')
    work_years_raw = candidate_data.get('workYears') or candidate_data.get('work_years') or 0
    big_company = int(candidate_data.get('bigCompany') or candidate_data.get('big_company_flag') or 0)
    cert_count = int(candidate_data.get('certCount') or candidate_data.get('cert_count') or 0)

    try:
        work_years = int(work_years_raw)
    except (ValueError, TypeError):
        work_years = 0

    edu_level = _EDU_MAP.get(edu_label, 0)
    school_level = _SCHOOL_MAP.get(school_label, 1)

    hard_score = calc_profile_score(
        edu_level=edu_level,
        school_level=school_level,
        work_years=work_years,
        big_company=big_company,
        cert_count=cert_count,
    )

    # Try AI assessment
    ai_score = None
    ai_detail = None
    ai_used = False

    try:
        extract = candidate_data.get('extract_json') or {}
        skills = candidate_data.get('skills') or extract.get('skills') or []
        summary = candidate_data.get('summary') or extract.get('summary') or ''
        name = candidate_data.get('name', '')

        prompt_data = {
            'name': name,
            'edu': edu_label,
            'school': school_label,
            'work_years': work_years,
            'skills': skills[:10],
            'summary': summary,
        }
        result = _deepseek_profile_score(prompt_data)
        if result and result.get('score') is not None:
            ai_score = max(0, min(100, int(result.get('score', 50))))
            ai_detail = result
            ai_used = True
    except Exception as exc:
        log.warning("AI profile scoring failed, using hard rules only: %s", exc)

    if ai_used and ai_score is not None:
        final = round(ai_score * 0.6 + hard_score * 0.4)
    else:
        final = hard_score

    return {
        'score': final,
        'grade': profile_grade(final),
        'class': match_color(final),
        'ai_score': ai_score,
        'hard_score': hard_score,
        'ai_used': ai_used,
        'ai_detail': ai_detail,
    }


def _deepseek_profile_score(candidate: dict) -> dict:
    """Call DeepSeek to assess candidate profile holistically.

    Input: {name, edu, school, work_years, skills[], summary}
    Returns: {score, dimensions: {...}, reasoning}
    """
    import json
    from app.services.deepseek_client import chat_completion_json

    system_prompt = """你是资深HR招聘评估专家。根据候选人简历信息，综合评估其画像分（0-100）。

评分维度与权重：
1. 技术深度与广度 (30%): 技能栈的深度、广度、掌握程度，技术路线的前瞻性
2. 项目经验与成果 (30%): 项目复杂度、个人角色与贡献、业务影响力
3. 成长性与潜力 (20%): 学习速度、技术成长曲线、自主驱动力
4. 背景与经验 (20%): 工作年限、公司背景、行业匹配度

【评分标准】
- 90-100: 顶尖人才，在多个维度表现卓越
- 80-89: 优秀，核心维度突出，有少量不足
- 60-79: 良好，能达到岗位基本要求
- 40-59: 一般，存在明显短板
- 0-39: 较弱，多项核心维度不达标

【重要原则 —— 最高优先级】
- 不要因为学历低就直接打低分：自学成才、十年深耕比985应届往往更值钱
- 不要因为没大厂经历就扣分：创业公司全栈实战往往比大厂螺丝钉更强
- 综合看项目难度、个人贡献、成长速度，而非只看标签
- 学历/学校只是加分项，不是否决项

【输出格式】
只输出JSON对象，第一个字符必须是{，最后一个字符必须是}：
{
  "score": 0-100整数,
  "dimensions": {
    "tech_depth": {"score": 0-30, "note": "简评"},
    "project_impact": {"score": 0-30, "note": "简评"},
    "growth_potential": {"score": 0-20, "note": "简评"},
    "background": {"score": 0-20, "note": "简评"}
  },
  "reasoning": "一句话总结评估依据"
}"""

    user_input = json.dumps(candidate, ensure_ascii=False, default=str)

    return chat_completion_json(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_input},
        ],
        temperature=0.3,
        max_tokens=800,
    )


# ---------------------------------------------------------------------------
# Skill keyword extraction for pre-filtering
# ---------------------------------------------------------------------------

def skill_keywords_from_jd(jd_text: str) -> list:
    """Extract skill keywords from JD text for pre-filtering candidates.

    Returns a list of lowercase skill keywords found in the JD.
    Used to skip candidates with zero skill overlap before AI matching.
    """
    if not jd_text:
        return []

    SKILL_DICT = [
        # Backend
        "java", "spring boot", "spring cloud", "spring", "mybatis", "hibernate",
        "python", "django", "flask", "fastapi",
        "golang", "go", "rust", "c++", "c#", ".net",
        "node.js", "nodejs", "express", "nestjs",
        # Frontend
        "javascript", "typescript", "react", "vue", "angular", "next.js", "nuxt",
        "html", "css", "sass", "less", "webpack", "vite",
        # Mobile
        "android", "ios", "swift", "kotlin", "flutter", "react native",
        # Data & AI
        "mysql", "postgresql", "mongodb", "redis", "elasticsearch",
        "kafka", "rabbitmq", "rocketmq",
        "hadoop", "spark", "flink", "hive", "clickhouse",
        "tensorflow", "pytorch", "scikit-learn", "机器学习", "深度学习",
        "nlp", "计算机视觉", "大模型", "llm",
        # DevOps
        "docker", "kubernetes", "k8s", "jenkins", "gitlab ci", "github actions",
        "terraform", "ansible", "prometheus", "grafana", "elk",
        # Cloud
        "aws", "azure", "阿里云", "腾讯云", "华为云",
        # Soft
        "微服务", "分布式", "高并发", "性能优化", "架构设计",
        "敏捷开发", "scrum", "code review",
        # Product/Design (for non-tech matching)
        "产品设计", "用户体验", "交互设计", "原型设计", "用户研究",
        "figma", "sketch", "axure",
        # HR/Finance/etc
        "招聘", "绩效", "薪酬", "培训", "员工关系",
        "财务分析", "预算", "税务", "审计",
    ]

    text_lower = jd_text.lower()
    found = []
    for kw in SKILL_DICT:
        if kw.lower() in text_lower:
            found.append(kw)

    return found


# ---------------------------------------------------------------------------
# Existing helpers (unchanged below)
# ---------------------------------------------------------------------------

_EDU_MAP = {
    '大专': 1, '专科': 1,
    '本科': 2, '学士': 2,
    '硕士': 3, '硕士研究生': 3,
    '博士': 4, '博士研究生': 4,
    '': 0, None: 0,
}

_SCHOOL_MAP = {
    '普通': 1, '': 1, None: 1,
    '211': 2,
    '985': 3,
    'C9': 4, 'C9/海外名校': 4, '海外名校': 4,
}


def normalize_profile_score(score, default=50):
    """Normalize persisted profile scores into the product scale: 50-99.

    Handles two storage formats:
      - Compressed scores (>= 50): returned as-is after clamping
      - Raw scores (< 50, typically 0-100): treated as uncompressed and
        mapped through the same compression formula used by calc_profile_score.
    """
    try:
        value = float(score)
    except (TypeError, ValueError):
        value = float(default)
    if value <= 0:
        value = float(default)

    if value < 50:
        # Likely a raw score (old storage format). Compress it.
        raw = min(value, 80)
        value = round(50 + raw * 0.61)
    else:
        value = round(value)

    return min(99, max(50, value))


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


def calc_recommend_score(profile_score, match_score, storage_time, now=None, rules=None):
    """
    Comprehensive recommendation score.
    Pool search: profileScore × profileWeight + matchScore × decay × matchWeight

    Weights come from config (ScoreRule table); pass rules= to override.
    """
    if rules is None:
        rules = _load_score_rules()
    pw = float(rules.get('profileWeight', 0.10))
    mw = float(rules.get('matchWeight', 0.90))
    decay = calc_decay_coefficient(storage_time, now, rules)
    return round(profile_score * pw + match_score * decay * mw, 1)


def calc_direct_score(profile_score, match_score, rules=None):
    """
    Direct application score (no decay).
    profileScore × profileWeight + matchScore × matchWeight

    Weights come from config (ScoreRule table); pass rules= to override.
    """
    if rules is None:
        rules = _load_score_rules()
    pw = float(rules.get('profileWeight', 0.10))
    mw = float(rules.get('matchWeight', 0.90))
    return round(profile_score * pw + match_score * mw, 1)


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
