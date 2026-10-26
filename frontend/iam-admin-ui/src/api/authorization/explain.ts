import http from '@/api/client/http'
import type { ExplainResponse } from '@/types/authorization'

/** 获取脱敏决策解释：GET /internal/v1/authorization/decisions/{decisionId}/explain（404 → IAM_AUTHZ_DECISION_NOT_FOUND） */
export async function explainDecision(decisionId: string): Promise<ExplainResponse> {
  const { data } = await http.get<ExplainResponse>(
    `/internal/v1/authorization/decisions/${decisionId}/explain`,
  )
  return data
}
