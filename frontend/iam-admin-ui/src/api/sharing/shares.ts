import http from '@/api/client/http'
import { idempotencyKey } from '@/api/authorization/idempotency'
import type {
  CreateShareRequest,
  ReshareRequest,
  RevokeShareRequest,
  ShareResponse,
} from '@/types/sharing'

/** 创建资源实例分享：POST /api/v1/shares → 201 ShareResponse */
export async function createShare(req: CreateShareRequest): Promise<ShareResponse> {
  const { data } = await http.post<ShareResponse>('/api/v1/shares', req, {
    headers: { 'Idempotency-Key': idempotencyKey() },
  })
  return data
}

/** 撤销分享并提交 epoch 栅栏：POST /api/v1/shares/{shareId}/revoke → 200 ShareResponse */
export async function revokeShare(shareId: string, req: RevokeShareRequest): Promise<ShareResponse> {
  const { data } = await http.post<ShareResponse>(`/api/v1/shares/${shareId}/revoke`, req, {
    headers: { 'Idempotency-Key': idempotencyKey() },
  })
  return data
}

/** 续分享（创建受限子分享）：POST /api/v1/shares/{shareId}/reshare → 201 ShareResponse */
export async function reshare(shareId: string, req: ReshareRequest): Promise<ShareResponse> {
  const { data } = await http.post<ShareResponse>(`/api/v1/shares/${shareId}/reshare`, req, {
    headers: { 'Idempotency-Key': idempotencyKey() },
  })
  return data
}

/** 按 ID 查询分享（契约未提供列表接口）：GET /api/v1/shares/{shareId} */
export async function getShare(shareId: string): Promise<ShareResponse> {
  const { data } = await http.get<ShareResponse>(`/api/v1/shares/${shareId}`)
  return data
}
