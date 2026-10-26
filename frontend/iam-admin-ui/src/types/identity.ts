import type { IdentityType } from '@/api/client/auth'

/** 用户状态（openapi-code-phase-01.yaml UserResponse） */
export type UserStatus = 'INVITED' | 'ACTIVE' | 'DISABLED'

/** 角色状态（openapi-code-phase-01.yaml RoleResponse） */
export type RoleStatus = 'ACTIVE' | 'DISABLED'

/** POST /admin/v1/users 请求体 */
export interface CreateUserRequest {
  displayName: string
  identity: {
    type: IdentityType
    value: string
  }
  initialPassword?: string
}

/** POST /admin/v1/users 响应（ID 归一化为字符串） */
export interface UserResponse {
  userId: string
  status: UserStatus
  version: number
}

/** POST /admin/v1/users/{userId}/disable 请求体 */
export interface DisableUserCommand {
  version: number
  reason: string
}

/** POST /admin/v1/roles 请求体 */
export interface CreateRoleRequest {
  roleCode: string
  roleName: string
  description?: string | null
}

/** POST /admin/v1/roles 响应（ID 归一化为字符串） */
export interface RoleResponse {
  roleId: string
  status: RoleStatus
  version: number
}

/** GET /admin/v1/roles 角色列表项 */
export interface RoleItem {
  id: string
  tenantId?: string
  roleCode: string
  roleName: string
  description?: string | null
  status: RoleStatus
  version: number
  priority?: number
  createdAt?: string
}

/** PUT /admin/v1/roles/{roleId}/permissions 请求体 */
export interface ReplaceRolePermissionsRequest {
  roleVersion: number
  permissionIds: string[]
}

/** PUT /admin/v1/roles/{roleId}/permissions 响应（ID 归一化为字符串） */
export interface RolePermissionResponse {
  roleId: string
  roleVersion: number
  permissionVersion: number
}

/** PUT /admin/v1/users/{userId}/roles/{roleId} 请求体 */
export interface BindUserRoleCommand {
  version: number
}

/** POST /admin/v1/tenants/bootstrap 请求体 */
export interface TenantBootstrapRequest {
  tenantCode: string
  tenantName: string
  initialAdmin: {
    identityType: IdentityType
    identity: string
    displayName: string
    initialPassword: string
  }
}

/** POST /admin/v1/tenants/bootstrap 响应（202 已受理，ID 归一化为字符串） */
export interface TenantBootstrapResponse {
  tenantId: string
  status: 'INITIALIZING'
  operationId: string
}

// ---------- V2.0: 职责分离策略 ----------

export type SodEnforcementMode = 'STATIC' | 'DYNAMIC'
export type SodSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM'
export type SodPolicyStatus = 'ACTIVE' | 'DISABLED'

export interface SodPolicy {
  id: string
  tenantId: string
  policyCode: string
  policyName: string
  description?: string | null
  conflictingRoleIds: string[]
  maxAllowedRoles: number
  enforcementMode: SodEnforcementMode
  severity: SodSeverity
  status: SodPolicyStatus
  version: number
  createdAt?: string
}

export interface CreateSodPolicyRequest {
  tenantId?: string
  policyCode: string
  policyName: string
  description?: string
  conflictingRoleIds: string[]
  maxAllowedRoles: number
  enforcementMode: SodEnforcementMode
  severity: SodSeverity
}

export interface SodConflictEvaluateResult {
  violated: boolean
  violatedPolicyCode?: string | null
  violatedPolicyName?: string | null
  conflictingRoleIds: string[]
}

// ---------- V2.0: 权限申请与审批工作流 ----------

export type ApprovalRequestType = 'ROLE_ASSIGN' | 'TEAM_ROLE_ASSIGN' | 'RESOURCE_SHARE'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface PermissionApprovalRequestItem {
  id: string
  tenantId: string
  applicantUserId: string
  requestType: ApprovalRequestType
  targetId: string
  targetName: string
  justification: string
  durationHours: number
  status: ApprovalStatus
  approverUserId?: string | null
  decisionComment?: string | null
  decidedAt?: string | null
  version: number
  createdAt: string
}

export interface CreateApprovalRequestCommand {
  tenantId?: string
  applicantUserId: string
  requestType: ApprovalRequestType
  targetId: string
  targetName: string
  justification: string
  durationHours: number
}

export interface ApprovalDecisionCommand {
  tenantId?: string
  approverUserId: string
  comment: string
}

// ---------- V2.0: WebAuthn / Passkey 凭证 ----------

export interface WebAuthnCredentialItem {
  id: string
  tenantId: string
  userId: string
  credentialId: string
  deviceName: string
  signCount: number
  transports?: string | null
  status: 'ACTIVE' | 'REVOKED'
  createdAt: string
  lastUsedAt?: string | null
}

// ---------- 用户详情与分页 ----------

export interface UserDetail {
  userId: string
  tenantId: string
  username: string
  realName?: string | null
  email?: string | null
  phone?: string | null
  departmentId?: string | null
  departmentName?: string | null
  positionId?: string | null
  positionName?: string | null
  status: string
  version?: number
  createdAt: string
  updatedAt?: string | null
  isTenantAdmin?: boolean
}

export interface UpdateUserRequest {
  realName?: string
  email?: string
  phone?: string
  departmentId?: string | null
  positionId?: string | null
}

export interface UserListPage {
  records: UserDetail[]
  total: number
  page: number
  size: number
  totalPages: number
}

// ---------- 菜单配置与发布 ----------

export type MenuType = 'DIRECTORY' | 'MENU' | 'BUTTON'
export type MenuStatus = 'ACTIVE' | 'DISABLED'
export type MenuPublishStatus = 'DRAFT' | 'PUBLISHED'

export interface MenuItem {
  id: string
  tenantId: string
  parentId?: string | null
  code: string
  title: string
  path?: string | null
  component?: string | null
  icon?: string | null
  permission?: string | null
  menuType: MenuType
  sortOrder: number
  status: MenuStatus
  publishStatus: MenuPublishStatus
  createdAt: string
  updatedAt?: string | null
}

export interface MenuNode extends MenuItem {
  children: MenuNode[]
}

export interface MenuQueryParams {
  tenantId?: string
  title?: string
  menuType?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface CreateMenuRequest {
  tenantId?: string
  parentId?: string | null
  code: string
  title: string
  path?: string | null
  component?: string | null
  icon?: string | null
  permission?: string | null
  menuType: MenuType
  sortOrder?: number
}

export interface UpdateMenuRequest {
  tenantId?: string
  parentId?: string | null
  code: string
  title: string
  path?: string | null
  component?: string | null
  icon?: string | null
  permission?: string | null
  menuType: MenuType
  sortOrder?: number
}

export interface ReorderMenuRequest {
  tenantId?: string
  id: string
  sortOrder: number
}
