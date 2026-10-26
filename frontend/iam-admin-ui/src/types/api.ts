/** 后端统一响应包装：{ success, code, data, traceId }（失败时附带 message/details） */
export type TraceId = string

export type ApiErrorCode = string

export interface ApiResponse<T> {
  success: boolean
  code: ApiErrorCode
  data: T
  traceId: TraceId
  message?: string
}
