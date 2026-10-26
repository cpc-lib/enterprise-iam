import { dayjs } from '@/utils/dayjs'

/** 人类可读的文件大小：B / KB / MB / GB / TB */
export function humanFileSize(size?: number | null): string {
  if (size === null || size === undefined || Number.isNaN(size)) return '—'
  if (size < 1024) return `${size} B`
  const units = ['KB', 'MB', 'GB', 'TB'] as const
  let value = size / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unitIndex]}`
}

/** 统一的时间展示（缺失或非法值显示 —） */
export function formatDateTime(input?: string | number | null): string {
  if (input === null || input === undefined || input === '') return '—'
  const parsed = dayjs(input)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : '—'
}

/** 是否为可内嵌预览的图片类型 */
export function isImageContentType(contentType?: string | null): boolean {
  return !!contentType && contentType.toLowerCase().startsWith('image/')
}
