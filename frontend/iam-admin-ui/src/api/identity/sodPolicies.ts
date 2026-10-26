import http from '@/api/client/http'
import { newIdempotencyKey as createIdempotencyKey } from './idempotency'
import type {
  CreateSodPolicyRequest,
  SodConflictEvaluateResult,
  SodPolicy,
  SodPolicyStatus,
} from '@/types/identity'

export async function listSodPolicies(tenantId: string): Promise<SodPolicy[]> {
  const { data } = await http.get<SodPolicy[]>('/api/v1/sod-policies', {
    params: { tenantId },
  })
  return data
}

export async function createSodPolicy(
  input: CreateSodPolicyRequest,
  idempotencyKey = createIdempotencyKey(),
): Promise<SodPolicy> {
  const { data } = await http.post<SodPolicy>('/api/v1/sod-policies', input, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}

export async function updateSodPolicyStatus(
  id: string,
  tenantId: string,
  status: SodPolicyStatus,
  idempotencyKey = createIdempotencyKey(),
): Promise<SodPolicy> {
  const { data } = await http.post<SodPolicy>(
    `/api/v1/sod-policies/${id}/status`,
    { tenantId, status },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}

export async function evaluateSodConflict(
  tenantId: string,
  roleIds: string[],
): Promise<SodConflictEvaluateResult> {
  const { data } = await http.post<SodConflictEvaluateResult>('/api/v1/sod-policies/evaluate', {
    tenantId,
    roleIds,
  })
  return data
}
