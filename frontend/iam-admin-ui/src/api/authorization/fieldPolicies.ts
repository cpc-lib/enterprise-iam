import http from '@/api/client/http'
import type {
  CreateFieldPolicyRequest,
  EntityResponse,
  PermissionVersionResponse,
  ReplaceFieldPoliciesRequest,
} from '@/types/authorization'
import { idempotencyKey } from './idempotency'

/** 创建字段策略：POST /admin/v1/field-policies → 201 EntityResponse */
export async function createFieldPolicy(req: CreateFieldPolicyRequest): Promise<EntityResponse> {
  const { data } = await http.post<EntityResponse>('/admin/v1/field-policies', req, {
    headers: { 'Idempotency-Key': idempotencyKey() },
  })
  return data
}

/** 整体替换角色权限上的字段策略绑定：PUT /admin/v1/role-permissions/{id}/field-policies */
export async function replaceRolePermissionFieldPolicies(
  rolePermissionId: string,
  req: ReplaceFieldPoliciesRequest,
): Promise<PermissionVersionResponse> {
  const { data } = await http.put<PermissionVersionResponse>(
    `/admin/v1/role-permissions/${rolePermissionId}/field-policies`,
    req,
    { headers: { 'Idempotency-Key': idempotencyKey() } },
  )
  return data
}
