import { useCallback, useMemo } from 'react'
import { ApiError, resolveErrorMessage } from '@/api/client/errors'
import {
  abortUploadSession,
  completeUpload,
  createUploadSession,
  putPartBytes,
  reportUploadedPart,
  signUploadPart,
} from '../api'
import { useUploadQueue } from '../store/uploadQueue'
import type { UploadTask } from '../store/uploadQueue'
import { hashFileSha256 } from '../utils/hash'
import type { PartEtagInput } from '@/types/file'

const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms))

/** 开发模拟完成使用的合成 etag 前缀（仅用于本地走通流程，生产不会出现） */
export const DEV_SIMULATED_ETAG_PREFIX = 'dev-simulated-etag-'
/** 存储 PUT 成功但未返回 ETag 头时的占位 etag */
const MISSING_ETAG_PREFIX = 'no-etag-part-'

function newTaskId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `upload-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function readTask(taskId: string): UploadTask | undefined {
  return useUploadQueue.getState().tasks.find((task) => task.id === taskId)
}

function describeUploadError(error: unknown): string {
  const detail = resolveErrorMessage(error)
  if (error instanceof TypeError || (error instanceof ApiError && error.code === 'NETWORK_ERROR')) {
    return `开发环境存储网关未接入，分片直传失败：${detail}`
  }
  return detail
}

/** 完成会话（真实分片 etag + 缺失部分用合成 etag 补齐） */
async function finishSession(task: UploadTask, partCount: number): Promise<void> {
  const { patchTask } = useUploadQueue.getState()
  if (!task.uploadId || task.version === undefined || !task.sha256) {
    throw new Error('缺少上传会话信息，无法完成合并')
  }
  const parts: PartEtagInput[] = []
  for (let partNumber = 1; partNumber <= partCount; partNumber += 1) {
    parts.push({
      partNumber,
      storageEtag:
        task.etags.find((part) => part.partNumber === partNumber)?.storageEtag ??
        `${DEV_SIMULATED_ETAG_PREFIX}${partNumber}`,
    })
  }
  const result = await completeUpload(task.uploadId, {
    version: task.version,
    wholeSha256: task.sha256,
    parts,
  })
  patchTask(task.id, { status: 'completed', progress: 100, fileId: result.fileId, result })
}

/** 单个任务的状态机循环：hashing → creating → uploading → completed */
async function runTask(taskId: string): Promise<void> {
  const { patchTask } = useUploadQueue.getState()

  for (;;) {
    const task = readTask(taskId)
    if (!task) return

    if (task.status === 'canceled') {
      if (task.uploadId && task.version !== undefined) {
        try {
          await abortUploadSession(task.uploadId, task.version)
        } catch {
          // 未成功终止的会话由服务端过期策略回收
        }
      }
      return
    }
    if (task.status === 'paused') {
      await sleep(300)
      continue
    }

    if (task.status === 'hashing') {
      try {
        const sha256 = await hashFileSha256(task.file, (progress) =>
          patchTask(taskId, { hashProgress: progress }),
        )
        patchTask(taskId, { status: 'creating', hashProgress: 100, sha256 })
      } catch (error) {
        patchTask(taskId, { status: 'failed', error: resolveErrorMessage(error) })
        return
      }
      continue
    }

    if (task.status === 'creating') {
      try {
        const session = await createUploadSession({
          fileName: task.fileName,
          fileSize: task.fileSize,
          sha256: task.sha256 ?? '',
          declaredContentType: task.contentType,
          resourceId: task.resourceId,
          resourceInstanceKey: task.resourceInstanceKey,
        })
        if (session.mode === 'INSTANT') {
          patchTask(taskId, {
            status: 'completed',
            progress: 100,
            mode: 'INSTANT',
            uploadId: session.uploadId ?? undefined,
            fileId: session.fileId ?? undefined,
            result: session.fileId
              ? {
                  fileId: session.fileId,
                  status: session.status === 'COMPLETED' ? 'AVAILABLE' : 'VERIFYING',
                  scanStatus: 'PENDING',
                  version: session.version,
                }
              : undefined,
          })
          return
        }
        patchTask(taskId, {
          status: 'uploading',
          mode: 'MULTIPART',
          uploadId: session.uploadId ?? undefined,
          version: session.version,
          partSize: session.partSize ?? undefined,
          partCount: session.partCount ?? undefined,
          nextPart: 1,
          etags: [],
        })
      } catch (error) {
        patchTask(taskId, { status: 'failed', error: resolveErrorMessage(error) })
        return
      }
      continue
    }

    if (task.status === 'uploading') {
      const { uploadId, version, partSize, partCount, nextPart, file, fileSize } = task
      if (!uploadId || version === undefined || !partSize || !partCount) {
        patchTask(taskId, { status: 'failed', error: '上传会话信息缺失，请重新上传' })
        return
      }
      if (nextPart > partCount) {
        try {
          await finishSession(task, partCount)
        } catch (error) {
          patchTask(taskId, { status: 'failed', error: resolveErrorMessage(error) })
        }
        return
      }
      try {
        const start = (nextPart - 1) * partSize
        const end = Math.min(start + partSize, fileSize)
        const sign = await signUploadPart(uploadId, nextPart, {
          version,
          expectedSize: Math.max(end - start, 1),
        })
        const etag = await putPartBytes(sign.uploadUrl, file.slice(start, end), sign.requiredHeaders ?? {})
        await reportUploadedPart(uploadId, nextPart, {
          version,
          storageEtag: etag || `${MISSING_ETAG_PREFIX}${nextPart}`,
          partSize: Math.max(end - start, 1),
        })
        const doneBytes = Math.min(nextPart * partSize, fileSize)
        patchTask(taskId, {
          nextPart: nextPart + 1,
          etags: [...task.etags, { partNumber: nextPart, storageEtag: etag || `${MISSING_ETAG_PREFIX}${nextPart}` }],
          progress: Math.round((doneBytes / fileSize) * 100),
        })
      } catch (error) {
        patchTask(taskId, {
          status: 'failed',
          failedPart: nextPart,
          error: describeUploadError(error),
        })
        return
      }
      continue
    }

    // failed / completed：等待用户操作
    return
  }
}

export interface UploadContext {
  resourceId: string
  resourceInstanceKey: string
}

/** 上传会话生命周期：入队 → 指纹 → 建会话 → 分片循环 → 完成 */
export function useFileUploader() {
  const enqueueFiles = useCallback((files: File[], context: UploadContext): void => {
    for (const file of files) {
      if (file.size < 1) continue
      const task: UploadTask = {
        id: newTaskId(),
        file,
        fileName: file.name,
        fileSize: file.size,
        contentType: file.type || 'application/octet-stream',
        resourceId: context.resourceId,
        resourceInstanceKey: context.resourceInstanceKey,
        status: 'hashing',
        hashProgress: 0,
        progress: 0,
        nextPart: 1,
        etags: [],
        createdAt: Date.now(),
      }
      useUploadQueue.getState().addTask(task)
      void runTask(task.id)
    }
  }, [])

  const pauseTask = useCallback((id: string): void => {
    const task = readTask(id)
    if (task?.status === 'uploading') useUploadQueue.getState().patchTask(id, { status: 'paused' })
  }, [])

  const resumeTask = useCallback((id: string): void => {
    const task = readTask(id)
    if (task?.status === 'paused') {
      useUploadQueue.getState().patchTask(id, { status: 'uploading' })
      void runTask(id)
    }
  }, [])

  const cancelTask = useCallback((id: string): void => {
    const task = readTask(id)
    if (task && ['hashing', 'creating', 'uploading', 'paused'].includes(task.status)) {
      useUploadQueue.getState().patchTask(id, { status: 'canceled' })
      void runTask(id)
    }
  }, [])

  const retryTask = useCallback((id: string): void => {
    const task = readTask(id)
    if (task?.status !== 'failed') return
    useUploadQueue.getState().patchTask(id, {
      status: task.uploadId ? 'uploading' : 'hashing',
      error: undefined,
    })
    void runTask(id)
  }, [])

  /** 开发模拟：跳过分片直传，用合成 etag 直接请求 complete 走通后续流程 */
  const simulateComplete = useCallback(async (id: string): Promise<void> => {
    const task = readTask(id)
    if (!task || task.status !== 'failed' || !task.partCount) {
      throw new Error('当前任务不支持模拟完成')
    }
    await finishSession(task, task.partCount)
  }, [])

  return useMemo(
    () => ({ enqueueFiles, pauseTask, resumeTask, cancelTask, retryTask, simulateComplete }),
    [enqueueFiles, pauseTask, resumeTask, cancelTask, retryTask, simulateComplete],
  )
}
