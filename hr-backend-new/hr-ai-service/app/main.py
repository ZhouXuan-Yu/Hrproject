"""AI 服务 FastAPI 入口。"""
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import router as ai_router
from app.core.config import settings

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s | %(message)s",
)

app = FastAPI(
    title="智能招聘 AI 服务",
    description="DeepSeek 驱动的 AI 工作流（JD 生成、简历解析、人岗匹配等）",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "Accept", "X-Internal-AI-Token"],
)

app.include_router(ai_router)


@app.get("/")
def root():
    return {"service": "hr-ai-service", "status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host=settings.HOST, port=settings.PORT, reload=True)
