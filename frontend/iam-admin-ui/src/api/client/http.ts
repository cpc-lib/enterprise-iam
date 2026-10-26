import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from './errors'
import type { ApiResponse } from '@/types/api'

export const AUTH_REFRESH_URL = '/api/v1/auth/refresh'
export const AUTH_LOGIN_DELIVERY_CONFIRM_URL = '/api/v1/auth/login-deliveries/confirm'
export const AUTH_PASSWORD_CHANGE_URL = '/api/v1/auth/password/change'

/** 认证凭据命令不做 401 静默重试，避免循环或重复提交敏感凭据。 */
const NO_RETRY_URLS = [
  '/api/v1/auth/login',
  AUTH_LOGIN_DELIVERY_CONFIRM_URL,
  AUTH_PASSWORD_CHANGE_URL,
  AUTH_REFRESH_URL,
]

type RetryableRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const http = axios.create({
  baseURL: '/',
  timeout: 15000,
  withCredentials: true,
})

function newRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `req-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

// 请求拦截：附加 Authorization / X-Tenant-Id / X-User-Id / X-Request-Id
http.interceptors.request.use((config) => {
  const { accessToken, user } = useAuthStore.getState()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  if (user?.tenantId) {
    config.headers['X-Tenant-Id'] = user.tenantId
  }
  if (user?.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  config.headers['X-Request-Id'] = newRequestId()
  return config
})

// 响应拦截：解包统一 envelope { success, code, data, traceId }
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    if (body && typeof body === 'object' && 'success' in body) {
      if (!body.success) {
        throw ApiError.fromEnvelope(body, response.status)
      }
      response.data = body.data
    }
    return response
  },
  async (error: AxiosError) => {
    const config = error.config as RetryableRequestConfig | undefined
    const status = error.response?.status
    const body = error.response?.data as Partial<ApiResponse<unknown>> | undefined

    const canRetry =
      status === 401 &&
      !!config &&
      !config._retry &&
      typeof config.url === 'string' &&
      !NO_RETRY_URLS.some((url) => config.url?.includes(url)) &&
      typeof body?.code === 'string' &&
      body.code.startsWith('IAM_AUTH')

    if (canRetry && config) {
      config._retry = true
      try {
        const accessToken = await refreshAccessToken()
        useAuthStore.getState().setAccessToken(accessToken)
        config.headers.Authorization = `Bearer ${accessToken}`
        return http.request(config)
      } catch {
        // 刷新失败：清空本地会话并回到登录页
        handleSessionExpired()
      }
    }
    throw ApiError.fromAxiosError(error)
  },
)

let refreshPromise: Promise<string> | null = null

/** 静默刷新（HttpOnly refresh cookie），并发 401 共享同一次刷新 */
function refreshAccessToken(): Promise<string> {
  refreshPromise ??= axios
    .post<ApiResponse<{ accessToken: string }>>(AUTH_REFRESH_URL, undefined, {
      withCredentials: true,
    })
    .then((res) => {
      const body = res.data
      if (!body?.success || !body.data?.accessToken) {
        throw new ApiError(body?.code ?? 'IAM_AUTH_REFRESH_FAILED', '会话刷新失败，请重新登录')
      }
      return body.data.accessToken
    })
    .finally(() => {
      refreshPromise = null
    })
  return refreshPromise
}

function handleSessionExpired(): void {
  useAuthStore.getState().clearAuth()
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

export default http
