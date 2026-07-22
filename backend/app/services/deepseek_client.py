"""DeepSeek API client — OpenAI-compatible interface.

Provides two primary functions:
    chat_completion(messages, temperature, max_tokens) → str
    chat_completion_json(messages, temperature) → dict

With error handling, logging, and graceful fallback.
"""

import json
import logging
import time
from typing import Optional

from openai import OpenAI

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Client initialisation (lazy, so import doesn't fail if config isn't ready)
# ---------------------------------------------------------------------------

_client: Optional[OpenAI] = None
_client_key: Optional[str] = None  # key used to build the cached client


def _resolve_api_key() -> str:
    """Resolve the DeepSeek API key.

    优先级:
        1. 环境变量 DEEPSEEK_API_KEY（.env）
        2. 数据库 t_hr_api_key（网页「API 密钥管理」配置，AES-256-GCM 加密存储）
    改动原因：支持在网页端配置 DeepSeek key，无需登录服务器改 .env 重启。
    """
    from config import Config
    api_key = Config.DEEPSEEK_API_KEY
    if not api_key:
        try:
            from app.services.config_service import get_decrypted_api_key
            api_key = get_decrypted_api_key('deepseek') or ''
        except Exception:
            api_key = ''
    return api_key


def _get_client() -> OpenAI:
    """Return (and cache) the OpenAI-compatible DeepSeek client.

    FIX: 缓存与 key 绑定——网页端更新 key 后，下一次调用会检测到 key 变化
    并重建 client，真正实现「线上配置无需重启」。key 为空时不缓存，
    避免空 key client 被永久固化。
    """
    global _client, _client_key
    from config import Config
    api_key = _resolve_api_key()
    if _client is not None and _client_key == api_key:
        return _client
    if not api_key:
        raise RuntimeError(
            'DeepSeek API key 未配置：请在 .env 设置 DEEPSEEK_API_KEY，'
            '或在网页端「招聘基础配置 → API 密钥管理」中配置 DeepSeek key'
        )
    _client = OpenAI(
        api_key=api_key,
        base_url=Config.DEEPSEEK_BASE_URL,
    )
    _client_key = api_key
    return _client


# ---------------------------------------------------------------------------
# Core helpers
# ---------------------------------------------------------------------------

def _get_model() -> str:
    from config import Config
    return Config.DEEPSEEK_MODEL


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def chat_completion(
    messages: list,
    temperature: float = 0.7,
    max_tokens: int = 2000,
    timeout: float = 60.0,
) -> str:
    """Send a chat-completion request to DeepSeek and return the text content.

    Args:
        messages: OpenAI-format message list [{"role":..., "content":...}].
        temperature: Sampling temperature (0-2).
        max_tokens: Maximum tokens in the response.
        timeout: Request timeout in seconds.

    Returns:
        The assistant's text response.

    Raises:
        RuntimeError: When the API call fails after all retries.
    """
    model = _get_model()
    client = _get_client()

    _log_request(model, messages, temperature, max_tokens)

    last_error = None
    for attempt in range(1, 4):  # up to 3 retries
        try:
            response = client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
                timeout=timeout,
            )
            content = response.choices[0].message.content or ""
            _log_response(model, content)
            return content

        except Exception as exc:
            last_error = exc
            _log_error(attempt, exc)
            if attempt < 3:
                time.sleep(min(2 ** attempt, 8))  # exponential backoff 2/4/8

    raise RuntimeError(
        f"DeepSeek API call failed after 3 attempts: {last_error}"
    )


def chat_completion_stream(
    messages: list,
    temperature: float = 0.7,
    max_tokens: int = 2000,
):
    """Stream tokens from DeepSeek API.

    Yields dicts: {'type':'token','content':str} or {'type':'error','message':str}

    Args:
        messages: OpenAI-format message list [{"role":..., "content":...}].
        temperature: Sampling temperature (0-2).
        max_tokens: Maximum tokens in the response.
    """
    model = _get_model()
    try:
        client = _get_client()
    except Exception as e:
        log.error("DeepSeek stream init failed: %s", e)
        yield {'type': 'error', 'message': str(e)}
        return

    _log_request(model, messages, temperature, max_tokens)

    try:
        response = client.chat.completions.create(
            model=model,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
            stream=True,
            timeout=120,
        )
        for chunk in response:
            delta = chunk.choices[0].delta if chunk.choices else None
            if delta and delta.content:
                yield {'type': 'token', 'content': delta.content}
    except Exception as e:
        log.error("DeepSeek stream error: %s", e)
        yield {'type': 'error', 'message': str(e)}


def chat_completion_json(
    messages: list,
    temperature: float = 0.3,
    max_tokens: int = 2000,
    timeout: float = 60.0,
) -> dict:
    """Send a chat-completion request and parse the response as JSON.

    The messages should instruct the model to return JSON.
    This function also appends a final user message to reinforce the JSON requirement.

    Returns:
        Parsed JSON dict.

    Raises:
        ValueError: When the response is not valid JSON.
        RuntimeError: When the API call fails.
    """
    # Append a firm JSON instruction as the last user message
    json_messages = list(messages)
    json_messages.append({
        "role": "user",
        "content": "请严格返回纯 JSON 格式，不要包含 markdown 代码块标记（```json），"
                   "直接输出 JSON 对象。确保 JSON 字段名使用英文，值使用中文。",
    })

    content = chat_completion(
        messages=json_messages,
        temperature=temperature,
        max_tokens=max_tokens,
        timeout=timeout,
    )

    return _parse_json_response(content)


def _parse_json_response(content: str) -> dict:
    """Parse a model response as JSON, stripping markdown fences if present."""
    text = content.strip()

    # Strip ```json / ``` fences
    if text.startswith("```"):
        # Remove opening fence line
        idx = text.find("\n")
        if idx != -1:
            text = text[idx + 1:]
        # Remove closing fence
        if text.endswith("```"):
            text = text[:-3]

    text = text.strip()

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        # Try to extract JSON object via brace matching
        extracted = _extract_braced_json(text)
        if extracted is not None:
            try:
                return json.loads(extracted)
            except json.JSONDecodeError:
                pass
        raise ValueError(
            f"DeepSeek response is not valid JSON. "
            f"Response (first 500 chars): {content[:500]}"
        )


def _extract_braced_json(text: str) -> Optional[str]:
    """Best-effort extract JSON object from text by finding outermost braces."""
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


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

def _log_request(model: str, messages: list, temperature: float, max_tokens: int):
    """Log a truncated summary of the API request."""
    summary = _summarise_messages(messages)
    log.info(
        "DeepSeek request model=%s temp=%.2f max_tokens=%d msgs=%s",
        model, temperature, max_tokens, summary,
    )


def _log_response(model: str, content: str):
    """Log a truncated summary of the API response."""
    preview = content.replace("\n", " ")[:200]
    log.info(
        "DeepSeek response model=%s chars=%d preview=%s",
        model, len(content), preview,
    )


def _log_error(attempt: int, exc: Exception):
    log.warning(
        "DeepSeek attempt %d/3 failed: %s: %s",
        attempt, type(exc).__name__, str(exc)[:300],
    )


def _summarise_messages(messages: list) -> str:
    """Produce a short summary of the message list for logging."""
    roles = []
    for m in messages:
        role = m.get("role", "?")
        content = str(m.get("content", ""))
        content_preview = content.replace("\n", " ")[:80]
        roles.append(f"{role}({content_preview}...)")
    return " | ".join(roles)
