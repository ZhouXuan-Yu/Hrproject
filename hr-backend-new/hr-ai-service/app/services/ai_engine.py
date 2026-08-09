"""本地规则引擎 — DeepSeek 不可用时的 fallback。

迁移自现有 Flask 后端 app/services/ai_engine.py 的核心逻辑。
"""
import re
from typing import Optional

# 25 道预置面试题
QUESTION_POOL = [
    ("请介绍你在过往项目中最有代表性的一次经历，你扮演什么角色？", "experience"),
    ("你为什么离开上一家公司？", "motivation"),
    ("你对我们这个岗位的职责怎么理解？", "role"),
    ("请描述一次你主动推动问题解决的经历。", "problem_solving"),
    ("你如何看待加班和远程办公？", "work_style"),
    ("请分享你与团队冲突的经历及解决方式。", "teamwork"),
    ("未来 3 年你的职业规划是什么？", "career"),
    ("你最大的优点和缺点分别是什么？", "self_awareness"),
    ("如果入职后前三个月你希望达成的目标是什么？", "plan"),
    ("你如何处理优先级冲突的任务？", "priority"),
    ("请举例说明你的学习能力。", "learning"),
    ("你对薪酬的期望是多少？如何考虑的？", "salary"),
    ("请介绍你掌握的一项核心技能及其深度。", "skill"),
    ("你在压力最大的一次项目中是如何应对的？", "stress"),
    ("描述一次你失败的经历，你学到了什么？", "failure"),
    ("你如何评估自己的沟通能力？", "communication"),
    ("你对公司目前所处行业有什么了解？", "industry"),
    ("如果项目需求频繁变更，你会怎么办？", "change"),
    ("你更倾向于独立工作还是团队协作？", "work_style"),
    ("请介绍你最近学习的一项新技术。", "learning"),
    ("你为什么选择申请我们公司？", "motivation"),
    ("你如何在多项目并行时分配精力？", "priority"),
    ("描述一次你给团队带来正面影响的经历。", "teamwork"),
    ("你如何理解岗位所需的专业技能？", "skill"),
    ("如果你发现同事犯了错误，你会如何处理？", "integrity"),
]


def parse_resume(text: str) -> dict:
    """正则提取简历关键字段。"""
    return {
        "name": _extract_name(text),
        "phone": _extract_phone(text),
        "email": _extract_email(text),
        "education": _extract_education(text),
        "school": _extract_school(text),
        "work_years": _extract_work_years(text),
        "skills": _extract_skills(text),
    }


def match_job(candidate: dict, jd_text: str) -> dict:
    """基于规则的岗位匹配评分（技能 50% / 经验 30% / 学历 20%）。"""
    skill_keywords = _extract_skills(jd_text)
    skills = set(candidate.get("skills", []))
    matched = skills.intersection(skill_keywords)
    skill_score = 100.0 * len(matched) / max(len(skill_keywords), 1)
    exp_score = 50.0  # 经验维度默认 50
    work_years = candidate.get("work_years") or 0
    if work_years >= 5:
        exp_score = 90
    elif work_years >= 3:
        exp_score = 75
    elif work_years >= 1:
        exp_score = 60

    edu_score = _education_score(candidate.get("education", ""))

    total = round(skill_score * 0.5 + exp_score * 0.3 + edu_score * 0.2, 1)
    return {
        "score": total,
        "matchedSkills": list(matched),
        "missingSkills": list(set(skill_keywords) - skills),
        "dimensions": {
            "skill": round(skill_score, 1),
            "experience": round(exp_score, 1),
            "education": round(edu_score, 1),
        },
        "_fallback": True,
        "disclaimer": "此内容由AI生成，请人工审核确认后使用",
    }


def generate_questions(jd_text: str, resume: dict, round_num: int = 1) -> dict:
    """从题库中挑选 5 道面试题。"""
    selected = QUESTION_POOL[:5]
    questions = [{"question": q, "focus": focus} for q, focus in selected]
    return {
        "round": round_num,
        "questions": questions,
        "_fallback": True,
        "disclaimer": "此内容由AI生成，请人工审核确认后使用",
    }


# ── 正则提取工具 ────────────────────────────────────────────────────────

def _extract_name(text: str) -> Optional[str]:
    m = re.search(r'(姓名[:：]\s*)?([一-龥]{2,4})\s*[，,;；\n]', text)
    if m:
        name = m.group(2)
        if not any(k in name for k in ('大学', '公司', '有限', '集团', '技术', '科技')):
            return name
    return None


def _extract_phone(text: str) -> Optional[str]:
    m = re.search(r'(?<!\d)(1[3-9]\d{9})(?!\d)', text)
    return m.group(1) if m else None


def _extract_email(text: str) -> Optional[str]:
    m = re.search(r'[\w.-]+@[\w.-]+\.\w+', text)
    return m.group(0) if m else None


def _extract_education(text: str) -> Optional[str]:
    for level in ('博士', '硕士', '本科', '大专', '高中'):
        if level in text:
            return level
    return None


def _extract_school(text: str) -> Optional[str]:
    m = re.search(r'((?:清华|北大|复旦|上交|浙大|南大|中大|华科|武大|西交|哈工大)[一-龥]*(?:大学|学院))', text)
    return m.group(1) if m else None


def _extract_work_years(text: str) -> int:
    m = re.search(r'(\d+)\s*年.*?(?:工作经验|工作经历|从业)', text)
    if m:
        return int(m.group(1))
    return 0


def _extract_skills(text: str) -> list:
    common = ('Java', 'Python', 'Spring', 'MySQL', 'Redis', 'Kafka', 'Vue', 'React',
              'Go', 'C++', 'JavaScript', 'TypeScript', 'Linux', 'Docker', 'K8s',
              'Flask', 'Django', 'FastAPI', 'Oracle', 'MongoDB', 'Hadoop',
              'Nginx', 'Git', 'CI/CD', '数据挖掘', '机器学习', '深度学习', 'AI')
    found = []
    for s in common:
        if s.lower() in text.lower():
            found.append(s)
    return found


def _education_score(education: Optional[str]) -> float:
    mapping = {'博士': 100, '硕士': 85, '本科': 70, '大专': 50, '高中': 30}
    return mapping.get(education, 40)
