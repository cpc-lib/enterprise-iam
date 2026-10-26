import http from '@/api/client/http'
import type { CreatePositionRequest, PositionItem, UpdatePositionRequest } from '@/types/organization'
import { newIdempotencyKey } from '@/api/identity/idempotency'

/** 查询岗位列表（GET /admin/v1/positions） */
export async function listPositions(tenantId: string, status?: string): Promise<PositionItem[]> {
  const { data } = await http.get<PositionItem[]>('/admin/v1/positions', {
    params: { tenantId, status },
  })
  return data
}

/** 创建岗位（POST /admin/v1/positions → 201） */
export async function createPosition(req: CreatePositionRequest): Promise<PositionItem> {
  const { data } = await http.post<PositionItem>('/admin/v1/positions', req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 查询岗位详情（GET /admin/v1/positions/{positionId}） */
export async function getPositionDetail(positionId: string, tenantId: string): Promise<PositionItem> {
  const { data } = await http.get<PositionItem>(`/admin/v1/positions/${positionId}`, {
    params: { tenantId },
  })
  return data
}

/** 更新岗位（PUT /admin/v1/positions/{positionId}） */
export async function updatePosition(
  positionId: string,
  req: UpdatePositionRequest,
): Promise<PositionItem> {
  const { data } = await http.put<PositionItem>(`/admin/v1/positions/${positionId}`, req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 删除岗位（DELETE /admin/v1/positions/{positionId}） */
export async function deletePosition(positionId: string, tenantId: string): Promise<void> {
  await http.delete(`/admin/v1/positions/${positionId}`, {
    params: { tenantId },
  })
}

/** 启用岗位（POST /admin/v1/positions/{positionId}/enable） */
export async function enablePosition(positionId: string, tenantId: string): Promise<PositionItem> {
  const { data } = await http.post<PositionItem>(`/admin/v1/positions/${positionId}/enable`, null, {
    params: { tenantId },
  })
  return data
}

/** 停用岗位（POST /admin/v1/positions/{positionId}/disable） */
export async function disablePosition(positionId: string, tenantId: string): Promise<PositionItem> {
  const { data } = await http.post<PositionItem>(`/admin/v1/positions/${positionId}/disable`, null, {
    params: { tenantId },
  })
  return data
}
