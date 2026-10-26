import type { FileScanStatus, FileState, UploadSessionStatus } from '@/types/file'

export interface StatusMeta {
  label: string
  color: string
}

export const FILE_STATE_META: Record<FileState, StatusMeta> = {
  VERIFYING: { label: '校验中', color: 'processing' },
  SCANNING: { label: '扫描中', color: 'processing' },
  AVAILABLE: { label: '可用', color: 'success' },
  QUARANTINED: { label: '已隔离', color: 'warning' },
  INFECTED: { label: '有风险', color: 'error' },
  DELETED: { label: '已删除', color: 'default' },
  ARCHIVED: { label: '已归档', color: 'default' },
}

export const SCAN_STATUS_META: Record<FileScanStatus, StatusMeta> = {
  PENDING: { label: '待扫描', color: 'default' },
  SCANNING: { label: '扫描中', color: 'processing' },
  CLEAN: { label: '无风险', color: 'success' },
  INFECTED: { label: '检出威胁', color: 'error' },
  FAILED: { label: '扫描失败', color: 'warning' },
  NOT_REQUIRED: { label: '无需扫描', color: 'default' },
}

export const UPLOAD_SESSION_STATUS_META: Record<UploadSessionStatus, StatusMeta> = {
  INIT: { label: '初始化', color: 'default' },
  UPLOADING: { label: '上传中', color: 'processing' },
  VERIFYING: { label: '校验中', color: 'processing' },
  MERGING: { label: '合并中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  ABORTED: { label: '已终止', color: 'default' },
  EXPIRED: { label: '已过期', color: 'warning' },
}

export function fileStateMeta(state?: string | null): StatusMeta | undefined {
  return state ? FILE_STATE_META[state as FileState] : undefined
}

export function scanStatusMeta(status?: string | null): StatusMeta | undefined {
  return status ? SCAN_STATUS_META[status as FileScanStatus] : undefined
}
