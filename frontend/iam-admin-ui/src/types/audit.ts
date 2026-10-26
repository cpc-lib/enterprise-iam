/** 审计服务类型（与 backend/iam-audit-service AuditLogResponse & V2.0OLAP分析对应） */

/** 64 位 ID 统一以十进制字符串承载（SPEC 47 §9），避免超出 JS 安全整数精度 */
export type AuditId = string

/** GET /admin/v1/audit/logs 返回条目 */
export interface AuditLog {
  id: AuditId
  tenantId: AuditId
  actorId: AuditId
  action: string
  resourceType: string
  resourceId: AuditId
  detail: string | null
  createdAt: string
}

/** GET /admin/v1/audit/logs 查询参数（tenantId 必填，其余可选） */
export interface AuditLogQueryParams {
  tenantId: AuditId
  actorId?: AuditId
  action?: string
  /** ISO-8601 即时时间 */
  from?: string
  to?: string
  limit?: number
}

// ---------- V2.0: ClickHouse / OLAP 审计聚合分析与冷归档导出 ----------

export interface AuditDistributionBucket {
  key: string
  count: number
}

export interface AuditAnalyticsSummary {
  tenantId: AuditId
  totalEvents: number
  allowCount: number
  denyCount: number
  highRiskEventCount: number
  olapEngine: 'CLICKHOUSE' | 'RELATIONAL_OLAP'
  actionDistribution: AuditDistributionBucket[]
  resourceDistribution: AuditDistributionBucket[]
}

export interface AuditArchiveExportResult {
  tenantId: AuditId
  exportedCount: number
  storageTarget: string
  exportedAt: string
}
