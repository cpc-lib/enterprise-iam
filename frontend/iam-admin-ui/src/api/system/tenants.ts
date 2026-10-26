import http from '@/api/client/http'

export interface TenantItem {
  id: string
  tenantCode: string
  tenantName: string
  status: 'ACTIVE' | 'DISABLED' | 'INITIALIZING' | 'SUSPENDED'
  version: number
  createdAt: string
  updatedAt: string
}

export interface ListTenantsParams {
  keyword?: string
  status?: string
}

export async function getTenants(params?: ListTenantsParams): Promise<TenantItem[]> {
  const { data } = await http.get<TenantItem[]>('/admin/v1/tenants', { params })
  return data ?? []
}

export async function getTenant(tenantId: string): Promise<TenantItem> {
  const { data } = await http.get<TenantItem>(`/admin/v1/tenants/${tenantId}`)
  return data
}

export async function updateTenant(tenantId: string, tenantName: string): Promise<void> {
  await http.put(`/admin/v1/tenants/${tenantId}`, { tenantName })
}

export async function enableTenant(tenantId: string): Promise<void> {
  await http.post(`/admin/v1/tenants/${tenantId}/enable`)
}

export async function disableTenant(tenantId: string): Promise<void> {
  await http.post(`/admin/v1/tenants/${tenantId}/disable`)
}
