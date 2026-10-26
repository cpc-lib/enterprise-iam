import http from '@/api/client/http'
import type { PageResponse } from '@/api/auth/sessions'

export interface DictTypeItem {
  id: string
  tenantId: string
  dictType: string
  dictName: string
  status: 'ACTIVE' | 'DISABLED'
  description?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface DictDataItem {
  id: string
  tenantId: string
  dictType: string
  dictLabel: string
  dictValue: string
  sortOrder: number
  status: 'ACTIVE' | 'DISABLED'
  cssTag?: string
  isDefault: boolean
  description?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface ListDictTypeParams {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface ListDictDataParams {
  dictType?: string
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface CreateDictTypeRequest {
  dictType: string
  dictName: string
  status?: string
  description?: string
}

export interface UpdateDictTypeRequest {
  dictName: string
  status?: string
  description?: string
  version: number
}

export interface CreateDictDataRequest {
  dictType: string
  dictLabel: string
  dictValue: string
  sortOrder?: number
  status?: string
  cssTag?: string
  isDefault?: boolean
  description?: string
}

export interface UpdateDictDataRequest {
  dictLabel: string
  dictValue: string
  sortOrder?: number
  status?: string
  cssTag?: string
  isDefault?: boolean
  description?: string
  version: number
}

// 字典类型 API
export async function listDictTypes(params: ListDictTypeParams = {}): Promise<PageResponse<DictTypeItem>> {
  const { data } = await http.get<PageResponse<DictTypeItem>>('/admin/v1/dicts/types', { params })
  return data
}

export async function getDictType(id: string): Promise<DictTypeItem> {
  const { data } = await http.get<DictTypeItem>(`/admin/v1/dicts/types/${id}`)
  return data
}

export async function createDictType(req: CreateDictTypeRequest): Promise<DictTypeItem> {
  const { data } = await http.post<DictTypeItem>('/admin/v1/dicts/types', req)
  return data
}

export async function updateDictType(id: string, req: UpdateDictTypeRequest): Promise<DictTypeItem> {
  const { data } = await http.put<DictTypeItem>(`/admin/v1/dicts/types/${id}`, req)
  return data
}

export async function deleteDictType(id: string): Promise<void> {
  await http.delete(`/admin/v1/dicts/types/${id}`)
}

// 字典数据 API
export async function listDictData(params: ListDictDataParams = {}): Promise<PageResponse<DictDataItem>> {
  const { data } = await http.get<PageResponse<DictDataItem>>('/admin/v1/dicts/data', { params })
  return data
}

export async function listDictDataByType(dictType: string): Promise<DictDataItem[]> {
  const { data } = await http.get<DictDataItem[]>(`/admin/v1/dicts/data/type/${encodeURIComponent(dictType)}`)
  return data
}

export async function createDictData(req: CreateDictDataRequest): Promise<DictDataItem> {
  const { data } = await http.post<DictDataItem>('/admin/v1/dicts/data', req)
  return data
}

export async function updateDictData(id: string, req: UpdateDictDataRequest): Promise<DictDataItem> {
  const { data } = await http.put<DictDataItem>(`/admin/v1/dicts/data/${id}`, req)
  return data
}

export async function deleteDictData(id: string): Promise<void> {
  await http.delete(`/admin/v1/dicts/data/${id}`)
}
