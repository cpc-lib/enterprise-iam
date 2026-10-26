import http from './http'
import { NAVIGATION_SCHEMA } from '@/constants/navigation'
import type { NavSchema } from '@/constants/navigation'
import { useAuthStore } from '@/stores/auth'

export interface PageOperation {
  controlId: string
  operationId: string
  title: string
  actionType: string
}

export interface PageColumn {
  fieldKey: string
  label: string
  dataType: string
  sortable: boolean
  filterable: boolean
}

export interface PageSchema {
  pageKey: string
  title: string
  resourceType: string
  operations: PageOperation[]
  columns: PageColumn[]
}

export interface FieldDefinition {
  fieldKey: string
  fieldName: string
  dataType: string
  sensitive: boolean
  maskable: boolean
  description?: string
}

export interface FieldSchema {
  resourceType: string
  fields: FieldDefinition[]
}

/**
 * 服务端导航（Server-Driven Navigation）客户端。
 *
 * 请求 `/admin/v1/navigation/schema` 获取服务端根据用户角色及数据库权限配置下发的导航 schema。
 * 若服务端离线或无响应，平滑降级到本地 NAVIGATION_SCHEMA 作为兜底。
 */
export async function fetchNavigationSchema(): Promise<NavSchema> {
  const user = useAuthStore.getState().user
  const params: Record<string, string> = {}
  if (user?.tenantId) params.tenantId = user.tenantId
  if (user?.userId) params.userId = user.userId

  try {
    const res = await http.get<NavSchema>('/admin/v1/navigation/schema', {
      params: Object.keys(params).length > 0 ? params : undefined,
    })
    if (Array.isArray(res.data) && res.data.length > 0) {
      return res.data
    }
    return NAVIGATION_SCHEMA
  } catch {
    return NAVIGATION_SCHEMA
  }
}

/**
 * 服务端页面元数据（Page Schema）获取。
 * 请求 `/admin/v1/navigation/page-schemas/{pageKey}`，用于动态驱动页面操作按钮与数据表格列。
 */
export async function fetchPageSchema(pageKey: string): Promise<PageSchema | null> {
  try {
    const res = await http.get<PageSchema>(`/admin/v1/navigation/page-schemas/${encodeURIComponent(pageKey)}`)
    return res.data ?? null
  } catch {
    return null
  }
}

/**
 * 服务端资源字段元数据（Field Schema）获取。
 * 请求 `/admin/v1/navigation/field-schemas/{resourceType}`，用于动态驱动字段级别策略、脱敏与表单项配置。
 */
export async function fetchFieldSchema(resourceType: string): Promise<FieldSchema | null> {
  try {
    const res = await http.get<FieldSchema>(`/admin/v1/navigation/field-schemas/${encodeURIComponent(resourceType)}`)
    return res.data ?? null
  } catch {
    return null
  }
}
