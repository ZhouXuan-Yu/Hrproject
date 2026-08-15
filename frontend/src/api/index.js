// api/index.js — HTTP client for backend API
// Features: timeout, retry, dedup, caching, CSRF, loading tracking

const BASE = '/api'
const DEFAULT_TIMEOUT_MS = 30000
const MAX_RETRIES = 2
const CACHE_TTL_MS = 5 * 60 * 1000

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

/**
 * 写操作后按 URL 前缀精准失效 GET 缓存。
 * 映射表：写操作路径前缀 → 受影响列表缓存前缀。
 */
const MUTATION_CACHE_MAP = [
  { write: '/talent', read: ['/talent/list', '/talent/ingest-log'] },
  { write: '/demand', read: ['/demand/list', '/demand/'] },
  { write: '/interview', read: ['/interview/list', '/interview/alerts', '/interview/calendar'] },
  { write: '/config', read: ['/config/channels', '/config/email-accounts', '/config/notify-templates', '/config/knowledge-base', '/config/score-rules', '/config/role-permissions'] },
  { write: '/auth', read: ['/auth/users', '/auth/departments', '/auth/positions'] },
  { write: '/hire', read: ['/hire/offers', '/hire/entries'] },
  { write: '/dashboard', read: ['/dashboard'] },
]

function invalidateAfterMutation(method, path) {
  if (!['POST', 'PATCH', 'PUT', 'DELETE'].includes(method)) return
  for (const entry of MUTATION_CACHE_MAP) {
    if (path.startsWith(entry.write)) {
      invalidateCache(entry.read)
      return
    }
  }
  // 未匹配的写操作保守全清
  responseCache.clear()
}

/**
 * 按 URL 前缀精准失效 GET 缓存。
 * 写操作（POST/PATCH/PUT/DELETE）后只清除相关列表缓存，而非全表清空。
 * 也可由模块主动调用（如原生 fetch 上传后 invalidateCache('/talent/list')）。
 * @param {string|string[]} urlPrefixes - 单个或一组 URL 前缀（含 /api 前路径，如 '/talent/list'）
 */
export function invalidateCache(urlPrefixes) {
  const prefixes = Array.isArray(urlPrefixes) ? urlPrefixes : [urlPrefixes]
  for (const key of [...responseCache.keys()]) {
    if (prefixes.some(p => key.includes(p))) {
      responseCache.delete(key)
    }
  }
}

/**
 * 清除全部 GET 缓存（登录/登出时使用）。
 */
export function clearCache() {
  responseCache.clear()
}

// ── Core request ──────────────────────────────────────────────────────────
async function request(path, options = {}) {
  const url = `${BASE}${path}`
  const method = options.method || 'GET'
  const timeout = options.timeout || DEFAULT_TIMEOUT_MS
  const cacheKey = getCacheKey(url, { method, params: options.params || {} })

  // ── GET response cache check ──
  if (method === 'GET' && options.cache !== false) {
    const cached = getCached(cacheKey)
    if (cached) return cached
  }

  // ── Request deduplication ──
  if (method === 'GET' || method === 'POST') {
    const pending = pendingRequests.get(cacheKey)
    if (pending) return pending
  }

  // ── Prepare headers ──
  const headers = {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
    ...options.headers,
  }

  // Auth: httpOnly cookie is sent automatically by the browser.
  // For transitional compat, also send Authorization header if token is in localStorage.
  const token = localStorage.getItem('hr_token')
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
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
    delete config.silent

    return fetch(url, config)
      .finally(() => clearTimeout(timeoutId))
  }

  let lastError = null
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    incrementLoading()
    try {
      // Create a promise that this request will resolve to
      const requestPromise = (async () => {
        const resp = await doFetch(attempt)
        const json = await handleResponse(resp, method, path, options)

      // On successful mutation, invalidate related GET caches by URL prefix
      invalidateAfterMutation(method, path)

        return json
      })()

      // Store pending promise for dedup
      if (method === 'GET' || method === 'POST') {
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
async function handleResponse(resp, method, path, options = {}) {
  const silent = options.silent === true
  // 401 — redirect to login
  if (resp.status === 401) {
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    const err = new Error('请重新登录')
    err.code = 'UNAUTHORIZED'
    err.status = 401
    if (!silent) dispatchApiError(err)
    throw err
  }

  // 403 — forbidden
  if (resp.status === 403) {
    const err = new Error('无权限访问')
    err.code = 'FORBIDDEN'
    err.status = 403
    if (!silent) dispatchApiError(err)
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
      if (!silent) dispatchApiError(err)
      throw err
    }
    return { success: true, raw: true }
  }

  if (!resp.ok) {
    if (resp.status === 502) {
      const msg = json?.error?.message || json?.message || json?.error
        || '服务暂时不可用，请稍后重试'
      const err = new Error(msg)
      err.code = 'GATEWAY_ERROR'
      err.status = 502
      if (!silent) dispatchApiError(err)
      throw err
    }
    const message = json?.error?.message || json?.message || `请求失败 (${resp.status})`
    const code = json?.error?.code || 'ERROR'
    const err = new Error(message)
    err.code = code
    err.status = resp.status
    if (!silent) dispatchApiError(err)
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
