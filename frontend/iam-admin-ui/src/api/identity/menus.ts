import http from '@/api/client/http'
import type {
  CreateMenuRequest,
  MenuItem,
  MenuNode,
  MenuQueryParams,
  PageResult,
  ReorderMenuRequest,
  UpdateMenuRequest,
} from '@/types/identity'
import { newIdempotencyKey } from './idempotency'

/** 获取租户所有菜单（GET /admin/v1/menus） */
export async function listMenus(tenantId: string): Promise<MenuItem[]> {
  const { data } = await http.get<MenuItem[]>('/admin/v1/menus', {
    params: { tenantId },
  })
  return data
}

/** 获取层级菜单树（GET /admin/v1/menus/tree，支持名称模糊查询与类型过滤） */
export async function getMenuTree(
  tenantId: string,
  params?: Partial<MenuQueryParams>,
): Promise<MenuNode[]> {
  const { data } = await http.get<MenuNode[]>('/admin/v1/menus/tree', {
    params: { tenantId, ...params },
  })
  return data
}

/** 分页获取平铺菜单列表（GET /admin/v1/menus/page） */
export async function getMenuPage(
  params: MenuQueryParams,
): Promise<PageResult<MenuItem>> {
  const { data } = await http.get<PageResult<MenuItem>>('/admin/v1/menus/page', {
    params,
  })
  return data
}

/** 获取菜单详情（GET /admin/v1/menus/{menuId}） */
export async function getMenuDetail(menuId: string, tenantId: string): Promise<MenuItem> {
  const { data } = await http.get<MenuItem>(`/admin/v1/menus/${menuId}`, {
    params: { tenantId },
  })
  return data
}

/** 创建菜单配置（POST /admin/v1/menus） */
export async function createMenu(req: CreateMenuRequest): Promise<MenuItem> {
  const { data } = await http.post<MenuItem>('/admin/v1/menus', req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 修改菜单配置（PUT /admin/v1/menus/{menuId}） */
export async function updateMenu(menuId: string, req: UpdateMenuRequest): Promise<MenuItem> {
  const { data } = await http.put<MenuItem>(`/admin/v1/menus/${menuId}`, req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 删除菜单配置（DELETE /admin/v1/menus/{menuId}） */
export async function deleteMenu(menuId: string, tenantId: string): Promise<void> {
  await http.delete(`/admin/v1/menus/${menuId}`, {
    params: { tenantId },
  })
}

/** 启用菜单（POST /admin/v1/menus/{menuId}/enable） */
export async function enableMenu(menuId: string, tenantId: string): Promise<MenuItem> {
  const { data } = await http.post<MenuItem>(`/admin/v1/menus/${menuId}/enable`, null, {
    params: { tenantId },
  })
  return data
}

/** 停用菜单（POST /admin/v1/menus/{menuId}/disable） */
export async function disableMenu(menuId: string, tenantId: string): Promise<MenuItem> {
  const { data } = await http.post<MenuItem>(`/admin/v1/menus/${menuId}/disable`, null, {
    params: { tenantId },
  })
  return data
}

/** 发布单菜单（POST /admin/v1/menus/{menuId}/publish） */
export async function publishMenu(menuId: string, tenantId: string): Promise<MenuItem> {
  const { data } = await http.post<MenuItem>(`/admin/v1/menus/${menuId}/publish`, null, {
    params: { tenantId },
  })
  return data
}

/** 发布租户所有生效菜单（POST /admin/v1/menus/publish） */
export async function publishAllMenus(tenantId: string): Promise<void> {
  await http.post('/admin/v1/menus/publish', null, {
    params: { tenantId },
  })
}

/** 批量重排序菜单（POST /admin/v1/menus/reorder） */
export async function reorderMenus(items: ReorderMenuRequest[]): Promise<void> {
  await http.post('/admin/v1/menus/reorder', items)
}
