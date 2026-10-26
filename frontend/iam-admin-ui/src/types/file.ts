/** 文件服务类型（与 backend/iam-file-service 接口 DTO 一一对应，含 V2.0 水印/DLP/归档/容灾演练） */

/** 64 位 ID 统一以十进制字符串承载（SPEC 47 §9），避免超出 JS 安全整数精度 */
export type FileId = string

export type UploadMode = 'INSTANT' | 'MULTIPART'

export type UploadSessionStatus =
  | 'INIT'
  | 'UPLOADING'
  | 'VERIFYING'
  | 'MERGING'
  | 'COMPLETED'
  | 'FAILED'
  | 'ABORTED'
  | 'EXPIRED'

export type FileState =
  | 'VERIFYING'
  | 'SCANNING'
  | 'AVAILABLE'
  | 'QUARANTINED'
  | 'INFECTED'
  | 'DELETED'
  | 'ARCHIVED'

export type FileScanStatus = 'PENDING' | 'SCANNING' | 'CLEAN' | 'INFECTED' | 'FAILED' | 'NOT_REQUIRED'

export type FileStorageTier = 'STANDARD' | 'INFREQUENT_ACCESS' | 'GLACIER_ARCHIVE'

export type FileDlpStatus = 'PASS' | 'MASKED' | 'BLOCKED'

export type FileAccessMode = 'AUTO' | 'PROXY' | 'PRESIGNED' | 'SANDBOX_PREVIEW'

export type FileReferenceType = 'ATTACHMENT' | 'INLINE' | 'EVIDENCE' | 'AVATAR'

/** UploadSessionResponse（POST /api/v1/file-uploads 等） */
export interface UploadSession {
  mode: UploadMode
  uploadId?: FileId | null
  fileId?: FileId | null
  status: UploadSessionStatus
  partSize?: number | null
  partCount?: number | null
  uploadedParts: number[]
  expiresAt?: string | null
  version: number
}

/** SignPartResponse（POST /api/v1/file-uploads/{id}/parts/{n}/sign） */
export interface SignPartResult {
  uploadUrl: string
  expiresAt?: string | null
  requiredHeaders: Record<string, string>
}

/** CompleteUploadRequest.PartEtag */
export interface PartEtagInput {
  partNumber: number
  storageEtag: string
}

/** FileResponse（complete / delete 等返回） */
export interface FileResult {
  fileId: FileId
  status: FileState
  scanStatus: FileScanStatus
  version: number
  storageTier?: FileStorageTier
  dlpStatus?: FileDlpStatus
}

/** FileAccessResponse（download / preview 授权返回） */
export interface FileAccessResult {
  mode: FileAccessMode
  accessUrl?: string | null
  expiresAt?: string | null
  auditId: FileId
}

/** EntityResponse（创建引用返回） */
export interface EntityResult {
  id: FileId
  version: number
  status: string
}

/** FileController 演示列表条目 */
export interface DemoFileRecord {
  id: FileId
  fileName: string
  contentType: string
  size: number
  tenantId: FileId
}

/** 列表合并视图：本会话创建（来自上传队列）+ 演示列表按 id 去重 */
export interface FileListItem {
  id: FileId
  fileName?: string
  contentType?: string
  size?: number
  status?: FileState
  scanStatus?: FileScanStatus
  storageTier?: FileStorageTier
  dlpStatus?: FileDlpStatus
  version?: number
  source: 'session' | 'demo'
  createdAt?: number
  resourceId?: FileId
  resourceInstanceKey?: string
}

// ---------- V2.0: 动态水印、DLP 深度检测、冷归档与 S204 容灾恢复演练 ----------

export interface DynamicWatermarkSpec {
  fileId: FileId
  watermarkText: string
  subText: string
  opacity: number
  rotateAngle: number
  fontSize: number
  color: string
  signature: string
  issuedAt: string
}

export interface FileDlpInspectionResult {
  fileId: FileId
  dlpStatus: FileDlpStatus
  archiveBombDetected: boolean
  matchedRules: string[]
  detail: string
}

export interface FileDrDrillRecord {
  id: FileId
  tenantId: FileId
  scenarioCode:
    | 'SCENARIO_A_OBJECT_CORRUPTION'
    | 'SCENARIO_B_BUCKET_LOSS'
    | 'SCENARIO_C_DB_PITR'
    | 'SCENARIO_D_FULL_REGION_FAILOVER'
  targetRpoSeconds: number
  actualRpoSeconds: number
  targetRtoSeconds: number
  actualRtoSeconds: number
  verifiedObjects: number
  status: 'PASSED' | 'FAILED'
  executedAt: string
}
