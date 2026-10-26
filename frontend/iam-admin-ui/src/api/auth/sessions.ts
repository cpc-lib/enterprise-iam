import http from '@/api/client/http'

export interface ActiveSession {
  id: string
  tenantId: string
  sessionId: string
  userId: string
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'
  deviceType: string
  lastAccessAt: string
  idleExpireAt: string
  absoluteExpireAt: string
  lastStrongAuthAt: string
  revokedAt?: string | null
  revokeReason?: string | null
  sessionVersion: number
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface ListSessionsParams {
  userId?: string
  status?: string
  page?: number
  pageSize?: number
}

export async function listSessions(params: ListSessionsParams = {}): Promise<PageResponse<ActiveSession>> {
  const { data } = await http.get<PageResponse<ActiveSession>>('/admin/v1/sessions', {
    params,
  })
  return data
}

export async function revokeSession(sessionId: string, reason?: string): Promise<void> {
  await http.post(`/admin/v1/sessions/${sessionId}/revoke`, { reason })
}
