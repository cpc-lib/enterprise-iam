/** 命令接口通用的创建/更新响应（openapi-code-phase-02-policy.yaml EntityResponse，ID 归一化为字符串） */
export interface EntityCreated {
  id: string
  version: number
  status: string
}

/** POST /admin/v1/organizations 请求体 */
export interface CreateOrganizationRequest {
  orgCode: string
  orgName: string
  parentId?: string | null
}

/** POST /admin/v1/teams 请求体 */
export interface CreateTeamRequest {
  organizationId: string
  parentTeamId?: string | null
  teamCode: string
  teamName: string
}

/** POST /admin/v1/teams/{teamId}/move 请求体 */
export interface MoveTeamRequest {
  newParentTeamId?: string | null
  version: number
}

/** POST /admin/v1/teams/{teamId}/roles 请求体 */
export interface CreateTeamRoleRequest {
  roleCode: string
  roleName: string
  description?: string | null
}

/** PUT /admin/v1/team-roles/{teamRoleId}/permissions 请求体 */
export interface ReplacePermissionSetCommand {
  version: number
  permissionIds: string[]
}

/** PUT /admin/v1/team-roles/{teamRoleId}/permissions 响应 */
export interface PermissionVersionResult {
  bindingVersion: number
  permissionVersion: number
}

export interface DepartmentNode {
  id: string
  tenantId: string
  parentId?: string | null
  code: string
  name: string
  sortOrder: number
  status: string
  children: DepartmentNode[]
}

export interface DepartmentMember {
  userId: string
  username: string
  realName?: string | null
  email?: string | null
  phone?: string | null
  positionId?: string | null
  positionName?: string | null
  status: string
}

export interface UpdateOrganizationRequest {
  name: string
  sortOrder?: number
}

export interface OrganizationDetail {
  id: string
  tenantId: string
  parentId?: string | null
  code: string
  name: string
  sortOrder: number
  status: string
  createdAt: string
  updatedAt?: string | null
}

export interface PositionItem {
  id: string
  tenantId: string
  code: string
  name: string
  sortOrder: number
  status: string
  createdAt: string
  updatedAt?: string | null
}

export interface CreatePositionRequest {
  code: string
  name: string
  sortOrder?: number
}

export interface UpdatePositionRequest {
  name: string
  sortOrder?: number
}
