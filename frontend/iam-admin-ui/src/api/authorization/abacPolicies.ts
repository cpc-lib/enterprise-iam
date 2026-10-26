import http from '@/api/client/http'
import { idempotencyKey as createIdempotencyKey } from './idempotency'
import type {
  AbacConditionEvaluationResult,
  AbacPolicy,
  AbacPolicyStatus,
  CreateAbacPolicyRequest,
} from '@/types/authorization'

export async function listAbacPolicies(tenantId: string): Promise<AbacPolicy[]> {
  const { data } = await http.get<AbacPolicy[]>('/api/v1/abac-policies', {
    params: { tenantId },
  })
  return data
}

export async function createAbacPolicy(
  input: CreateAbacPolicyRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<AbacPolicy> {
  const { data } = await http.post<AbacPolicy>('/api/v1/abac-policies', input, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}

export async function updateAbacPolicyStatus(
  id: string,
  tenantId: string,
  status: AbacPolicyStatus,
  idempotencyKey = createIdempotencyKey(),
): Promise<AbacPolicy> {
  const { data } = await http.post<AbacPolicy>(
    `/api/v1/abac-policies/${id}/status`,
    { tenantId, status },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}

export async function deleteAbacPolicy(id: string, tenantId: string): Promise<void> {
  await http.delete(`/api/v1/abac-policies/${id}`, {
    params: { tenantId },
  })
}

export async function evaluateAbacCondition(
  conditionExpr: string,
  attributes: Record<string, unknown>,
): Promise<AbacConditionEvaluationResult> {
  const { data } = await http.post<AbacConditionEvaluationResult>('/api/v1/abac-policies/evaluate', {
    conditionExpr,
    attributes,
  })
  return data
}
