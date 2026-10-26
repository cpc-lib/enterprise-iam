import http from '@/api/client/http'
import type {
  AuditAnalyticsSummary,
  AuditArchiveExportResult,
  AuditLog,
  AuditLogQueryParams,
} from '@/types/audit'

/**
 * 审计日志查询 —— AuditLogController#query（GET /admin/v1/audit/logs）。
 */
export async function queryAuditLogs(params: AuditLogQueryParams): Promise<AuditLog[]> {
  const { data } = await http.get<AuditLog[]>('/admin/v1/audit/logs', { params })
  return data
}

/**
 * V2.0 OLAP 审计聚合分析摘要（GET /api/v1/audit/analytics/summary）
 */
export async function fetchAuditAnalyticsSummary(tenantId: string): Promise<AuditAnalyticsSummary> {
  const { data } = await http.get<AuditAnalyticsSummary>('/api/v1/audit/analytics/summary', {
    params: { tenantId },
  })
  return data
}

/**
 * V2.0 审计冷归档批量导出（POST /api/v1/audit/archive-export）
 */
export async function exportAuditArchive(tenantId: string): Promise<AuditArchiveExportResult> {
  const { data } = await http.post<AuditArchiveExportResult>(
    '/api/v1/audit/archive-export',
    null,
    { params: { tenantId } },
  )
  return data
}
