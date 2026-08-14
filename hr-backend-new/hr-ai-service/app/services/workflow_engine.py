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
    "你是资深招聘专家，负责为招聘系统生成规范的岗位说明书(JD)。"
    "根据岗位名称和部门，生成内容全面、具体、真实、可直接用于正式招聘发布的 JD。"
    "必须严格返回 JSON 对象（不要输出 markdown 代码块，直接输出 JSON），字段如下：\n"
    '{\n'
    '  "position": "岗位名称",\n'
    '  "department": "所属部门",\n'
    '  "overview": "岗位概述（1-2 句话，说明该岗位的核心职责与价值）",\n'
    '  "responsibilities": ["岗位职责（5-8 条，具体可执行，不要套话）"],\n'
    '  "required_skills": [{"name": "必备技能", "weight": "必须", "description": "要求说明"}],\n'
    '  "plus_skills": [{"name": "加分技能", "description": "说明"}],\n'
    '  "qualifications": {"education": "学历要求", "experience": "经验年限", "industry": "行业经验", "soft": "软素质"}\n'
    '}\n'
    "要求：岗位职责要具体、可衡量、贴合该岗位真实工作内容；"
    "技能要求区分必备与加分；学历/经验/行业要符合该岗位的市场通行标准；"
    "禁止编造虚假数据，不要空洞套话。"
)

# 流式接口专用：输出 markdown 正文（而非 JSON），供"思考过程"面板实时展示
JD_GENERATE_STREAM_SYSTEM = (
    "你是资深招聘专家，负责为招聘系统撰写规范的岗位说明书(JD)。"
    "根据岗位名称和部门，用中文输出一份完整的 JD 正文（markdown 格式，不要输出 JSON），"
    "必须包含以下章节：岗位概述、岗位职责（5-8 条，具体可执行）、任职要求（学历/经验/技能，符合市场通行标准）、加分项。"
    "内容要全面、真实、专业，可直接用于正式招聘发布，禁止空洞套话和编造虚假数据。"
)

RESUME_PARSE_SYSTEM = (
    "你是资深简历解析专家，从简历文本中提取结构化信息。"
    "输出字段：name, phone, email, education, school, workYears, skills, summary。"
)

MATCH_SYSTEM = (
    "你是资深人岗匹配专家，根据岗位描述和候选人信息评估匹配度。"
    "必须严格返回 JSON 对象（不要输出 markdown 代码块），字段如下：\n"
    '{\n'
    '  "overall_score": 综合匹配得分(0-100 整数),\n'
    '  "profile_score": 候选人画像分(0-100，基于学历/经验/软素质),\n'
    '  "match_score": 技能匹配分(0-100，基于技能与岗位契合度),\n'
    '  "grade": "综合等级 S/A/B/C",\n'
    '  "strengths": ["匹配优势 3-5 条，具体、贴合候选人"],\n'
    '  "missing_skills": [{"skill": "待补足技能", "importance": "必备/加分", "note": "补齐建议"}],\n'
    '  "reasons": ["详细匹配理由 3-5 条"]\n'
    '}\n'
    "要求：评分客观有据，理由具体，禁止空洞套话。"
)

INTERVIEW_QA_SYSTEM = (
    "你是资深面试官，根据岗位描述和候选人简历生成 5 道针对性面试题。"
    "必须严格返回 JSON 对象（不要输出 markdown 代码块），字段如下：\n"
    '{\n'
    '  "questions": [{"question": "面试问题", "dimension": "考察维度(技术能力/项目经验/沟通协作/职业动机等)", "expected_answer_hints": ["回答要点提示"]}]\n'
    '}\n'
    "要求：问题具体、有区分度、贴合岗位与候选人背景，维度覆盖全面，禁止套话。"
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
        "position": role,
        "department": dept,
        "overview": f"{dept}拟招聘{role}，负责本岗位日常事务与部门协作。",
        "responsibilities": [f"负责{role}相关日常工作", "协助上级完成部门事务性工作"],
        "required_skills": [
            {"name": "办公软件", "weight": "必须", "description": "熟练使用 Word/Excel/PPT"},
            {"name": "沟通协调", "weight": "必须", "description": "良好的沟通表达能力"},
        ],
        "plus_skills": [],
        "qualifications": {"education": "大专及以上", "experience": "1年以上", "industry": "", "soft": "责任心强、细致"},
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
    # 前端 api/ai.js 实际调用的连字符命名
    "interview-questions": interview_qa,
}

# SSE 流式工作流（支持流式输出）
STREAM_WORKFLOWS = {
    "jd-generate": jd_generate,
    "match-score": match_score,
    "match": match_score,
}


# 支持走 Dify 编排的工作流（配置了 Dify 才尝试，未配/失败回退 DeepSeek）
DIFY_WORKFLOWS = {
    'jd-generate', 'match-score', 'match', 'interview-qa', 'interview-questions',
    'resume-parse', 'offer-email-generate',
}


def run_workflow(name: str, data: dict) -> dict:
    handler = WORKFLOWS.get(name)
    if not handler:
        raise ValueError(f"未知工作流: {name}")
    # Dify 优先（配置驱动）：配置了 Dify 且该工作流在 DIFY_WORKFLOWS 内，先走 Dify 编排；
    # 未配置 / 调用失败 / 无输出，一律回退本地 DeepSeek 直连。
    if name in DIFY_WORKFLOWS:
        try:
            from app.services import dify_client
            if dify_client.dify_configured():
                outputs = dify_client.run_workflow(name, data)
                if outputs:
                    outputs.setdefault('disclaimer', DISCLAIMER)
                    outputs['_engine'] = 'dify'
                    return outputs
        except Exception as e:
            log.warning('Dify 工作流 %s 失败，回退 DeepSeek: %s', name, e)
    return handler(data)
