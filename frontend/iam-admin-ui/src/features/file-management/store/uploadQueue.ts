import { create } from 'zustand'
import type { FileId, FileResult, PartEtagInput, UploadMode } from '@/types/file'

export type UploadTaskStatus =
  | 'hashing'
  | 'creating'
  | 'uploading'
  | 'paused'
  | 'completed'
  | 'failed'
  | 'canceled'

export interface UploadTask {
  id: string
  file: File
  fileName: string
  fileSize: number
  contentType: string
  resourceId: FileId
  resourceInstanceKey: string
  status: UploadTaskStatus
  /** 指纹计算进度（hashing 阶段展示） */
  hashProgress: number
  /** 上传总进度（0-100） */
  progress: number
  mode?: UploadMode
  uploadId?: FileId
  /** 完成后的文件 ID（用于文件列表合并） */
  fileId?: FileId
  version?: number
  sha256?: string
  partSize?: number
  partCount?: number
  /** 下一个待上传分片号（从 1 开始） */
  nextPart: number
  failedPart?: number
  etags: PartEtagInput[]
  error?: string
  result?: FileResult
  createdAt: number
}

interface UploadQueueState {
  tasks: UploadTask[]
  addTask: (task: UploadTask) => void
  patchTask: (id: string, patch: Partial<Omit<UploadTask, 'id'>>) => void
  removeTask: (id: string) => void
  clearFinished: () => void
}

/** 上传队列（全局）：暂停/取消用状态标记，运行中的循环在分片间隙响应 */
export const useUploadQueue = create<UploadQueueState>()((set) => ({
  tasks: [],
  addTask: (task) => set((state) => ({ tasks: [...state.tasks, task] })),
  patchTask: (id, patch) =>
    set((state) => ({
      tasks: state.tasks.map((task) => (task.id === id ? { ...task, ...patch } : task)),
    })),
  removeTask: (id) => set((state) => ({ tasks: state.tasks.filter((task) => task.id !== id) })),
  clearFinished: () =>
    set((state) => ({
      tasks: state.tasks.filter((task) => task.status !== 'completed' && task.status !== 'canceled'),
    })),
}))
