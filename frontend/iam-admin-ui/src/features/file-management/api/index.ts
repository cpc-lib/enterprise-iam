import http from '@/api/client/http'
import { useAuthStore } from '@/stores/auth'
import type {
  DemoFileRecord,
  DynamicWatermarkSpec,
  EntityResult,
  FileAccessMode,
  FileAccessResult,
  FileDlpInspectionResult,
  FileDrDrillRecord,
  FileId,
  FileReferenceType,
  FileResult,
  PartEtagInput,
  SignPartResult,
  UploadSession,
} from '@/types/file'

export interface CreateUploadInput {
  fileName: string
  fileSize: number
  sha256: string
  declaredContentType: string
  resourceId: string
  resourceInstanceKey: string
  fieldId?: string
}

export interface SignPartInput {
  version: number
  expectedSize: number
  partSha256?: string
}

export interface ReportPartInput {
  version: number
  storageEtag: string
  partSize: number
  partSha256?: string
}

export interface CompleteUploadInput {
  version: number
  wholeSha256: string
  parts: PartEtagInput[]
}

export interface FileAccessInput {
  resourceId: string
  resourceInstanceKey: string
  referenceId?: string
  requestedMode: FileAccessMode
}

export interface CreateFileReferenceInput {
  resourceId: string
  resourceInstanceKey: string
  referenceType: FileReferenceType
  fieldId?: string
  sortOrder?: number
}

function trustedHeaders(): Record<string, string> {
  const userId = useAuthStore.getState().user?.userId
  return userId ? { 'X-User-Id': userId } : {}
}

export async function createUploadSession(input: CreateUploadInput): Promise<UploadSession> {
  const { data } = await http.post<UploadSession>('/api/v1/file-uploads', input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function signUploadPart(
  uploadId: FileId,
  partNumber: number,
  input: SignPartInput,
): Promise<SignPartResult> {
  const { data } = await http.post<SignPartResult>(
    `/api/v1/file-uploads/${uploadId}/parts/${partNumber}/sign`,
    input,
    { headers: trustedHeaders() },
  )
  return data
}

export async function reportUploadedPart(
  uploadId: FileId,
  partNumber: number,
  input: ReportPartInput,
): Promise<void> {
  await http.put(`/api/v1/file-uploads/${uploadId}/parts/${partNumber}`, input, {
    headers: trustedHeaders(),
  })
}

export async function getUploadSession(uploadId: FileId): Promise<UploadSession> {
  const { data } = await http.get<UploadSession>(`/api/v1/file-uploads/${uploadId}`, {
    headers: trustedHeaders(),
  })
  return data
}

export async function abortUploadSession(uploadId: FileId, version: number): Promise<void> {
  await http.delete(`/api/v1/file-uploads/${uploadId}`, {
    headers: { ...trustedHeaders(), 'If-Match-Version': String(version) },
  })
}

export async function completeUpload(
  uploadId: FileId,
  input: CompleteUploadInput,
): Promise<FileResult> {
  const { data } = await http.post<FileResult>(`/api/v1/file-uploads/${uploadId}/complete`, input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function createFileReference(
  fileId: FileId,
  input: CreateFileReferenceInput,
): Promise<EntityResult> {
  const { data } = await http.post<EntityResult>(`/api/v1/files/${fileId}/references`, input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function authorizeFileDownload(
  fileId: FileId,
  input: FileAccessInput,
): Promise<FileAccessResult> {
  const { data } = await http.post<FileAccessResult>(`/api/v1/files/${fileId}/download`, input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function authorizeFilePreview(
  fileId: FileId,
  input: FileAccessInput,
): Promise<FileAccessResult> {
  const { data } = await http.post<FileAccessResult>(`/api/v1/files/${fileId}/preview`, input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function deleteFile(
  fileId: FileId,
  input: { version: number; reason: string },
): Promise<FileResult> {
  const { data } = await http.post<FileResult>(`/api/v1/files/${fileId}/delete`, input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function listDemoFiles(): Promise<DemoFileRecord[]> {
  const { data } = await http.get<DemoFileRecord[]>('/api/v1/files', { headers: trustedHeaders() })
  return data
}

export async function putPartBytes(
  uploadUrl: string,
  blob: Blob,
  requiredHeaders: Record<string, string>,
): Promise<string> {
  const response = await fetch(uploadUrl, { method: 'PUT', headers: { ...requiredHeaders }, body: blob })
  if (!response.ok) {
    throw new Error(`存储网关返回 HTTP ${response.status}`)
  }
  return response.headers.get('ETag') ?? response.headers.get('etag') ?? ''
}

// ---------- V2.0: 动态水印、DLP 深度检测、冷归档与容灾演练 API ----------

export async function fetchDynamicWatermark(fileId: FileId, tenantId: string, userId?: string): Promise<DynamicWatermarkSpec> {
  const { data } = await http.get<DynamicWatermarkSpec>(`/api/v1/files/${fileId}/watermark`, {
    params: { tenantId, userId },
    headers: trustedHeaders(),
  })
  return data
}

export async function inspectFileDlp(
  fileId: FileId,
  input: {
    tenantId: string
    fileName?: string
    compressionRatio?: number
    nestedArchiveDepth?: number
    contentSample?: string
  },
): Promise<FileDlpInspectionResult> {
  const { data } = await http.post<FileDlpInspectionResult>(`/api/v1/files/${fileId}/inspect-dlp`, input, {
    headers: trustedHeaders(),
  })
  return data
}

export async function archiveFileToColdTier(fileId: FileId, tenantId: string): Promise<FileResult> {
  const { data } = await http.post<FileResult>(`/api/v1/files/${fileId}/archive`, null, {
    params: { tenantId },
    headers: trustedHeaders(),
  })
  return data
}

export async function restoreFileFromColdTier(fileId: FileId, tenantId: string): Promise<FileResult> {
  const { data } = await http.post<FileResult>(`/api/v1/files/${fileId}/restore`, null, {
    params: { tenantId },
    headers: trustedHeaders(),
  })
  return data
}

export async function listFileDrDrills(tenantId: string): Promise<FileDrDrillRecord[]> {
  const { data } = await http.get<FileDrDrillRecord[]>('/api/v1/files/dr-drills', {
    params: { tenantId },
    headers: trustedHeaders(),
  })
  return data
}

export async function executeFileDrDrill(input: {
  tenantId: string
  scenarioCode: FileDrDrillRecord['scenarioCode']
  targetRpoSeconds: number
  targetRtoSeconds: number
}): Promise<FileDrDrillRecord> {
  const { data } = await http.post<FileDrDrillRecord>('/api/v1/files/dr-drills', input, {
    headers: trustedHeaders(),
  })
  return data
}
