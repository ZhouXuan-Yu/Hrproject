"""Redis-backed application cache with safe local fallback.

The cache is intentionally optional: when Redis is unavailable, callers still
execute the loader and the application behaves exactly like a DB-only runtime.
"""
import hashlib
import json
import logging
import time
from contextlib import contextmanager

import redis
from flask import current_app, g

log = logging.getLogger(__name__)

_client = None
_client_url = None
_unavailable_until = 0
_UNAVAILABLE_BACKOFF_SECONDS = 5


def _enabled():
    try:
        return bool(current_app.config.get('CACHE_ENABLED', True))
    except RuntimeError:
        return False


def get_client():
    """Return a Redis client or None when caching should be bypassed."""
    global _client, _client_url, _unavailable_until
    if not _enabled():
        return None
    now = time.time()
    if now < _unavailable_until:
        return None
    url = (
        current_app.config.get('REDIS_CACHE_URL')
        or current_app.config.get('REDIS_URL')
        or current_app.config.get('CELERY_BROKER_URL')
    )
    if not url:
        return None
    if _client is None or _client_url != url:
        _client = redis.from_url(
            url,
            decode_responses=True,
            socket_connect_timeout=0.25,
            socket_timeout=0.5,
            health_check_interval=30,
        )
        _client_url = url
    return _client


def _mark_unavailable(exc):
    global _unavailable_until
    _unavailable_until = time.time() + _UNAVAILABLE_BACKOFF_SECONDS
    log.debug("Redis cache temporarily unavailable: %s", exc)


def cache_status():
    """Return health information for /api/health."""
    client = get_client()
    if not client:
        return {'enabled': _enabled(), 'connected': False}
    try:
        client.ping()
        return {'enabled': True, 'connected': True}
    except Exception as exc:
        _mark_unavailable(exc)
        return {'enabled': True, 'connected': False, 'error': str(exc)}


def _stable_params(params):
    if not params:
        return {}
    if hasattr(params, 'lists'):
        return {k: list(v) for k, v in sorted(params.lists())}
    return {str(k): params[k] for k in sorted(params)}


def build_key(namespace, params=None, vary=None):
    """Build a tenant/role-aware stable cache key."""
    tenant_id = getattr(g, 'tenant_id', 1)
    role = getattr(g, 'current_role', None) or ''
    payload = {
        'tenant': tenant_id,
        'role': role,
        'params': _stable_params(params),
        'vary': vary or {},
    }
    digest = hashlib.sha256(
        json.dumps(payload, sort_keys=True, ensure_ascii=False, default=str).encode('utf-8')
    ).hexdigest()[:32]
    return f"hrcache:v1:{namespace}:{digest}"


def cached(namespace, params, loader, ttl=None, vary=None):
    """Read-through cache wrapper.

    Args:
        namespace: Prefix such as ``interview:list``.
        params: Request args or a plain dict.
        loader: Zero-arg callable that computes the value on cache miss.
        ttl: Seconds. Defaults to CACHE_DEFAULT_TTL.
        vary: Additional key dimensions not present in params.
    """
    if ttl is None:
        ttl = int(current_app.config.get('CACHE_DEFAULT_TTL', 60))
    key = build_key(namespace, params, vary=vary)
    client = get_client()
    if client:
        try:
            raw = client.get(key)
            if raw is not None:
                return json.loads(raw)
        except Exception as exc:
            _mark_unavailable(exc)

    value = loader()
    client = get_client()
    if client:
        try:
            client.setex(key, int(ttl), json.dumps(value, ensure_ascii=False, default=str))
        except Exception as exc:
            _mark_unavailable(exc)
    return value


def invalidate_namespace(namespace):
    """Delete every cache key under one namespace prefix."""
    client = get_client()
    if not client:
        return 0
    pattern = f"hrcache:v1:{namespace}:*"
    deleted = 0
    try:
        keys = list(client.scan_iter(match=pattern, count=200))
        if keys:
            deleted = int(client.delete(*keys))
    except Exception as exc:
        _mark_unavailable(exc)
    return deleted


def invalidate_many(*namespaces):
    total = 0
    for namespace in namespaces:
        total += invalidate_namespace(namespace)
    return total


@contextmanager
def lock(name, ttl=10):
    """Best-effort Redis lock; yields True when acquired or Redis is absent."""
    client = get_client()
    if not client:
        yield True
        return
    key = f"hrlock:v1:{name}"
    acquired = False
    try:
        acquired = bool(client.set(key, '1', nx=True, ex=max(1, int(ttl))))
        yield acquired
    except Exception as exc:
        _mark_unavailable(exc)
        yield True
    finally:
        if acquired:
            try:
                client.delete(key)
            except Exception as exc:
                _mark_unavailable(exc)
