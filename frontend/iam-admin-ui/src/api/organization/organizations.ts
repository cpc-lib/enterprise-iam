import http from '@/api/client/http'
import type {
  CreateOrganizationRequest,
  DepartmentMember,
  DepartmentNode,
  EntityCreated,
  OrganizationDetail,
  UpdateOrganizationRequest,
} from '@/types/organization'
import { newIdempotencyKey } from '@/api/identity/idempotency'

interface RawEntityCreated {
  id?: string
  organizationId?: string | number
  version: number
  status: string
}

function toEntityCreated(raw: RawEntityCreated): EntityCreated {
  return {
    id: raw.id ?? String(raw.organizationId),
    version: raw.version,
    status: raw.status,
  }
}

/** 创建组织节点（POST /admin/v1/organizations → 201） */
export async function createOrganization(req: CreateOrganizationRequest): Promise<EntityCreated> {
  const { data } = await http.post<RawEntityCreated>('/admin/v1/organizations', req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return toEntityCreated(data)
}

/** 获取组织架构树（GET /admin/v1/organizations/tree） */
export async function getOrganizationTree(tenantId: string): Promise<DepartmentNode[]> {
  const { data } = await http.get<DepartmentNode[]>('/admin/v1/organizations/tree', {
    params: { tenantId },
  })
  return data
}

/** 获取部门树（GET /admin/v1/departments） */
export async function getDepartmentTree(tenantId: string): Promise<DepartmentNode[]> {
  const { data } = await http.get<DepartmentNode[]>('/admin/v1/departments', {
    params: { tenantId },
  })
  return data
}

/** 获取组织/部门详情（GET /admin/v1/organizations/{organizationId}） */
export async function getOrganizationDetail(organizationId: string, tenantId: string): Promise<OrganizationDetail> {
  const { data } = await http.get<OrganizationDetail>(`/admin/v1/organizations/${organizationId}`, {
    params: { tenantId },
  })
  return data
}

/** 更新组织/部门信息（PUT /admin/v1/organizations/{organizationId}） */
export async function updateOrganization(
  organizationId: string,
  req: UpdateOrganizationRequest,
): Promise<EntityCreated> {
  const { data } = await http.put<RawEntityCreated>(`/admin/v1/organizations/${organizationId}`, req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return toEntityCreated(data)
}

/** 删除组织节点（DELETE /admin/v1/organizations/{organizationId}） */
export async function deleteOrganization(organizationId: string, tenantId: string): Promise<void> {
  await http.delete(`/admin/v1/organizations/${organizationId}`, {
    params: { tenantId },
  })
}

/** 停用组织节点（POST /admin/v1/organizations/{organizationId}/disable） */
export async function disableOrganization(organizationId: string, tenantId: string): Promise<EntityCreated> {
  const { data } = await http.post<RawEntityCreated>(`/admin/v1/organizations/${organizationId}/disable`, null, {
    params: { tenantId },
  })
  return toEntityCreated(data)
}

/** 启用组织节点（POST /admin/v1/organizations/{organizationId}/enable） */
export async function enableOrganization(organizationId: string, tenantId: string): Promise<EntityCreated> {
  const { data } = await http.post<RawEntityCreated>(`/admin/v1/organizations/${organizationId}/enable`, null, {
    params: { tenantId },
  })
  return toEntityCreated(data)
}

/** 查询部门成员（GET /admin/v1/organizations/{organizationId}/members） */
export async function listOrganizationMembers(
  organizationId: string,
  tenantId: string,
): Promise<DepartmentMember[]> {
  const { data } = await http.get<DepartmentMember[]>(`/admin/v1/organizations/${organizationId}/members`, {
    params: { tenantId },
  })
  return data
}
