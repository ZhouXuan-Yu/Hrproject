// useAppError.js — Centralized error handling with Chinese messages
// Maps HTTP codes to user-facing messages and actions

import { useToast } from './useToast.js'
import { clearAuth } from './useAuth.js'

const HTTP_ERROR_MAP = {
  401: { message: '登录已过期，请重新登录', action: 'redirect' },
  403: { message: '无权限访问', action: 'toast' },
  404: { message: '资源不存在', action: 'toast' },
  409: { message: '数据冲突，请刷新后重试', action: 'toast' },
  429: { message: '请求过于频繁，请稍后重试', action: 'toast' },
  500: { message: '服务器错误，已通知技术团队', action: 'toast' },
  502: { message: '网关错误，请稍后重试', action: 'toast' },
  503: { message: '服务暂不可用，请稍后重试', action: 'toast' },
}

export function useAppError() {
  const { toast } = useToast()

  /**
   * Centralized error handler for all API errors.
   * @param {Error} error - The error object from fetch/API call
   * @param {string} [context=''] - Optional context label for console logging
   */
  function handleError(error, context = '') {
    const status = error?.status || error?.response?.status || 0
    const code = error?.code || ''
    const message = error?.message || '未知错误'

    // 401 — redirect to login
    if (status === 401) {
      toast.error(HTTP_ERROR_MAP[401].message, 5000)
      localStorage.removeItem('hr_token')
      localStorage.removeItem('hr_session')
      // Use dynamic import to avoid circular dependency with router
      setTimeout(async () => {
        try {
          const { router } = await import('../router/index.js')
          router.push('/login')
        } catch (e) {
          window.location.href = '/login'
        }
      }, 1500)
      return
    }

    // Known HTTP status codes
    if (HTTP_ERROR_MAP[status]) {
      toast.error(HTTP_ERROR_MAP[status].message, 5000)
      return
    }

    // Network errors (fetch throws TypeError on network failure)
    if (code === 'NETWORK_ERROR' || message === 'Failed to fetch' || error instanceof TypeError) {
      toast.error('网络连接失败，请检查网络', 6000)
      return
    }

    // Timeout errors
    if (code === 'TIMEOUT' || message === 'The operation was aborted' || message.indexOf('aborted') >= 0) {
      toast.error('请求超时，请重试', 4000)
      return
    }

    // Unknown errors — include a short error reference code
    const errorId = Date.now().toString(36).toUpperCase()
    const displayMsg = message.length > 60 ? `未知错误 (${errorId})` : `${message} (${errorId})`
    toast.error(displayMsg, 5000)
    console.error(`[AppError/${errorId}]`, context || 'no context', error)
  }

  return { handleError }
}
