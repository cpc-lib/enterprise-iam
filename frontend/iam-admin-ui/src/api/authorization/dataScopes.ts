import http from '@/api/client/http'
import type {
  CreateDataScopeRequest,
  EntityResponse,
  PermissionVersionResponse,
  ReplaceDataScopesRequest,
} from '@/types/authorization'
import { idempotencyKey } from './idempotency'

/** 创建数据范围：POST /admin/v1/data-scopes → 201 EntityResponse */
export async function createDataScope(req: CreateDataScopeRequest): Promise<EntityResponse> {
  const { data } = await http.post<EntityResponse>('/admin/v1/data-scopes', req, {
    headers: { 'Idempotency-Key': idempotencyKey() },
  })
  return data
}

/** 整体替换角色权限上的数据范围绑定：PUT /admin/v1/role-permissions/{id}/data-scopes */
export async function replaceRolePermissionDataScopes(
  rolePermissionId: string,
  req: ReplaceDataScopesRequest,
): Promise<PermissionVersionResponse> {
  const { data } = await http.put<PermissionVersionResponse>(
    `/admin/v1/role-permissions/${rolePermissionId}/data-scopes`,
    req,
    { headers: { 'Idempotency-Key': idempotencyKey() } },
  )
  return data
}
