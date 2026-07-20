// api/index.js — HTTP client for backend API
// Features: timeout, retry, dedup, caching, CSRF, loading tracking

const BASE = '/api'
const DEFAULT_TIMEOUT_MS = 30000
const MAX_RETRIES = 2
const CACHE_TTL_MS = 30000

// ── In-flight dedup map ───────────────────────────────────────────────────
const pendingRequests = new Map()

// ── GET response cache ────────────────────────────────────────────────────
const responseCache = new Map()

// ── Loading state tracking ────────────────────────────────────────────────
let _inFlightCount = 0
const _loadingListeners = new Set()

function notifyLoadingChange() {
  _loadingListeners.forEach(fn => fn(_inFlightCount))
}

/**
 * Subscribe to loading state changes.
 * @param {Function} listener - receives current in-flight count
 * @returns {Function} unsubscribe function
 */
export function onLoadingChange(listener) {
  _loadingListeners.add(listener)
  return () => _loadingListeners.delete(listener)
}

/**
 * Check if there are any in-flight requests.
 */
export function isLoading() {
  return _inFlightCount > 0
}

function incrementLoading() {
  _inFlightCount++
  if (_inFlightCount === 1) notifyLoadingChange()
}

function decrementLoading() {
  _inFlightCount = Math.max(0, _inFlightCount - 1)
  if (_inFlightCount === 0) notifyLoadingChange()
}

// ── Cache helpers ─────────────────────────────────────────────────────────
function getCacheKey(url, config) {
  const method = config?.method || 'GET'
  const params = config?.params ? JSON.stringify(config.params) : ''
  return `${method}:${url}:${params}`
}

function getCached(key) {
  const entry = responseCache.get(key)
  if (!entry) return null
  if (Date.now() - entry.timestamp > CACHE_TTL_MS) {
    responseCache.delete(key)
    return null
  }
  return entry.data
}

function setCache(key, data) {
  responseCache.set(key, { data, timestamp: Date.now() })
  // Evict old entries if cache grows too large
  if (responseCache.size > 100) {
    const oldest = responseCache.entries().next().value
    if (oldest) responseCache.delete(oldest[0])
  }
}

function invalidateCache(method, url) {
  if (method === 'POST' || method === 'PATCH' || method === 'PUT' || method === 'DELETE') {
    // Invalidate all GET cache entries (conservative: clear all)
    responseCache.clear()
  }
}

// ── Core request ──────────────────────────────────────────────────────────
async function request(path, options = {}) {
  const url = `${BASE}${path}`
  const method = options.method || 'GET'
  const timeout = options.timeout || DEFAULT_TIMEOUT_MS
  const cacheKey = getCacheKey(url, { method, params: options.params })

  // ── GET response cache check ──
  if (method === 'GET' && options.cache !== false) {
    const cached = getCached(cacheKey)
    if (cached) return cached
  }

  // ── POST request deduplication ──
  if (method === 'POST') {
    const pending = pendingRequests.get(cacheKey)
    if (pending) return pending
  }

  // ── Prepare headers ──
  const headers = {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
    ...options.headers,
  }

  // Bearer token auth
  const token = localStorage.getItem('hr_token')
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  // In dev mode, forward the current role as query param for auth
  const role = localStorage.getItem('hr_role')
  let finalUrl = url
  if (role && !url.includes('role=')) {
    const sep = url.includes('?') ? '&' : '?'
    finalUrl = `${url}${sep}role=${role}`
  }

  // ── Execute with retry ──
  const doFetch = (retryCount) => {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), timeout)

    const config = {
      headers,
      method,
      signal: controller.signal,
      ...options,
    }
    // Remove non-fetch properties
    delete config.timeout
    delete config.params

    return fetch(finalUrl, config)
      .finally(() => clearTimeout(timeoutId))
  }

  let lastError = null
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    incrementLoading()
    try {
      // Create a promise that this request will resolve to
      const requestPromise = (async () => {
        const resp = await doFetch(attempt)
        const json = await handleResponse(resp, method, path)

        // On successful mutation, invalidate cache
        invalidateCache(method, path)

        return json
      })()

      // Store pending promise for dedup
      if (method === 'POST') {
        pendingRequests.set(cacheKey, requestPromise)
        requestPromise.finally(() => pendingRequests.delete(cacheKey))
      }

      const result = await requestPromise

      // Cache GET responses
      if (method === 'GET' && options.cache !== false) {
        setCache(cacheKey, result)
      }

      return result
    } catch (err) {
      lastError = err
      const status = err.status || 0

      // Retry on 5xx / network errors / timeout, but not on 4xx or 401/403
      const isRetryable =
        (status >= 500 && status < 600) ||
        status === 0 ||
        err.code === 'NETWORK_ERROR' ||
        err.code === 'TIMEOUT' ||
        err.message === 'Failed to fetch' ||
        err.name === 'AbortError'

      if (isRetryable && attempt < MAX_RETRIES) {
        // Exponential backoff: 500ms, 1500ms
        const delay = 500 * Math.pow(3, attempt)
        await new Promise(r => setTimeout(r, delay))
        continue
      }

      // Non-retryable or exhausted retries — rethrow
      throw err
    } finally {
      decrementLoading()
    }
  }

  throw lastError || new Error('请求失败')
}

// ── Response handler ──────────────────────────────────────────────────────
async function handleResponse(resp, method, path) {
  // 401 / 403 — clear stale token on real auth error
  if ((resp.status === 401 || resp.status === 403) && localStorage.getItem('hr_token')) {
    if (resp.status === 401) {
      localStorage.removeItem('hr_token')
    }
    let message = resp.status === 401 ? '请重新登录' : '无权限访问'
    const err = new Error(message)
    err.code = resp.status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN'
    err.status = resp.status
    dispatchApiError(err)
    throw err
  }

  // Handle 204 No Content
  if (resp.status === 204) {
    return { success: true }
  }

  let json
  try {
    json = await resp.json()
  } catch (e) {
    // Response is not JSON
    if (!resp.ok) {
      const err = new Error(`请求失败 (${resp.status})`)
      err.code = 'PARSE_ERROR'
      err.status = resp.status
      dispatchApiError(err)
      throw err
    }
    return { success: true, raw: true }
  }

  if (!resp.ok) {
    // 502 from boss-cli — extract backend error message
    if (resp.status === 502) {
      const msg = json?.error?.message || json?.message || json?.error || '服务暂不可用，请检查 boss-cli 安装状态'
      const err = new Error(msg)
      err.code = 'BOSS_UNAVAILABLE'
      err.status = 502
      dispatchApiError(err)
      throw err
    }
    const message = json?.error?.message || json?.message || `请求失败 (${resp.status})`
    const code = json?.error?.code || 'ERROR'
    const err = new Error(message)
    err.code = code
    err.status = resp.status
    dispatchApiError(err)
    throw err
  }

  return json
}

// ── Global error event dispatch ───────────────────────────────────────────
function dispatchApiError(err) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('api:error', {
      detail: { message: err.message, code: err.code, status: err.status },
    }))
  }
}

// ── Public API ────────────────────────────────────────────────────────────
export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, data, options) => request(path, { ...options, method: 'POST', body: JSON.stringify(data) }),
  patch: (path, data, options) => request(path, { ...options, method: 'PATCH', body: JSON.stringify(data) }),
  put: (path, data, options) => request(path, { ...options, method: 'PUT', body: JSON.stringify(data) }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
}

export default api
