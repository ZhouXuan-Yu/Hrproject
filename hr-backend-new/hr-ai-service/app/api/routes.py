"""AI 服务 API 路由。"""
import json
import logging
from typing import Optional

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.services.deepseek_client import chat_completion_stream, set_request_api_key
from app.services.dify_client import set_request_dify_key
from app.services.workflow_engine import (STREAM_WORKFLOWS, WORKFLOWS,
                                          run_workflow)

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/ai")


class WorkflowRequest(BaseModel):
    """通用工作流请求体。"""
    role: Optional[str] = None
    position: Optional[str] = None
    dept: Optional[str] = None
    department: Optional[str] = None
    text: Optional[str] = None
    content: Optional[str] = None
    jd: Optional[str] = None
    jobDescription: Optional[str] = None
    candidate: Optional[dict] = None
    resume: Optional[dict] = None
    query: Optional[str] = None
    keyword: Optional[str] = None
    offer: Optional[dict] = None

    def to_dict(self) -> dict:
        return self.model_dump(exclude_none=True)


@router.get("/capabilities")
def capabilities():
    """返回 AI 能力清单。"""
    return {
        "data": [
            {"name": "jd-generate", "label": "JD 生成", "enabled": True},
            {"name": "resume-parse", "label": "简历解析", "enabled": True},
            {"name": "match-score", "label": "人岗匹配", "enabled": True},
            {"name": "interview-qa", "label": "面试题生成", "enabled": True},
            {"name": "talent-search", "label": "人才搜索", "enabled": True},
            {"name": "offer-email-generate", "label": "Offer 邮件生成", "enabled": True},
        ],
        "message": "ok",
    }


@router.post("/run/{workflow}")
def run(workflow: str, req: WorkflowRequest, request: Request):
    """执行指定 AI 工作流。"""
    set_request_api_key(request.headers.get("X-DeepSeek-Key", ""))
    set_request_dify_key(request.headers.get("X-Dify-Key", ""))
    if workflow not in WORKFLOWS:
        raise HTTPException(status_code=400, detail={"code": "UNKNOWN_WORKFLOW", "message": f"未知工作流: {workflow}"})
    try:
        result = run_workflow(workflow, req.to_dict())
        return {"data": result, "message": "ok"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail={"code": "UNKNOWN_WORKFLOW", "message": str(e)})
    except Exception as e:
        log.exception("Workflow %s failed", workflow)
        raise HTTPException(status_code=500, detail={"code": "AI_ERROR", "message": str(e)})


@router.post("/stream/{workflow}")
async def stream(workflow: str, req: WorkflowRequest, request: Request):
    """SSE 流式执行工作流。"""
    set_request_api_key(request.headers.get("X-DeepSeek-Key", ""))
    set_request_dify_key(request.headers.get("X-Dify-Key", ""))
    if workflow not in STREAM_WORKFLOWS:
        raise HTTPException(status_code=400, detail={"code": "UNKNOWN_WORKFLOW", "message": f"不支持流式的工作流: {workflow}"})

    async def event_stream():
        yield "data: " + json.dumps({"type": "thinking", "content": "正在思考..."}) + "\n\n"
        messages = _build_stream_messages(workflow, req.to_dict())
        try:
            for chunk in chat_completion_stream(messages):
                yield "data: " + json.dumps(chunk) + "\n\n"
            yield "data: " + json.dumps({"type": "done", "content": ""}) + "\n\n"
        except Exception as e:
            yield "data: " + json.dumps({"type": "error", "message": str(e)}) + "\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.get("/health")
def health():
    from app.core.config import settings
    return {
        "status": "ok",
        "deepseek_configured": bool(settings.DEEPSEEK_API_KEY),
    }


def _build_stream_messages(workflow: str, data: dict) -> list:
    """为流式工作流构造 prompt。"""
    from app.services import workflow_engine
    if workflow == "jd-generate":
        role = data.get('role') or data.get('position') or ''
        dept = data.get('dept') or data.get('department') or ''
        return [
            {"role": "system", "content": workflow_engine.JD_GENERATE_STREAM_SYSTEM},
            {"role": "user", "content": f"岗位名称：{role}，部门：{dept}。请生成完整 JD。"},
        ]
    if workflow in ("match-score", "match"):
        jd = data.get('jd') or ''
        candidate = data.get('candidate') or {}
        text = f"岗位描述：{jd}\n候选人：{candidate}"
        return [
            {"role": "system", "content": workflow_engine.MATCH_SYSTEM},
            {"role": "user", "content": text},
        ]
    return [{"role": "user", "content": str(data)}]
