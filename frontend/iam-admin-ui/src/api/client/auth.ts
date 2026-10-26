import http, { AUTH_LOGIN_DELIVERY_CONFIRM_URL, AUTH_PASSWORD_CHANGE_URL } from './http'

export type IdentityType = 'USERNAME' | 'EMAIL' | 'PHONE'

export interface LoginParams {
  tenantCode: string
  identityType: IdentityType
  identity: string
  password: string
}

export interface LoginData {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  sessionId: string
  userId: string
  tenantId: string
}

export interface RefreshData {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  sessionId: string
}

export interface ChangePasswordParams {
  currentPassword: string
  newPassword: string
}

/** 账号密码登录（envelope 已由拦截器解包，以下函数直接返回 data） */
export async function login(params: LoginParams): Promise<LoginData> {
  const { data } = await http.post<LoginData>('/api/v1/auth/login', params)
  return data
}

/** 使用登录响应设置的 HttpOnly refresh cookie 确认会话已交付。 */
export async function confirmLoginDelivery(sessionId: string, tenantId: string): Promise<void> {
  await http.post(
    AUTH_LOGIN_DELIVERY_CONFIRM_URL,
    { sessionId },
    {
      headers: {
        'X-Tenant-Id': tenantId,
        'Idempotency-Key': newIdempotencyKey(),
      },
    },
  )
}

/** 使用 HttpOnly refresh cookie 换取新的 accessToken（响应同时轮换 cookie） */
export async function refresh(): Promise<RefreshData> {
  const { data } = await http.post<RefreshData>('/api/v1/auth/refresh')
  return data
}

/** 重新认证后修改密码；服务端保留当前会话并撤销其他会话。 */
export async function changePassword(params: ChangePasswordParams): Promise<void> {
  await http.post(
    AUTH_PASSWORD_CHANGE_URL,
    {
      currentPassword: params.currentPassword.normalize('NFC'),
      newPassword: params.newPassword.normalize('NFC'),
    },
    {
      headers: {
        'Idempotency-Key': newIdempotencyKey(),
      },
    },
  )
}

/** 吊销当前会话（服务端清除 refresh cookie） */
export async function logout(): Promise<void> {
  await http.post('/api/v1/auth/logout')
}

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}${Math.random()
    .toString(36)
    .slice(2)}`
}
