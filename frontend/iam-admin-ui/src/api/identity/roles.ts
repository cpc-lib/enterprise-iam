import http from '@/api/client/http'
import type {
  BindUserRoleCommand,
  CreateRoleRequest,
  ReplaceRolePermissionsRequest,
  RoleItem,
  RolePermissionResponse,
  RoleResponse,
  UserDetail,
} from '@/types/identity'
import { newIdempotencyKey } from './idempotency'

/** 获取租户角色列表（GET /admin/v1/roles） */
export async function getRoles(tenantId?: string): Promise<RoleItem[]> {
  const { data } = await http.get<RoleItem[]>('/admin/v1/roles', {
    params: tenantId ? { tenantId } : undefined,
  })
  return data ?? []
}

/** 启用角色（POST /admin/v1/roles/{roleId}/enable） */
export async function enableRole(roleId: string, version: number, tenantId?: string): Promise<RoleItem> {
  const { data } = await http.post<RoleItem>(
    `/admin/v1/roles/${roleId}/enable`,
    { version },
    { params: tenantId ? { tenantId } : undefined },
  )
  return data
}

/** 禁用角色（POST /admin/v1/roles/{roleId}/disable） */
export async function disableRole(roleId: string, version: number, tenantId?: string): Promise<RoleItem> {
  const { data } = await http.post<RoleItem>(
    `/admin/v1/roles/${roleId}/disable`,
    { version },
    { params: tenantId ? { tenantId } : undefined },
  )
  return data
}

/** 删除角色（DELETE /admin/v1/roles/{roleId}） */
export async function deleteRole(roleId: string, version?: number, tenantId?: string): Promise<void> {
  await http.delete(`/admin/v1/roles/${roleId}`, {
    params: {
      ...(version !== undefined ? { version } : {}),
      ...(tenantId ? { tenantId } : {}),
    },
  })
}

/** 创建租户角色（POST /admin/v1/roles → 201） */
export async function createRole(req: CreateRoleRequest, tenantId?: string): Promise<RoleResponse> {
  const { data } = await http.post<RoleResponse>('/admin/v1/roles', req, {
    params: tenantId ? { tenantId } : undefined,
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 替换角色权限集（PUT /admin/v1/roles/{roleId}/permissions → 200） */
export async function replaceRolePermissions(
  roleId: string,
  req: ReplaceRolePermissionsRequest,
): Promise<RolePermissionResponse> {
  const { data } = await http.put<RolePermissionResponse>(
    `/admin/v1/roles/${roleId}/permissions`,
    req,
    { headers: { 'Idempotency-Key': newIdempotencyKey() } },
  )
  return data
}

/** 获取角色拥有的菜单 ID 列表（GET /admin/v1/roles/{roleId}/menus） */
export async function getRoleMenus(roleId: string, tenantId?: string): Promise<string[]> {
  const { data } = await http.get<string[]>(`/admin/v1/roles/${roleId}/menus`, {
    params: tenantId ? { tenantId } : undefined,
  })
  return data ?? []
}

/** 更新角色菜单权限集（PUT /admin/v1/roles/{roleId}/menus → 200，操作 iam_role_menu） */
export async function updateRoleMenus(roleId: string, menuIds: string[], tenantId?: string): Promise<void> {
  await http.put(`/admin/v1/roles/${roleId}/menus`, { menuIds }, {
    params: tenantId ? { tenantId } : undefined,
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
}

/** 获取用户已分配的角色列表（GET /admin/v1/users/{userId}/roles） */
export async function getUserRoles(userId: string, tenantId?: string): Promise<RoleItem[]> {
  const { data } = await http.get<RoleItem[]>(`/admin/v1/users/${encodeURIComponent(userId)}/roles`, {
    params: tenantId ? { tenantId } : undefined,
  })
  return data ?? []
}

/** 绑定用户角色（PUT /admin/v1/users/{userId}/roles/{roleId} → 204，空响应体，不解析 JSON） */
export async function bindUserRole(
  userId: string,
  roleId: string,
  req: BindUserRoleCommand,
  tenantId?: string,
): Promise<void> {
  await http.put(`/admin/v1/users/${userId}/roles/${roleId}`, req, {
    params: tenantId ? { tenantId } : undefined,
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
}

/** 获取分配该角色的用户列表（GET /admin/v1/roles/{roleId}/users） */
export async function getRoleUsers(roleId: string, tenantId?: string): Promise<UserDetail[]> {
  const { data } = await http.get<UserDetail[]>(`/admin/v1/roles/${roleId}/users`, {
    params: tenantId ? { tenantId } : undefined,
  })
  return (data ?? []).map((item) => ({
    ...item,
    userId: String(item.userId || (item as { id?: string | number }).id || ''),
    realName: item.realName || (item as { displayName?: string }).displayName || '',
    tenantId: String(item.tenantId || tenantId || ''),
  }))
}

/** 从角色中移除指定用户（DELETE /admin/v1/roles/{roleId}/users/{userId}） */
export async function unbindUserRole(
  roleId: string,
  userId: string,
  tenantId?: string,
): Promise<void> {
  await http.delete(`/admin/v1/roles/${roleId}/users/${userId}`, {
    params: tenantId ? { tenantId } : undefined,
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
}
