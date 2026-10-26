import http from '@/api/client/http'
import type {
  CreateTeamRequest,
  CreateTeamRoleRequest,
  EntityCreated,
  MoveTeamRequest,
  PermissionVersionResult,
  ReplacePermissionSetCommand,
} from '@/types/organization'
import { newIdempotencyKey } from '@/api/identity/idempotency'

/**
 * SPEC 47 §9：创建/移动响应统一为 { id, version, status }。
 * 保留 teamId/teamRoleId 单发布周期的兜底读取（后端原子更新后可移除）。
 */
interface RawEntityCreated {
  id?: string
  teamId?: string | number
  teamRoleId?: string | number
  version: number
  status: string
}

function toEntityCreated(raw: RawEntityCreated): EntityCreated {
  return {
    id: raw.id ?? String(raw.teamId ?? raw.teamRoleId),
    version: raw.version,
    status: raw.status,
  }
}

/** 创建团队节点（POST /admin/v1/teams → 201） */
export async function createTeam(req: CreateTeamRequest): Promise<EntityCreated> {
  const { data } = await http.post<RawEntityCreated>('/admin/v1/teams', req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return toEntityCreated(data)
}

/** 在租户树内移动团队（POST /admin/v1/teams/{teamId}/move → 200） */
export async function moveTeam(teamId: string, req: MoveTeamRequest): Promise<EntityCreated> {
  const { data } = await http.post<RawEntityCreated>(`/admin/v1/teams/${teamId}/move`, req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return toEntityCreated(data)
}

/** 添加团队成员（PUT /admin/v1/teams/{teamId}/members/{userId} → 204，空响应体） */
export async function addTeamMember(
  teamId: string,
  userId: string,
  version: number,
): Promise<void> {
  await http.put(`/admin/v1/teams/${teamId}/members/${userId}`, { version }, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
}

/** 移除团队成员（DELETE /admin/v1/teams/{teamId}/members/{userId} → 204，版本走 If-Match-Version 请求头） */
export async function removeTeamMember(
  teamId: string,
  userId: string,
  ifMatchVersion: number,
): Promise<void> {
  await http.delete(`/admin/v1/teams/${teamId}/members/${userId}`, {
    headers: {
      'If-Match-Version': String(ifMatchVersion),
      'Idempotency-Key': newIdempotencyKey(),
    },
  })
}

/** 创建团队角色（POST /admin/v1/teams/{teamId}/roles → 201） */
export async function createTeamRole(
  teamId: string,
  req: CreateTeamRoleRequest,
): Promise<EntityCreated> {
  const { data } = await http.post<RawEntityCreated>(`/admin/v1/teams/${teamId}/roles`, req, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
  return toEntityCreated(data)
}

/**
 * 绑定成员团队角色（PUT /admin/v1/teams/{teamId}/members/{userId}/roles/{teamRoleId} → 204）。
 * 成功响应为空响应体，不读取/解析 data。
 */
export async function bindTeamMemberRole(
  teamId: string,
  userId: string,
  teamRoleId: string,
  version: number,
): Promise<void> {
  await http.put(`/admin/v1/teams/${teamId}/members/${userId}/roles/${teamRoleId}`, { version }, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
  })
}

/** 替换团队角色权限集（PUT /admin/v1/team-roles/{teamRoleId}/permissions → 200） */
export async function replaceTeamRolePermissions(
  teamRoleId: string,
  req: ReplacePermissionSetCommand,
): Promise<PermissionVersionResult> {
  const { data } = await http.put<PermissionVersionResult>(
    `/admin/v1/team-roles/${teamRoleId}/permissions`,
    req,
    { headers: { 'Idempotency-Key': newIdempotencyKey() } },
  )
  return data
}
