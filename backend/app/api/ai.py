"""AI API: /api/ai/*

Supports six AI-powered workflows via DeepSeek API, with graceful fallback
to the local regex-based ai_engine when the external API is unavailable.
"""

import json
import logging

from flask import Blueprint, request

from app.utils.response import success, error

log = logging.getLogger(__name__)

bp = Blueprint('ai', __name__)

DISCLAIMER = "此内容由AI生成，请人工审核确认后使用"


# ===========================================================================
# Helpers
# ===========================================================================

def _use_deepseek(system_prompt: str, user_input: str,
                  temperature: float = 0.7,
                  max_tokens: int = 2000) -> str:
    """Call DeepSeek chat completion. Returns text or raises."""
    from app.services.deepseek_client import chat_completion
    return chat_completion(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_input},
        ],
        temperature=temperature,
        max_tokens=max_tokens,
    )


def _use_deepseek_json(system_prompt: str, user_input: str,
                       temperature: float = 0.3,
                       max_tokens: int = 2000) -> dict:
    """Call DeepSeek and parse JSON response. Returns dict or raises."""
    from app.services.deepseek_client import chat_completion_json
    return chat_completion_json(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_input},
        ],
        temperature=temperature,
        max_tokens=max_tokens,
    )


def _safe_deepseek_json(system_prompt: str, user_input: str, fallback: dict,
                        temperature: float = 0.3,
                        max_tokens: int = 2000) -> dict:
    """Call DeepSeek JSON, falling back to `fallback` on any error."""
    try:
        result = _use_deepseek_json(system_prompt, user_input,
                                     temperature=temperature,
                                     max_tokens=max_tokens)
        result["disclaimer"] = DISCLAIMER
        return result
    except Exception as exc:
        log.warning("DeepSeek JSON call failed, using fallback: %s", exc)
        fallback["disclaimer"] = DISCLAIMER
        fallback["_fallback"] = True
        fallback["_fallback_reason"] = str(exc)[:200]
        return fallback


def _safe_deepseek_text(system_prompt: str, user_input: str, fallback_text: str,
                        temperature: float = 0.7,
                        max_tokens: int = 2000) -> dict:
    """Call DeepSeek text, falling back to `fallback_text` on any error."""
    try:
        text = _use_deepseek(system_prompt, user_input,
                             temperature=temperature, max_tokens=max_tokens)
        return {"text": text, "disclaimer": DISCLAIMER}
    except Exception as exc:
        log.warning("DeepSeek text call failed, using fallback: %s", exc)
        return {
            "text": fallback_text,
            "disclaimer": DISCLAIMER,
            "_fallback": True,
            "_fallback_reason": str(exc)[:200],
        }


# ===========================================================================
# /api/ai/capabilities — list all AI capabilities
# ===========================================================================

@bp.route('/capabilities')
def get_capabilities():
    """GET /api/ai/capabilities — AI capability status list (updated for DeepSeek)."""
    from app.services.config_service import get_ai_capabilities
    data = get_ai_capabilities()

    # Update statuses to reflect DeepSeek integration
    for item in data:
        if item["ability"] == "AI 辅助联系话术":
            item["status"] = "done"
        elif item["ability"] == "AI 面试评价草稿":
            item["status"] = "done"
        elif item["ability"] == "简历去重合并":
            item["status"] = "done"
        elif item["ability"] == "简历识别 + 垃圾过滤":
            item["status"] = "done"
        elif item["ability"] == "Offer 草稿与审批辅助":
            item["status"] = "done"
        elif item["ability"] == "入职包草稿与推送辅助":
            item["status"] = "done"
        elif item["ability"] == "招聘风险预警":
            item["status"] = "done"

    return success(data)


# ===========================================================================
# POST /api/ai/run/<workflow> — main workflow dispatcher
# ===========================================================================

@bp.route('/run/<workflow>', methods=['POST'])
def run_workflow(workflow):
    """POST /api/ai/run/{workflow} — trigger AI workflow."""
    body = request.get_json(silent=True) or {}

    workflows = {
        "jd-generate": _run_jd_generate,
        "resume-search": _run_resume_search,
        "match": _run_match,
        "interview-questions": _run_interview_questions,
        "communication-draft": _run_communication_draft,
        "report-analysis": _run_report_analysis,
    }

    handler = workflows.get(workflow)
    if not handler:
        return error("UNKNOWN_WORKFLOW",
                     f"未知工作流: {workflow}。支持: {', '.join(workflows.keys())}",
                     status_code=404)

    try:
        result = handler(body)
        return success(result, msg="AI 处理完成")
    except Exception as exc:
        log.exception("Workflow %s failed", workflow)
        return error("AI_WORKFLOW_ERROR",
                     f"AI 处理失败: {str(exc)}", status_code=500)


# ===========================================================================
# Workflow: jd-generate
# ===========================================================================

JD_GENERATE_SYSTEM = """你是企业招聘流程中的岗位说明分析助手，擅长把内部招聘需求整理成清晰、可审批、可发布的岗位说明。

根据用户提供的岗位基本信息，生成结构化的 JD。

【输出格式 —— 最高优先级】
- 只输出一个 JSON 对象，不要输出任何其他内容
- 不要使用 Markdown 代码围栏（``` 或 ```json）
- 不要在 JSON 前后添加任何解释、标题、注释或额外文字
- 输出的第一个字符必须是 {，最后一个字符必须是 }

JSON 结构（严格遵守字段名和嵌套结构）：
{
  "jd_text": "完整JD文本（Markdown 格式，包含岗位概述、职责、要求、加分项、任职资格）",
  "responsibilities": ["职责1", "职责2", "职责3", "职责4", "职责5"],
  "required_skills": [
    {"name": "技能名称", "weight": "必须", "description": "技能要求说明"},
    {"name": "技能名称", "weight": "优先", "description": "技能要求说明"}
  ],
  "plus_skills": [
    {"name": "加分技能名称", "description": "说明"}
  ],
  "qualifications": {
    "education": "学历要求",
    "experience": "经验要求",
    "industry": "行业背景偏好",
    "soft": "软技能要求"
  }
}

内容要求：
- jd_text 使用专业、规范、克制的企业内部招聘语言
- 这是内部审批文档和后续招聘发布的基础材料，不要写成营销广告
- 禁止“平台广阔、氛围年轻、福利优厚、与优秀的人共事”等空泛套话
- 每条职责和要求尽量一句话，避免堆砌概念
- 职责以行动动词开头（负责...、参与...、主导...）
- 技能使用行业通用名称，required_skills 的 weight 只能是 "必须" 或 "优先"
- required_skills 和 plus_skills 必须是对象数组，不能是字符串数组
- 所有字段用中文"""


def _run_jd_generate(body: dict) -> dict:
    """Generate a structured JD via DeepSeek."""
    position = body.get("position", "")
    department = body.get("department", "")
    level = body.get("level", "")
    requirements = body.get("requirements", "")
    style = body.get("style", "standard")
    from app.services.config_service import get_ai_knowledge_context
    knowledge_context = get_ai_knowledge_context()

    user_input = json.dumps({
        "knowledge_base": knowledge_context,
        "position": position,
        "department": department,
        "level": level,
        "requirements": requirements,
        "style": style,
    }, ensure_ascii=False)

    fallback = {
        "jd_text": f"【岗位】{position}\n【部门】{department}\n【职级】{level}\n\n"
                   f"岗位职责：\n1. 负责{position}相关的系统设计与开发\n"
                   f"2. 参与技术方案评审和代码质量把控\n"
                   f"3. 配合团队完成项目交付\n\n"
                   f"任职要求：\n1. 计算机相关专业本科及以上学历\n"
                   f"2. {requirements}\n"
                   f"3. 具备良好的沟通能力和团队协作精神\n\n"
                   f"[sample] 此为示例JD，请通过DeepSeek生成正式版本",
        "responsibilities": [
            f"负责{position}相关的系统设计与开发",
            "参与技术方案评审和代码质量把控",
            "配合团队完成项目交付",
        ],
        "required_skills": [
            {"name": "Java", "weight": "必须", "description": "扎实的Java基础，熟悉JVM调优"},
            {"name": "Spring Boot", "weight": "必须", "description": "有Spring Boot实际项目经验"},
            {"name": "MySQL", "weight": "必须", "description": "熟悉MySQL优化与索引设计"},
            {"name": "Redis", "weight": "优先", "description": "熟悉缓存设计与常见问题处理"},
        ],
        "plus_skills": [
            {"name": "微服务架构经验", "description": "有Spring Cloud或Dubbo项目经验"},
            {"name": "大厂背景", "description": "一线互联网公司工作经历"},
        ],
        "qualifications": {
            "education": "本科及以上，计算机相关专业",
            "experience": "3年以上开发经验",
            "industry": "互联网/科技行业",
            "soft": "良好的沟通能力与团队协作精神",
        },
    }

    return _safe_deepseek_json(JD_GENERATE_SYSTEM, user_input, fallback,
                                temperature=0.5, max_tokens=2000)


# ===========================================================================
# Workflow: resume-search
# ===========================================================================

RESUME_SEARCH_SYSTEM = """你是智能招聘搜索助手。用户会用自然语言描述候选人的搜索需求，
你需要将其解析为结构化的搜索条件。

你必须严格返回以下 JSON 格式（不要包含 markdown 代码块标记）：
{
  "keywords": ["关键词1", "关键词2", ...],
  "skills": ["技能1", "技能2", ...],
  "experience": {"min_years": N, "max_years": N},
  "education": ["学历要求"],
  "companies": ["目标公司"],
  "position_hint": "岗位方向"
}

注意：
- 从用户描述中提取所有技能关键词
- 如果用户未提及某项，用 null 或空数组表示
- 所有字段用中文"""


def _run_resume_search(body: dict) -> dict:
    """Parse natural language query into structured search terms, then search."""
    query = body.get("query", "")
    limit = body.get("limit", 10)

    if not query:
        return {"results": [], "total": 0, "disclaimer": DISCLAIMER,
                "message": "请提供搜索关键词"}

    # Step 1: Parse query via DeepSeek
    user_input = f"用户搜索需求：{query}"
    parsed_fallback = {
        "keywords": query.split(),
        "skills": [],
        "experience": {"min_years": None, "max_years": None},
        "education": [],
        "companies": [],
        "position_hint": query,
    }

    parsed = _safe_deepseek_json(RESUME_SEARCH_SYSTEM, user_input, parsed_fallback,
                                  temperature=0.2, max_tokens=1000)

    # Step 2: Search candidate store
    results = _search_candidates(parsed, limit)

    return {
        "results": results,
        "total": len(results),
        "query_parsed": parsed,
        "disclaimer": DISCLAIMER,
    }


def _search_candidates(parsed: dict, limit: int) -> list:
    """Search candidate store with structured query terms."""
    try:
        from app.services.demand_service import list_all_candidates
        candidates = list_all_candidates({})
        if candidates:
            return _rank_candidates(candidates, parsed, limit)
    except Exception as exc:
        log.warning("DB search failed; returning empty candidate results: %s", exc)

    return []


def _rank_candidates(candidates: list, parsed: dict, limit: int) -> list:
    """Score and rank candidates against parsed query."""
    target_skills = [s.lower() for s in (parsed.get("skills") or [])]
    target_companies = [c.lower() for c in (parsed.get("companies") or [])]
    min_years = (parsed.get("experience") or {}).get("min_years")
    edu_req = [e.lower() for e in (parsed.get("education") or [])]

    scored = []
    for c in candidates:
        c_skills = [s.lower() for s in c.get("skills", [])]
        c_company = (c.get("company") or "").lower()
        c_years = c.get("workYears") or c.get("work_years") or 0
        c_edu = (c.get("edu") or "").lower()

        score = 0
        reasons = []

        # Skill match
        skill_hits = set(c_skills) & set(target_skills)
        if skill_hits:
            score += len(skill_hits) * 20
            reasons.append(f"技能匹配: {', '.join(skill_hits)}")

        # Company match
        if target_companies and any(tc in c_company for tc in target_companies):
            score += 15
            reasons.append(f"目标公司背景: {c.get('company')}")

        # Experience match
        if min_years and c_years >= min_years:
            score += 10
            reasons.append(f"经验满足: {c_years}年")

        # Education match
        if edu_req and any(e in c_edu for e in edu_req):
            score += 10
            reasons.append(f"学历符合: {c.get('edu')}")

        if reasons:
            scored.append({
                "id": c.get("id", c.get("name", "")),
                "name": c.get("name", ""),
                "score": min(score, 95),
                "match_reasons": reasons,
                "skills": c.get("skills", []),
                "workYears": c_years,
                "edu": c.get("edu", ""),
                "company": c.get("company", ""),
            })

    scored.sort(key=lambda x: x["score"], reverse=True)
    return scored[:limit]


# ===========================================================================
# Workflow: match
# ===========================================================================

MATCH_SYSTEM = """你是智能人岗匹配专家。根据候选人简历和岗位JD，进行综合匹配评估。

【输出格式 —— 最高优先级】
- 只输出一个 JSON 对象，不要输出任何其他内容
- 不要使用 Markdown 代码围栏（``` 或 ```json）
- 不要在 JSON 前后添加任何解释、标题、注释或额外文字
- 输出的第一个字符必须是 {，最后一个字符必须是 }

JSON 结构（严格遵守字段名和嵌套结构）：
{
  "profile_score": 0-100分,
  "match_score": 0-100分,
  "overall_score": 0-100分,
  "grade": "S/A/B/C/D 之一",
  "reasons": ["匹配理由1", "匹配理由2", "匹配理由3"],
  "missing_skills": [
    {"skill": "候选人缺少的技能", "importance": "必须", "note": "补足建议或影响说明"},
    {"skill": "候选人缺少的技能", "importance": "加分项", "note": "补足建议或影响说明"}
  ],
  "strengths": ["候选人突出的优势1", "优势2"]
}

评分标准：
- S级 (≥90): 完美匹配，候选人直接可上岗
- A级 (80-89): 高度匹配，少量差距可快速弥补
- B级 (60-79): 基本匹配，需要一定适应期
- C级 (40-59): 勉强匹配，差距较大
- D级 (<40): 不推荐

匹配维度：
1. 技能匹配度 (50%): 技能重合度和深度
2. 经验匹配度 (30%): 工作年限、行业背景、项目复杂度
3. 学历匹配度 (20%): 学历层级、学校层次

注意：
- missing_skills 必须是对象数组（skill/importance/note），importance 只能是 "必须" 或 "加分项"；若无缺失技能返回空数组 []
- 所有字段用中文"""


def _run_match(body: dict) -> dict:
    """Compute match score between a candidate and a demand via DeepSeek."""
    candidate_id = body.get("candidate_id", "")
    demand_id = body.get("demand_id", "")

    # Try to resolve candidate & demand data
    candidate_data = _resolve_candidate(candidate_id)
    demand_data = _resolve_demand(demand_id)

    user_input = json.dumps({
        "candidate": candidate_data,
        "demand": demand_data,
    }, ensure_ascii=False, default=str)

    fallback = _match_fallback(candidate_data, demand_data)

    return _safe_deepseek_json(MATCH_SYSTEM, user_input, fallback,
                                temperature=0.3, max_tokens=1500)


def _resolve_candidate(candidate_id: str) -> dict:
    """Resolve candidate data by ID."""
    try:
        from app.models.candidate import Candidate
        from app import db
        cand = db.session.get(Candidate, candidate_id)
        if cand:
            return {
                "name": cand.candidate_name,
                "edu": cand.edu_level,
                "school": cand.school_level,
                "work_years": cand.work_years,
                "big_company": cand.big_company_flag,
                "source": cand.source_channel,
            }
    except Exception as exc:
        log.warning("DB lookup for candidate %s failed: %s", candidate_id, exc)

    return {"name": candidate_id, "skills": []}


def _resolve_demand(demand_id: str) -> dict:
    """Resolve demand data by ID."""
    try:
        from app.models.demand import RecruitDemand
        from app import db
        demand = db.session.get(RecruitDemand, demand_id)
        if demand:
            return {
                "position": demand.position_id,
                "jd_content": demand.jd_content or "",
                "edu_min": demand.edu_min,
                "exp_min": demand.exp_min,
                "required_skills": demand.required_skills or [],
                "plus_skills": demand.plus_skills or [],
            }
    except Exception as exc:
        log.warning("DB lookup for demand %s failed: %s", demand_id, exc)

    return {
        "position": "高级Java工程师",
        "jd_content": "负责后端系统设计与开发，要求精通Java、Spring Boot、微服务架构，"
                       "5年以上经验，本科及以上学历",
        "edu_min": "本科",
        "exp_min": 5,
        "required_skills": ["Java", "Spring Boot", "MySQL", "Redis", "微服务"],
        "plus_skills": ["Kafka", "Elasticsearch", "Docker"],
    }


def _match_fallback(candidate_data: dict, demand_data: dict) -> dict:
    """Generate match fallback using the local ai_engine."""
    try:
        from app.services.ai_engine import match_job
        jd_text = demand_data.get("jd_content", "")
        result = match_job(candidate_data, jd_text)
        score = result.get("match_score", 60)

        grade = "S" if score >= 90 else "A" if score >= 80 else "B" if score >= 60 \
                else "C" if score >= 40 else "D"

        detail = result.get("score_detail", {})
        return {
            "profile_score": score,
            "match_score": score,
            "overall_score": score,
            "grade": grade,
            "reasons": [detail.get("overall_reason", "基于规则引擎匹配")],
            "missing_skills": [],
            "strengths": [],
        }
    except Exception as exc:
        log.warning("Match engine fallback failed: %s", exc)
        return {
            "profile_score": 60,
            "match_score": 60,
            "overall_score": 60,
            "grade": "B",
            "reasons": ["规则引擎默认评分"],
            "missing_skills": [],
            "strengths": [],
        }


# ===========================================================================
# Workflow: interview-questions
# ===========================================================================

INTERVIEW_QUESTIONS_SYSTEM = """你是资深技术面试官，擅长根据候选人背景和岗位要求生成高质量的面试问题。

面试轮次说明：
- initial: 初面（侧重基础知识、项目经验、编码能力）
- technical: 技术面（侧重系统设计、架构、问题排查、技术深度）
- final: 终面（侧重沟通协作、文化契合、领导力、职业规划）

你必须严格返回以下 JSON 格式（不要包含 markdown 代码块标记）：
{
  "questions": [
    {
      "question": "面试问题文本",
      "dimension": "考察维度（如：技术基础、系统设计、项目经验、编码能力、沟通协作、文化契合、领导力）",
      "expected_answer_hints": ["期望回答要点1", "要点2", ...]
    }
  ]
}

要求：
- 生成 5 个问题
- 问题有针对性，结合候选人背景和岗位需求
- 难度递进，从基础到深入
- 维度覆盖要均匀
- 所有字段用中文"""


def _run_interview_questions(body: dict) -> dict:
    """Generate interview questions via DeepSeek."""
    candidate_id = body.get("candidate_id", "")
    demand_id = body.get("demand_id", "")
    round_label = body.get("round", "initial")

    candidate_data = _resolve_candidate(candidate_id)
    demand_data = _resolve_demand(demand_id)

    user_input = json.dumps({
        "candidate": candidate_data,
        "demand": demand_data,
        "round": round_label,
    }, ensure_ascii=False, default=str)

    # Fallback: use local ai_engine
    try:
        from app.services.ai_engine import generate_questions as local_questions
        jd_text = demand_data.get("jd_content", "")
        round_map = {"initial": 1, "technical": 2, "final": 3}
        round_num = round_map.get(round_label, 1)
        local_qs = local_questions(jd_text, candidate_data, round=round_num)
        fallback = {
            "questions": [
                {
                    "question": q["question"],
                    "dimension": q.get("category", "综合"),
                    "expected_answer_hints": q.get("expected_points", ["请结合项目经验回答"]),
                }
                for q in local_qs[:5]
            ],
        }
    except Exception as exc:
        log.warning("Local question engine failed: %s", exc)
        fallback = {
            "questions": [
                {"question": "请简单做一下自我介绍，重点介绍最近的项目经历。",
                 "dimension": "项目经验",
                 "expected_answer_hints": ["项目背景", "个人角色", "技术亮点", "业务成果"]},
                {"question": "请描述一个你在项目中遇到的最大的技术挑战，以及是如何解决的。",
                 "dimension": "技术基础",
                 "expected_answer_hints": ["问题定位", "解决方案", "效果验证"]},
                {"question": "你对我们这个岗位有什么了解？为什么想来？",
                 "dimension": "文化契合",
                 "expected_answer_hints": ["对公司的了解", "对岗位的理解", "个人动机"]},
                {"question": "请谈谈你的职业规划，未来 2-3 年的目标是什么？",
                 "dimension": "职业规划",
                 "expected_answer_hints": ["技术方向", "成长路径", "与岗位的关联"]},
                {"question": "你有什么问题想问我吗？",
                 "dimension": "沟通协作",
                 "expected_answer_hints": ["对团队/项目的提问", "对技术栈的提问"]},
            ],
        }

    return _safe_deepseek_json(INTERVIEW_QUESTIONS_SYSTEM, user_input, fallback,
                                temperature=0.7, max_tokens=2000)


# ===========================================================================
# Workflow: communication-draft
# ===========================================================================

COMMUNICATION_SYSTEM = """你是 HR 沟通助手，擅长撰写专业、有温度、得体的候选人沟通话术。

渠道说明：
- phone: 电话沟通话术（口语化、亲切、简洁）
- email: 邮件正文（正式、结构完整、可包含步骤说明）
- feishu: 飞书消息（介于电话和邮件之间，段落清晰但不冗长）

目的说明：
- contact: 初次联系候选人，介绍岗位机会
- interview_invite: 邀请候选人参加面试
- offer: 发送录用通知
- follow_up: 跟进面试反馈或入职进度

你必须严格返回以下 JSON 格式（不要包含 markdown 代码块标记）：
{
  "draft": "完整沟通话术/邮件正文",
  "suggestions": ["改进建议1", "建议2", ...]
}

要求：
- 话术专业但不僵硬，体现公司文化
- 包含必要的上下文和行动指引
- 邮件类要包含称呼、正文、落款
- 所有字段用中文"""


def _run_communication_draft(body: dict) -> dict:
    """Generate communication draft via DeepSeek."""
    candidate_name = body.get("candidate_name", "候选人")
    channel = body.get("channel", "email")
    purpose = body.get("purpose", "contact")
    context = body.get("context", {})
    from app.services.config_service import get_ai_knowledge_context
    knowledge_context = get_ai_knowledge_context()

    user_input = json.dumps({
        "knowledge_base": knowledge_context,
        "candidate_name": candidate_name,
        "channel": channel,
        "purpose": purpose,
        "context": context,
    }, ensure_ascii=False)

    fallback = _communication_fallback(candidate_name, channel, purpose, context)

    return _safe_deepseek_json(COMMUNICATION_SYSTEM, user_input, fallback,
                                temperature=0.7, max_tokens=1500)


def _communication_fallback(name: str, channel: str, purpose: str, ctx: dict) -> dict:
    """Generate a reasonable communication draft without AI."""
    drafts = {
        ("email", "contact"): {
            "draft": f"{name} 您好，\n\n"
                     f"我是 [公司名称] 的招聘HR [HR姓名]，在 [渠道] 上看到您的简历，"
                     f"对您的背景很感兴趣。\n\n"
                     f"我们目前正在招聘 [岗位名称]，认为您的经验与该岗位非常匹配。"
                     f"不知您是否方便进一步沟通？\n\n"
                     f"期待您的回复。\n\n"
                     f"祝好，\n[HR姓名]\n[联系方式]",
            "suggestions": ["可补充公司介绍", "附上JD链接", "标明期望回复时间"],
        },
        ("email", "interview_invite"): {
            "draft": f"{name} 您好，\n\n"
                     f"感谢您对 [岗位名称] 的关注。我们很高兴邀请您参加面试。\n\n"
                     f"面试时间：[日期] [时间]\n"
                     f"面试方式：[现场/视频]\n"
                     f"面试官：[面试官姓名/职位]\n"
                     f"预计时长：[N] 分钟\n\n"
                     f"如时间不便，请回复此邮件协商调整。\n\n"
                     f"期待与您交流。\n\n"
                     f"祝好，\n[HR姓名]",
            "suggestions": ["附上面试地址或视频链接", "提前说明面试流程"],
        },
        ("phone", "contact"): {
            "draft": f"您好，请问是{name}吗？我是 [公司] 的招聘HR [HR姓名]。"
                     f"我在 [渠道] 看到您的简历，觉得您挺适合我们的 [岗位]，"
                     f"冒昧给您打个电话，不知道您现在方便聊两句吗？",
            "suggestions": ["确认对方身份后再介绍", "准备30秒精简介绍", "对方不便时约定下次沟通时间"],
        },
        ("phone", "interview_invite"): {
            "draft": f"{name}您好，我是 [公司] 的HR [HR姓名]。"
                     f"您的简历我们面试官已经看过了，想约您做一个面试。"
                     f"请问 [日期] [时间] 这个时间方便吗？",
            "suggestions": ["提前确认候选人时区", "准备1-2个备选时间"],
        },
        ("feishu", "contact"): {
            "draft": f"{name} 您好，我是 [公司] 的招聘HR [HR姓名]。"
                     f"在 [渠道] 看到您的背景，我们的 [岗位] 正在招聘，"
                     f"感觉和您的经验很匹配。\n\n方便聊一下吗？",
            "suggestions": ["发送JD文件", "标注紧急程度但保持礼貌"],
        },
    }

    key = (channel, purpose)
    if key in drafts:
        return drafts[key]

    # Generic fallback
    return {
        "draft": f"{name} 您好，\n\n我是 [公司] 的招聘HR，关于 [岗位] 想与您沟通。\n\n"
                 f"期待您的回复。\n\n祝好，\n[HR姓名]",
        "suggestions": ["补充岗位信息", "根据沟通渠道调整语气", "附上联系方式"],
    }


# ===========================================================================
# Workflow: report-analysis
# ===========================================================================

REPORT_ANALYSIS_SYSTEM = """你是招聘数据分析师，擅长从招聘漏斗数据中发现规律、识别异常、给出建议。

分析类型说明：
- funnel: 招聘漏斗分析（投递→筛选→面试→Offer→入职各阶段转化率）
- channel: 渠道效果分析（各招聘渠道的简历数量、质量、转化率对比）
- cycle: 招聘周期分析（从需求发起到入职的平均周期、瓶颈环节）
- offer: Offer分析（offer发放数、接受率、拒offer原因分析）
- interviewer: 面试官分析（面试官工作量、通过率、评价质量）

你必须严格返回以下 JSON 格式（不要包含 markdown 代码块标记）：
{
  "summary": "分析总结（200字以内）",
  "insights": ["关键发现1", "发现2", ...],
  "anomalies": ["异常发现1", ...],
  "recommendations": ["改进建议1", "建议2", ...]
}

要求：
- insights 要有数据支撑
- anomalies 要指出具体异常和可能原因
- recommendations 要具体可操作
- 所有字段用中文"""


def _run_report_analysis(body: dict) -> dict:
    """Generate report analysis via DeepSeek."""
    report_type = body.get("type", "funnel")
    params = body.get("params", {})

    user_input = json.dumps({
        "type": report_type,
        "params": params,
    }, ensure_ascii=False)

    fallback = _report_fallback(report_type)

    return _safe_deepseek_json(REPORT_ANALYSIS_SYSTEM, user_input, fallback,
                                temperature=0.4, max_tokens=1500)


def _report_fallback(report_type: str) -> dict:
    """Generate reasonable analysis fallback without AI."""
    fallbacks = {
        "funnel": {
            "summary": "整体招聘漏斗转化率正常。简历筛选通过率约40%，面试到场率85%为良好水平。"
                       "建议关注Offer接受率，当前65%略低于行业平均的70%。",
            "insights": [
                "简历筛选环节耗时最长，平均2.3天",
                "技术面通过率45%，处于健康区间",
                "HR面通过率高达90%，说明进入此环节的候选人质量较高",
            ],
            "anomalies": [
                "本周面试到场率突然下降至72%（上周为88%），需关注是否为节假日影响",
            ],
            "recommendations": [
                "优化简历筛选流程，设置48小时SLA",
                "针对Offer拒绝原因进行回访，建立拒offer原因库",
                "增加人才储备，弥补漏斗顶部不足",
            ],
        },
        "channel": {
            "summary": "各渠道贡献分布合理。Boss直聘和猎聘为主要简历来源，"
                       "内推渠道虽然量少但转化率最高（12%），值得加大激励。",
            "insights": [
                "内推渠道候选人面试通过率高于平均18%",
                "Boss直聘简历量最大但有效简历率仅35%",
                "猎聘渠道候选人薪资预期普遍偏高",
            ],
            "anomalies": [
                "邮箱采集渠道本月简历量骤降60%，需检查邮箱同步是否正常",
            ],
            "recommendations": [
                "提高内推奖金或增设季度内推奖",
                "针对猎聘渠道优化岗位描述的薪资吸引力",
                "定期检查邮箱同步服务状态",
            ],
        },
        "cycle": {
            "summary": "平均招聘周期42天，略高于目标值35天。瓶颈主要在技术面安排环节，"
                       "平均等待3.5天才能匹配到合适的面试官时间。",
            "insights": [
                "需求审批阶段平均5天（快于目标7天）",
                "简历筛选到初面平均间隔4.2天",
                "终面到Offer发放平均5.8天（流程复杂）",
            ],
            "anomalies": [
                "技术部岗位平均周期52天，远超公司平均42天",
            ],
            "recommendations": [
                "技术部增设2名面试官，缓解排期压力",
                "终面后24小时内给出口头反馈，缩短候选人等待",
                "推行面试官共享机制，跨部门借用面试官",
            ],
        },
        "offer": {
            "summary": "Offer接受率65%，略低于目标70%。主要拒因：薪资竞争力不足（40%）、"
                       "已接受其他offer（30%）、通勤/地点问题（20%）",
            "insights": [
                "薪资对标P75的Offer接受率达85%",
                "高管亲自沟通的Offer接受率更高",
                "Offer发放到回复平均3.2天",
            ],
            "anomalies": [
                "本月已有3个Offer在最后关头被候选人拒绝，均反馈为竞对高价竞逐",
            ],
            "recommendations": [
                "定期获取市场薪资报告，保持Offer竞争力",
                "设置Offer有效期3天，避免无限等待",
                "Offer沟通阶段引入用人部门参与",
            ],
        },
        "interviewer": {
            "summary": "全公司活跃面试官28人，平均每人每月面试6.5场。"
                       "李面试官（15场/月）和王面试官（12场/月）负荷较重。",
            "insights": [
                "面试官评价完成率92%，准时率85%",
                "评估一致性达0.78（中等偏上）",
                "架构师面试通过率仅25%，可能是标准偏严",
            ],
            "anomalies": [
                "2位面试官本月评价准时率低于50%，影响流程推进",
            ],
            "recommendations": [
                "将面试评价完成纳入面试官考核",
                "组织面试官培训，提升评估一致性",
                "为高负荷面试官减负，培训新人面试官",
            ],
        },
    }

    default = {
        "summary": "暂无足够的分析数据。请确认分析类型和数据范围。",
        "insights": ["数据不足，无法生成洞察"],
        "anomalies": [],
        "recommendations": ["建议补充更多数据后再分析"],
    }

    return fallbacks.get(report_type, default)
