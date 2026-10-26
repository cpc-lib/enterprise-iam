/** 分享域类型（SPEC 47 §9 收敛后的 ShareController DTO + V2.0 文件夹继承/集合共享） */
import type { IamId } from './authorization'

export type ShareTargetType = 'USER' | 'TEAM' | 'ROLE' | 'TEAM_ROLE'

export type ShareScopeType = 'INSTANCE' | 'FOLDER_INHERITED' | 'COLLECTION'

export type ShareStatus = 'WAITING' | 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface ShareResponse {
  shareId: IamId
  status: ShareStatus
  version: number
  projectionEpoch: number
  scopeType?: ShareScopeType
  folderPath?: string | null
  collectionResourceIds?: string[]
}

/** 分享目标（嵌套对象：类型 + 用户/团队/角色/团队角色 ID） */
export interface ShareTarget {
  type: ShareTargetType
  id: IamId
}

/**
 * 创建分享请求（支持 V2.0 文件夹继承 FOLDER_INHERITED 与资源集合 COLLECTION）。
 */
export interface CreateShareRequest {
  resourceId: IamId
  resourceInstanceKey: string
  target: ShareTarget
  operationIds: IamId[]
  fields: []
  /** ISO-8601 过期时间串 */
  expireAt: string
  canReshare: boolean
  /** V2.0 共享作用域类型：单实例 / 文件夹继承 ACL / 资源集合 */
  scopeType?: ShareScopeType
  /** 当 scopeType === 'FOLDER_INHERITED' 时的文件夹前缀路径（例如 /projects/alpha/） */
  folderPath?: string | null
  /** 当 scopeType === 'COLLECTION' 时的集合实例标识列表 */
  collectionResourceIds?: string[]
}

export interface RevokeShareRequest {
  version: number
  reason: string
}

/** 续分享：与创建同形，额外携带父分享版本 */
export interface ReshareRequest extends CreateShareRequest {
  parentVersion: number
}
