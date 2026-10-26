import http from '@/api/client/http'
import { newIdempotencyKey as createIdempotencyKey } from './idempotency'
import type {
  ApprovalDecisionCommand,
  ApprovalStatus,
  CreateApprovalRequestCommand,
  PermissionApprovalRequestItem,
} from '@/types/identity'

export async function listApprovalRequests(
  tenantId: string,
  status?: ApprovalStatus,
): Promise<PermissionApprovalRequestItem[]> {
  const { data } = await http.get<PermissionApprovalRequestItem[]>('/api/v1/approvals', {
    params: { tenantId, status },
  })
  return data
}

export async function submitApprovalRequest(
  input: CreateApprovalRequestCommand,
  idempotencyKey = createIdempotencyKey(),
): Promise<PermissionApprovalRequestItem> {
  const { data } = await http.post<PermissionApprovalRequestItem>('/api/v1/approvals', input, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}

export async function approvePermissionRequest(
  id: string,
  input: ApprovalDecisionCommand,
  idempotencyKey = createIdempotencyKey(),
): Promise<PermissionApprovalRequestItem> {
  const { data } = await http.post<PermissionApprovalRequestItem>(`/api/v1/approvals/${id}/approve`, input, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}

export async function rejectPermissionRequest(
  id: string,
  input: ApprovalDecisionCommand,
  idempotencyKey = createIdempotencyKey(),
): Promise<PermissionApprovalRequestItem> {
  const { data } = await http.post<PermissionApprovalRequestItem>(`/api/v1/approvals/${id}/reject`, input, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}
