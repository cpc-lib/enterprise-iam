import http from '@/api/client/http'
import type { TenantBootstrapRequest, TenantBootstrapResponse } from '@/types/identity'
import { newIdempotencyKey } from './idempotency'

export type BootstrapTenantParams = TenantBootstrapRequest

/**
 * 引导租户与初始管理员（POST /admin/v1/tenants/bootstrap → 202 Accepted）。
 */
export async function bootstrapTenant(
  params: BootstrapTenantParams,
): Promise<TenantBootstrapResponse> {
  const { data } = await http.post<TenantBootstrapResponse>(
    '/admin/v1/tenants/bootstrap',
    params,
    {
      headers: {
        'Idempotency-Key': newIdempotencyKey(),
      },
    },
  )
  return data
}
