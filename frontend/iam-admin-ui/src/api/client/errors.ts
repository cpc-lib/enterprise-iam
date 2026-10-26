import type { AxiosError } from 'axios'
import type { ApiErrorCode, ApiResponse, TraceId } from '@/types/api'

/** 常见错误码的中文文案（未命中时回退服务端 message） */
const ERROR_MESSAGES: Record<string, string> = {
  IAM_AUTH_INVALID_CREDENTIALS: '用户名或密码不正确',
  IAM_AUTH_TOKEN_EXPIRED: '登录已过期，请重新登录',
  IAM_AUTH_SESSION_EXPIRED: '会话已失效，请重新登录',
  IAM_AUTH_UNAUTHENTICATED: '请先登录',
  IAM_AUTH_ACCESS_DENIED: '没有权限执行此操作',
  IAM_USER_DISABLED: '账号已被禁用，请联系管理员',
  IAM_TENANT_NOT_FOUND: '租户不存在',
  IAM_TENANT_DISABLED: '租户已被停用，请联系管理员',
  IAM_RATE_LIMIT_EXCEEDED: '操作过于频繁，请稍后再试',
  IAM_VALIDATION_FAILED: '请求参数有误，请检查后重试',
  IAM_NOT_FOUND: '请求的资源不存在',
  IAM_CONFLICT: '数据已变更，请刷新后重试',
}

/** 统一的业务/网络错误：携带 code / traceId / status，UI 层用 resolveErrorMessage 转成文案 */
export class ApiError extends Error {
  readonly code: ApiErrorCode
  readonly traceId?: TraceId
  readonly status?: number

  constructor(
    code: ApiErrorCode,
    message: string,
    options?: { traceId?: TraceId; status?: number },
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = options?.traceId
    this.status = options?.status
  }

  static fromEnvelope(body: Partial<ApiResponse<unknown>>, status?: number): ApiError {
    const code = body.code ?? 'UNKNOWN'
    const fallback = ERROR_MESSAGES[code] ?? `请求失败（${code}）`
    return new ApiError(code, body.message || fallback, {
      traceId: body.traceId,
      status,
    })
  }

  static fromAxiosError(error: AxiosError): ApiError {
    const status = error.response?.status
    const body = error.response?.data
    if (body && typeof body === 'object' && 'code' in body) {
      return ApiError.fromEnvelope(body as Partial<ApiResponse<unknown>>, status)
    }
    if (error.code === 'ECONNABORTED') {
      return new ApiError('REQUEST_TIMEOUT', '请求超时，请稍后重试', { status })
    }
    if (!error.response) {
      return new ApiError('NETWORK_ERROR', '网络异常，请检查网络后重试')
    }
    return new ApiError(`HTTP_${status ?? 'UNKNOWN'}`, `请求失败（${status ?? '未知错误'}）`, {
      status,
    })
  }
}

/** 将任意异常转换为用户可读的中文文案 */
export function resolveErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return ERROR_MESSAGES[error.code] ?? error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '请求失败，请稍后重试'
}
