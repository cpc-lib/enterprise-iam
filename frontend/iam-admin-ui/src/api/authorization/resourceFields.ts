import http from '@/api/client/http'
import type { EntityResponse, ReplaceResourceFieldsRequest } from '@/types/authorization'
import { idempotencyKey } from './idempotency'

/** 整体替换资源字段元数据：PUT /admin/v1/resources/{resourceId}/fields → 200 EntityResponse */
export async function replaceResourceFields(
  resourceId: string,
  req: ReplaceResourceFieldsRequest,
): Promise<EntityResponse> {
  const { data } = await http.put<EntityResponse>(
    `/admin/v1/resources/${resourceId}/fields`,
    req,
    { headers: { 'Idempotency-Key': idempotencyKey() } },
  )
  return data
}
