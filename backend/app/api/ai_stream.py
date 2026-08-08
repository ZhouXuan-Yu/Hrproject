"""AI Streaming API: /api/ai/stream/*

SSE (Server-Sent Events) endpoints for streaming AI responses.
Supports jd-generate and match workflows via DeepSeek streaming,
with graceful fallback to the blocking ai.engine when streaming fails.
"""

import json
import logging
import re

from flask import Blueprint, request, Response, current_app

from app.utils.response import error
from app.api.ai import _resolve_candidate, _resolve_demand, DISCLAIMER, JD_GENERATE_SYSTEM, MATCH_SYSTEM

log = logging.getLogger(__name__)

ai_stream_bp = Blueprint('ai_stream', __name__)


# ===========================================================================
# Helpers
# ===========================================================================

def _sse_event(data: dict) -> str:
    """Format a dict as an SSE data line."""
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def _stream_error(message: str):
    """Yield an error event followed by done."""
    yield _sse_event({"type": "error", "message": message})
    yield _sse_event({"type": "done"})


# ===========================================================================
# POST /api/ai/stream/jd-generate
# ===========================================================================


@ai_stream_bp.route('/jd-generate', methods=['POST'])
def stream_jd_generate():
    """POST /api/ai/stream/jd-generate — stream JD generation via SSE."""
    body = request.get_json(silent=True) or {}

    position = body.get("position", "")
    department = body.get("department", "")
    level = body.get("level", "")
    salary = body.get("salary", "")
    workCity = body.get("workCity", "")
    eduMin = body.get("eduMin", "")
    expMin = body.get("expMin", "")
    headcount = body.get("headcount", 1)
    requirements = body.get("requirements", "")
    style = body.get("style", "standard")

    if not position or not department:
        return _stream_response(_stream_error("请提供岗位名称和部门"))

    # Load company knowledge context
    knowledge_context = {}
    try:
        from app.services.config_service import get_ai_knowledge_context
        knowledge_context = get_ai_knowledge_context()
    except Exception:
        pass

    user_input = json.dumps({
        "position": position,
        "department": department,
        "level": level,
        "salary": salary,
        "workCity": workCity,
        "eduMin": eduMin,
        "expMin": expMin,
        "headcount": headcount,
        "requirements": requirements,
        "style": style,
        "knowledge_base": knowledge_context,
    }, ensure_ascii=False)

    messages = [
        {"role": "system", "content": JD_GENERATE_SYSTEM},
        {"role": "user", "content": user_input},
    ]

    return _stream_response(_jd_generate_stream(messages, position, department))


def _jd_generate_stream(messages: list, position: str, department: str):
    """Generator: thinking → tokens → result → done for JD generation."""
    from app.services.deepseek_client import chat_completion_stream

    yield _sse_event({"type": "thinking", "content": "正在分析JD需求..."})

    full_text = ""
    error_occurred = False

    for chunk in chat_completion_stream(messages, temperature=0.5, max_tokens=2000):
        if chunk.get("type") == "error":
            yield _sse_event({"type": "error", "message": chunk["message"]})
            error_occurred = True
            break
        elif chunk.get("type") == "token":
            full_text += chunk["content"]
            yield _sse_event({"type": "token", "content": chunk["content"]})

    if error_occurred:
        yield _sse_event({"type": "done"})
        return

    # Try to parse the accumulated text as JSON for structured results
    parsed = _parse_accumulated_json(full_text)
    if parsed:
        for key in ["responsibilities", "required_skills", "plus_skills", "qualifications", "jd_text"]:
            if key in parsed:
                yield _sse_event({"type": "result", "key": key, "value": parsed[key]})

    yield _sse_event({"type": "done", "disclaimer": DISCLAIMER})


# ===========================================================================
# POST /api/ai/stream/match
# ===========================================================================


@ai_stream_bp.route('/match', methods=['POST'])
def stream_match():
    """POST /api/ai/stream/match — stream candidate-job matching via SSE."""
    body = request.get_json(silent=True) or {}

    candidate_id = body.get("candidate_id", "")
    demand_id = body.get("demand_id", "")

    if not candidate_id or not demand_id:
        return _stream_response(_stream_error("请提供候选人ID和岗位需求ID"))

    # Resolve candidate and demand data (reuse logic from ai.py via import)
    candidate_data = _resolve_candidate(candidate_id)
    demand_data = _resolve_demand(demand_id)

    user_input = json.dumps({
        "candidate": candidate_data,
        "demand": demand_data,
    }, ensure_ascii=False, default=str)

    messages = [
        {"role": "system", "content": MATCH_SYSTEM},
        {"role": "user", "content": user_input},
    ]

    return _stream_response(_match_stream(messages))


def _match_stream(messages: list):
    """Generator: thinking → tokens → result → done for candidate matching."""
    from app.services.deepseek_client import chat_completion_stream

    yield _sse_event({"type": "thinking", "content": "正在分析候选人匹配度..."})

    full_text = ""
    error_occurred = False

    for chunk in chat_completion_stream(messages, temperature=0.3, max_tokens=1500):
        if chunk.get("type") == "error":
            yield _sse_event({"type": "error", "message": chunk["message"]})
            error_occurred = True
            break
        elif chunk.get("type") == "token":
            full_text += chunk["content"]
            yield _sse_event({"type": "token", "content": chunk["content"]})

    if error_occurred:
        yield _sse_event({"type": "done"})
        return

    # Try to parse the accumulated text as JSON for structured results
    parsed = _parse_accumulated_json(full_text)
    if parsed:
        for key in ["profile_score", "match_score", "overall_score", "grade",
                     "reasons", "missing_skills", "strengths"]:
            if key in parsed:
                yield _sse_event({"type": "result", "key": key, "value": parsed[key]})

    yield _sse_event({"type": "done", "disclaimer": DISCLAIMER})


# ===========================================================================
# Shared helpers
# ===========================================================================

def _stream_response(generator):
    """Wrap a generator as a Flask SSE Response."""
    return Response(
        generator,
        content_type='text/event-stream',
        headers={
            'Cache-Control': 'no-cache',
            'X-Accel-Buffering': 'no',
            'Connection': 'keep-alive',
        },
    )


def _parse_accumulated_json(text: str) -> dict | None:
    """Best-effort parse accumulated streamed text as JSON, similar to
    _parse_json_response in deepseek_client.py."""
    if not text:
        return None

    cleaned = text.strip()

    # Strip ```json / ``` fences
    if cleaned.startswith("```"):
        idx = cleaned.find("\n")
        if idx != -1:
            cleaned = cleaned[idx + 1:]
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3]

    cleaned = cleaned.strip()

    # Tolerate common LLM JSON glitches: trailing commas before } or ]
    cleaned = re.sub(r",\s*([}\]])", r"\1", cleaned)

    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass

    # Extract JSON object via brace matching
    start = cleaned.find("{")
    if start == -1:
        return None

    depth = 0
    for i in range(start, len(cleaned)):
        c = cleaned[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                extracted = cleaned[start:i + 1]
                try:
                    return json.loads(extracted)
                except json.JSONDecodeError:
                    return None

    return None


# _resolve_candidate and _resolve_demand are imported from app.api.ai
