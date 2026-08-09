"""AI 工作流编排器 — 6 个工作流，DeepSeek 优先 + 本地引擎 fallback。

对齐现有 Flask 后端 app/api/ai.py 的 6 个 workflow。
"""
import logging
from typing import Callable

from app.services import ai_engine
from app.services.deepseek_client import (DISCLAIMER, chat_completion,
                                          chat_completion_json)

log = logging.getLogger(__name__)

# 工作流注册表
WORKFLOWS: dict[str, Callable[[dict], dict]] = {}

# ── System prompts ────────────────────────────────────────────────────────

JD_GENERATE_SYSTEM = (
    "你是资深招聘专家，根据岗位名称和部门生成一份专业的岗位描述(JD)。"
    "包含：岗位职责、任职要求、技能要求、加分项。用中文回答。"
)

RESUME_PARSE_SYSTEM = (
    "你是资深简历解析专家，从简历文本中提取结构化信息。"
    "输出字段：name, phone, email, education, school, workYears, skills, summary。"
)

MATCH_SYSTEM = (
    "你是资深人岗匹配专家，根据岗位描述和候选人信息评估匹配度。"
    "输出：score(0-100), overallComment, dimensions{salaryExpectation, skillFit, experienceFit, educationFit}。"
)

INTERVIEW_QA_SYSTEM = (
    "你是资深面试官，根据岗位描述和候选人简历生成 5 道面试题。"
    "输出：questions[{question, focus, difficulty}]。"
)

TALENT_SEARCH_SYSTEM = (
    "你是招聘系统查询解析器，把用户自然语言需求解析为结构化筛选条件。"
    "输出：keyword, city, education, minWorkYears, skills[], salaryRange。"
)

OFFER_EMAIL_SYSTEM = (
    "你是招聘 HR，根据候选人信息和 Offer 信息撰写一封专业的 Offer 邮件。"
    "语气正式友好，包含岗位、薪酬、入职日期、需要携带的材料。"
)


# ── 各工作流实现 ─────────────────────────────────────────────────────────

def _safe_json(system: str, user_input: str, fallback: dict, temperature: float = 0.3) -> dict:
    """调用 DeepSeek JSON，失败时返回 fallback。"""
    try:
        result = chat_completion_json(
            [{"role": "system", "content": system}, {"role": "user", "content": user_input}],
            temperature=temperature)
        result["disclaimer"] = DISCLAIMER
        return result
    except Exception as exc:
        log.warning("DeepSeek JSON failed, using fallback: %s", exc)
        fallback["disclaimer"] = DISCLAIMER
        fallback["_fallback"] = True
        fallback["_fallback_reason"] = "AI 服务暂时不可用，已切换本地引擎"
        return fallback


def jd_generate(data: dict) -> dict:
    role = data.get('role') or data.get('position') or ''
    dept = data.get('dept') or data.get('department') or ''
    user_input = f"岗位名称：{role}，部门：{dept}。请生成完整 JD。"
    fallback = {
        "title": role,
        "department": dept,
        "responsibilities": ["负责" + role + "相关工作"],
        "requirements": ["熟悉岗位技能", "相关工作经验"],
        "skills": ["请人工补充技能要求"],
    }
    return _safe_json(JD_GENERATE_SYSTEM, user_input, fallback)


def resume_parse(data: dict) -> dict:
    text = data.get('text') or data.get('content') or ''
    fallback = ai_engine.parse_resume(text)
    if not text.strip():
        return {"error": "简历内容为空", "disclaimer": DISCLAIMER}
    return _safe_json(RESUME_PARSE_SYSTEM, text, fallback)


def match_score(data: dict) -> dict:
    jd = data.get('jd') or data.get('jobDescription') or ''
    candidate = data.get('candidate') or {}
    candidate_text = (
        f"候选人信息：{candidate.get('skills', [])}，"
        f"学历：{candidate.get('education', '')}，"
        f"工作年限：{candidate.get('workYears', '')}，"
        f"简历摘要：{candidate.get('summary', '')}"
    )
    user_input = f"岗位描述：{jd}\n{candidate_text}"
    fallback = ai_engine.match_job(candidate, jd)
    return _safe_json(MATCH_SYSTEM, user_input, fallback)


def interview_qa(data: dict) -> dict:
    jd = data.get('jd') or ''
    resume = data.get('resume') or {}
    user_input = f"岗位描述：{jd}\n候选人简历：{resume.get('summary', '')}"
    fallback = ai_engine.generate_questions(jd, resume)
    return _safe_json(INTERVIEW_QA_SYSTEM, user_input, fallback)


def talent_search(data: dict) -> dict:
    query = data.get('query') or data.get('keyword') or ''
    user_input = f"用户搜索需求：{query}"
    fallback = {
        "keyword": query,
        "skills": [],
        "_fallback": True,
    }
    return _safe_json(TALENT_SEARCH_SYSTEM, user_input, fallback)


def offer_email_generate(data: dict) -> dict:
    candidate = data.get('candidate') or {}
    offer = data.get('offer') or {}
    user_input = (
        f"候选人：{candidate.get('name', '')}，应聘岗位：{offer.get('position', '')}，"
        f"薪酬：{offer.get('salary', '')}，入职日期：{offer.get('entryDate', '')}"
    )
    fallback = {
        "subject": f"录用通知 - {offer.get('position', '岗位')}",
        "body": f"尊敬的{data.get('candidate', {}).get('name', '候选人')}：恭喜您通过面试，欢迎加入我们！",
        "disclaimer": DISCLAIMER,
        "_fallback": True,
    }
    return _safe_json(OFFER_EMAIL_SYSTEM, user_input, fallback)


# ── 注册工作流 ───────────────────────────────────────────────────────────

WORKFLOWS = {
    "jd-generate": jd_generate,
    "resume-parse": resume_parse,
    "match-score": match_score,
    "interview-qa": interview_qa,
    "talent-search": talent_search,
    "offer-email-generate": offer_email_generate,
    # 兼容现有 Flask 端点命名
    "jd_generate": jd_generate,
    "resume_parse": resume_parse,
    "match": match_score,
    "interview_questions": interview_qa,
    "communication_draft": offer_email_generate,
    "report_analysis": jd_generate,
}

# SSE 流式工作流（支持流式输出）
STREAM_WORKFLOWS = {
    "jd-generate": jd_generate,
    "match-score": match_score,
}


def run_workflow(name: str, data: dict) -> dict:
    handler = WORKFLOWS.get(name)
    if not handler:
        raise ValueError(f"未知工作流: {name}")
    return handler(data)
