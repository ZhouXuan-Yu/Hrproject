"""DeepSeek API 客户端 — OpenAI 兼容接口。

提供 chat_completion / chat_completion_json / chat_completion_stream。
迁移自现有 Flask 后端 app/services/deepseek_client.py。
"""
import json
import logging
import time
from typing import Iterator, Optional

from openai import OpenAI

from app.core.config import settings

log = logging.getLogger(__name__)

_client: Optional[OpenAI] = None
_client_key: Optional[str] = None

DISCLAIMER = "此内容由AI生成，请人工审核确认后使用"


def _get_client() -> OpenAI:
    global _client, _client_key
    api_key = settings.DEEPSEEK_API_KEY
    if not api_key:
        raise RuntimeError('DeepSeek API key 未配置：请设置 DEEPSEEK_API_KEY 环境变量')
    if _client is not None and _client_key == api_key:
        return _client
    _client = OpenAI(api_key=api_key, base_url=settings.DEEPSEEK_BASE_URL)
    _client_key = api_key
    return _client


def chat_completion(messages: list, temperature: float = 0.7,
                    max_tokens: int = 2000, timeout: float = 60.0) -> str:
    """调用 DeepSeek chat completion，返回文本。"""
    model = settings.DEEPSEEK_MODEL
    client = _get_client()
    last_error = None
    for attempt in range(1, 4):  # 最多 3 次重试，指数退避
        try:
            response = client.chat.completions.create(
                model=model, messages=messages, temperature=temperature,
                max_tokens=max_tokens, timeout=timeout)
            return response.choices[0].message.content or ""
        except Exception as exc:
            last_error = exc
            log.warning("DeepSeek attempt %d/3 failed: %s", attempt, str(exc)[:300])
            if attempt < 3:
                time.sleep(min(2 ** attempt, 8))
    raise RuntimeError(f"DeepSeek API call failed after 3 attempts: {last_error}")


def chat_completion_json(messages: list, temperature: float = 0.3,
                         max_tokens: int = 2000, timeout: float = 60.0) -> dict:
    """调用 DeepSeek 并解析 JSON 返回。"""
    json_messages = list(messages)
    json_messages.append({
        "role": "user",
        "content": "请严格返回纯 JSON 格式，不要包含 markdown 代码块标记（```json），"
                   "直接输出 JSON 对象。确保 JSON 字段名使用英文，值使用中文。",
    })
    content = chat_completion(json_messages, temperature, max_tokens, timeout)
    return _parse_json_response(content)


def chat_completion_stream(messages: list, temperature: float = 0.7,
                           max_tokens: int = 2000) -> Iterator[dict]:
    """SSE 流式调用 DeepSeek，yield {type: token/error, content}。"""
    model = settings.DEEPSEEK_MODEL
    try:
        client = _get_client()
    except Exception as e:
        log.error("DeepSeek stream init failed: %s", e)
        yield {'type': 'error', 'message': str(e)}
        return
    try:
        response = client.chat.completions.create(
            model=model, messages=messages, temperature=temperature,
            max_tokens=max_tokens, stream=True, timeout=120)
        for chunk in response:
            delta = chunk.choices[0].delta if chunk.choices else None
            if delta and delta.content:
                yield {'type': 'token', 'content': delta.content}
    except Exception as e:
        log.error("DeepSeek stream error: %s", e)
        yield {'type': 'error', 'message': str(e)}


def _parse_json_response(content: str) -> dict:
    """解析模型 JSON 响应，剥离 markdown fence。"""
    text = content.strip()
    if text.startswith("```"):
        idx = text.find("\n")
        if idx != -1:
            text = text[idx + 1:]
        if text.endswith("```"):
            text = text[:-3]
    text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        extracted = _extract_braced_json(text)
        if extracted is not None:
            try:
                return json.loads(extracted)
            except json.JSONDecodeError:
                pass
        raise ValueError("DeepSeek response is not valid JSON")


def _extract_braced_json(text: str) -> Optional[str]:
    start = text.find("{")
    if start == -1:
        return None
    depth = 0
    for i in range(start, len(text)):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    return None
