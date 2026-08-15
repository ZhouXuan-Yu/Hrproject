"""Dify 工作流编排客户端 — 配置驱动，未配置/调用失败时由上层回退 DeepSeek。

对齐 Dify 服务端 API：POST {DIFY_BASE_URL}/workflows/run
  请求头 Authorization: Bearer <app-api-key>（请求级 key 优先，其次 .env）
  请求体 {"inputs": {...}, "response_mode": "blocking", "user": "hr-recruit"}
"""
import contextvars
import json
import logging
import urllib.error
import urllib.request

from app.core.config import settings

log = logging.getLogger(__name__)

# 请求级 Dify key（由 routes 从请求头 X-Dify-Key 注入，优先于 .env）
_request_dify_key: contextvars.ContextVar[str] = contextvars.ContextVar('request_dify_key', default='')


def set_request_dify_key(key: str) -> None:
    """设置当前请求使用的 Dify key（空值忽略，回落到 .env）。"""
    if key:
        _request_dify_key.set(key)


def dify_configured() -> bool:
    """是否已配置 Dify（请求级 key 或 .env）。"""
    return bool(_request_dify_key.get() or settings.DIFY_API_KEY)


def _api_key() -> str:
    return _request_dify_key.get() or settings.DIFY_API_KEY


def run_workflow(workflow: str, inputs: dict) -> dict:
    """调用 Dify 工作流（阻塞）。成功返回 outputs dict，失败抛异常。

    inputs 直接透传请求体（jd-generate 传 position/department 等，
    match 传 jd/candidate 等）；Dify 应用的 start 节点输入变量名需与之对应。
    """
    api_key = _api_key()
    if not api_key:
        raise RuntimeError('Dify API key 未配置')

    base = settings.DIFY_BASE_URL.rstrip('/')
    payload = json.dumps({
        'inputs': inputs,
        'response_mode': 'blocking',
        'user': 'hr-recruit',
    }, ensure_ascii=False).encode('utf-8')

    req = urllib.request.Request(
        base + '/workflows/run',
        data=payload,
        method='POST',
        headers={
            'Authorization': 'Bearer ' + api_key,
            'Content-Type': 'application/json',
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        detail = ''
        try:
            detail = e.read().decode('utf-8', 'ignore')[:300]
        except Exception:
            pass
        raise RuntimeError('Dify HTTP %s: %s' % (e.code, detail)) from e
    except Exception as e:
        raise RuntimeError('Dify 请求失败: %s' % e) from e

    data = body.get('data') or {}
    if data.get('status') not in ('succeeded', 'success'):
        err = data.get('error') or body.get('message') or 'Dify 工作流执行失败'
        raise RuntimeError(str(err))
    outputs = data.get('outputs') or {}
    if not outputs:
        raise RuntimeError('Dify 工作流未返回 outputs')
    return outputs


def test_connection() -> dict:
    """连通性测试：GET {DIFY_BASE_URL}/info，返回 {ok, message, source}。"""
    api_key = _api_key()
    result = {'ok': False, 'message': '', 'source': None}
    if _request_dify_key.get():
        result['source'] = 'request'
    elif settings.DIFY_API_KEY:
        result['source'] = 'env'
    if not api_key:
        result['message'] = '未配置 Dify API Key'
        return result

    req = urllib.request.Request(
        settings.DIFY_BASE_URL.rstrip('/') + '/info',
        method='GET',
        headers={'Authorization': 'Bearer ' + api_key},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status == 200:
                result['ok'] = True
                result['message'] = '连接成功，Dify 应用可用'
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            result['message'] = 'Dify API Key 无效（HTTP %d）' % e.code
        elif e.code == 404:
            result['message'] = 'Dify 应用不存在或 Key 类型不匹配（HTTP 404）'
        else:
            result['message'] = 'Dify 返回 HTTP %d' % e.code
    except Exception as e:
        result['message'] = '网络请求失败: %s' % e.__class__.__name__
    return result
