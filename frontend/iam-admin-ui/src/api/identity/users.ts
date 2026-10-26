import http from '@/api/client/http'
import type {
  CreateUserRequest,
  DisableUserCommand,
  UpdateUserRequest,
  UserDetail,
  UserListPage,
  UserResponse,
} from '@/types/identity'
import { newIdempotencyKey } from './idempotency'

export interface ListUsersParams {
  tenantId?: string
  keyword?: string
  page?: number
  size?: number
  departmentId?: string
  positionId?: string
  status?: string
}

interface RawUserPageResponse {
  records?: UserDetail[]
  items?: UserDetail[]
  total?: number | string
  page?: number
  size?: number
  pageSize?: number
  totalPages?: number
}

/** 分页查询用户列表（GET /admin/v1/users） */
export async function listUsers(params: ListUsersParams): Promise<UserListPage> {
  const { data } = await http.get<RawUserPageResponse>('/admin/v1/users', {
    params: {
      ...params,
      pageSize: params.size ?? 20,
    },
  })
  const rawList = data?.records ?? data?.items ?? []
  const records: UserDetail[] = rawList.map((item) => ({
    ...item,
    userId: String(item.userId || (item as { id?: string | number }).id || ''),
    realName: item.realName || (item as { displayName?: string }).displayName || '',
    tenantId: String(item.tenantId || params.tenantId || ''),
  }))
  return {
    records,
    total: Number(data?.total ?? records.length),
    page: Number(data?.page ?? 1),
    size: Number(data?.size ?? data?.pageSize ?? 20),
    totalPages: Number(data?.totalPages ?? 1),
  }
}

/** 查询租户下的用户总数（支持 listUsers 即时统计与 /count 优雅降级） */
export async function getUserCount(tenantId?: string): Promise<number> {
  try {
    const res = await listUsers({ tenantId, page: 1, size: 1 })
    return res.total
  } catch {
    try {
      const { data } = await http.get<number>('/admin/v1/users/count', {
        params: tenantId ? { tenantId } : undefined,
      })
      return typeof data === 'number' ? data : Number(data ?? 0)
    } catch {
      return 0
    }
  }
}

/** 查询用户详情（GET /admin/v1/users/{userId}） */
export async function getUserDetail(userId: string, tenantId: string): Promise<UserDetail> {
  const { data } = await http.get<UserDetail>(`/admin/v1/users/${userId}`, {
    params: { tenantId },
  })
  return data
}

/** 更新用户信息与部门岗位归属（PUT /admin/v1/users/{userId}） */
export async function updateUser(
  userId: string,
  tenantId: string,
  req: UpdateUserRequest,
): Promise<UserDetail> {
  const { data } = await http.put<UserDetail>(`/admin/v1/users/${userId}`, req, {
    params: { tenantId },
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 创建租户用户（POST /admin/v1/users → 201） */
export async function createUser(req: CreateUserRequest, tenantId?: string): Promise<UserResponse> {
  const { data } = await http.post<UserResponse>('/admin/v1/users', req, {
    params: tenantId ? { tenantId } : undefined,
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

export interface EnableUserCommand {
  version?: number
  reason?: string
}

/** 启用用户（POST /admin/v1/users/{userId}/enable → 200） */
export async function enableUser(
  userId: string,
  tenantId: string,
  cmd?: EnableUserCommand,
): Promise<UserResponse> {
  const { data } = await http.post<UserResponse>(
    `/admin/v1/users/${userId}/enable`,
    cmd || { version: 0, reason: '管理员启用账号' },
    {
      params: { tenantId },
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    },
  )
  return data
}

/** 停用用户并使其会话失效（POST /admin/v1/users/{userId}/disable → 200） */
export async function disableUser(userId: string, req: DisableUserCommand, tenantId?: string): Promise<UserResponse> {
  const { data } = await http.post<UserResponse>(`/admin/v1/users/${userId}/disable`, req, {
    params: tenantId ? { tenantId } : undefined,
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return data
}

/** 强退用户所有会话（POST /admin/v1/users/{userId}/kick → 200） */
export async function kickUserSessions(userId: string, tenantId: string): Promise<void> {
  await http.post(`/admin/v1/users/${userId}/kick`, null, {
    params: { tenantId },
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
}

export interface ResetUserPasswordRequest {
  newPassword?: string
  reason?: string
}

export interface ResetUserPasswordResult {
  userId: string
  tenantId: string
  newPassword: string
  isTemporary: boolean
}

/** 重置用户密码（POST /admin/v1/users/{userId}/reset-password） */
export async function resetUserPassword(
  userId: string,
  tenantId: string,
  req?: ResetUserPasswordRequest,
): Promise<ResetUserPasswordResult> {
  const { data } = await http.post<ResetUserPasswordResult>(
    `/admin/v1/users/${userId}/reset-password`,
    req || {},
    {
      params: { tenantId },
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    },
  )
  return data
}
