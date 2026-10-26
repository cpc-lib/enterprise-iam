/** 授权策略域类型（对齐 docs/api/openapi-code-phase-02-policy.yaml / phase-01 & V2.0） */

/** 64 位正整数 ID：契约要求以数字字符串承载，避免超出 JS 安全整数精度 */
export type IamId = string

export type DataScopeType =
  | 'ALL'
  | 'SELF'
  | 'TEAM'
  | 'TEAM_AND_CHILDREN'
  | 'SPECIFIED_TEAM'
  | 'SHARED'
  | 'CUSTOM_POLICY'

export type DataScopeMergeMode = 'UNION' | 'INTERSECT'

export type FieldDataType =
  | 'STRING'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'OBJECT'
  | 'ARRAY'
  | 'MAP_FIELD'

export type SensitiveLevel = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED'

export type AuthorizationDecision = 'ALLOW' | 'DENY'

/** 实体命令通用响应：{ id, version, status } */
export interface EntityResponse {
  id: IamId
  version: number
  status: string
}

/** 绑定替换响应：{ bindingVersion, permissionVersion } */
export interface PermissionVersionResponse {
  bindingVersion: number
  permissionVersion: number
}

// ---------- 数据范围 ----------

export interface CreateDataScopeRequest {
  scopeCode: string
  scopeName: string
  scopeType: DataScopeType
  customPolicyId?: IamId | null
}

export interface DataScopeBinding {
  dataScopeId: IamId
  mergeMode: DataScopeMergeMode
  teamIds: IamId[]
  includeChildren?: boolean
}

export interface ReplaceDataScopesRequest {
  bindingVersion: number
  bindings: DataScopeBinding[]
}

// ---------- 资源字段元数据 ----------

export interface ResourceFieldInput {
  fieldCode: string
  propertyPath: string
  columnName?: string | null
  dataType: FieldDataType
  sensitiveLevel: SensitiveLevel
  systemManaged: boolean
}

export interface ReplaceResourceFieldsRequest {
  resourceVersion: number
  fields: ResourceFieldInput[]
}

// ---------- 字段策略 ----------

export interface CreateFieldPolicyRequest {
  resourceId: IamId
  operationId: IamId
  fieldId: IamId
  readable: boolean
  writable: boolean
  hidden: boolean
  maskStrategyId?: IamId | null
  priority: number
}

export interface ReplaceFieldPoliciesRequest {
  bindingVersion: number
  fieldPolicyIds: IamId[]
}

// ---------- 决策解释 ----------

export interface ExplainResponse {
  decisionId: IamId
  decision: AuthorizationDecision
  reasonCode: string
  evaluatedAt: string
  sanitizedSteps: string[]
}

// ---------- V2.0: ABAC 策略与安全条件 DSL ----------

export type AbacEffect = 'ALLOW' | 'DENY'
export type AbacPolicyStatus = 'ACTIVE' | 'DISABLED'

export interface AbacPolicy {
  id: IamId
  tenantId: IamId
  policyCode: string
  policyName: string
  resourceType: string
  operation: string
  effect: AbacEffect
  priority: number
  conditionExpr: string
  description?: string | null
  status: AbacPolicyStatus
  version: number
  createdAt?: string
}

export interface CreateAbacPolicyRequest {
  tenantId?: IamId
  policyCode: string
  policyName: string
  resourceType: string
  operation: string
  effect: AbacEffect
  priority: number
  conditionExpr: string
  description?: string
}

export interface AbacConditionEvaluationResult {
  valid: boolean
  matched: boolean
  tokenCount: number
  depth: number
  detail: string
}
