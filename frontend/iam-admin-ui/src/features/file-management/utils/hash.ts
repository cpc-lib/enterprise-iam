import { ApiError } from '@/api/client/errors'
import type {
  Sha256WorkerInboundMessage,
  Sha256WorkerOutboundMessage,
} from '../workers/sha256-protocol'

const MAX_MAIN_THREAD_BYTES = 128 * 1024 * 1024

function toHex(bytes: Uint8Array): string {
  let hex = ''
  for (const byte of bytes) {
    hex += byte.toString(16).padStart(2, '0')
  }
  return hex
}

/** 回退方案：主线程整读计算（仅小文件，避免冻结 UI） */
async function hashOnMainThread(file: File, onProgress?: (percent: number) => void): Promise<string> {
  if (file.size > MAX_MAIN_THREAD_BYTES) {
    throw new ApiError('IAM_FILE_TOO_LARGE', '当前浏览器不支持后台计算，文件过大无法计算校验值')
  }
  const buffer = await file.arrayBuffer()
  onProgress?.(50)
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  onProgress?.(100)
  return toHex(new Uint8Array(digest))
}

/**
 * 计算文件 SHA-256：优先 Web Worker 后台计算（不冻结 UI），
 * Worker 不可用（如测试环境）时回退主线程整读。
 */
export function hashFileSha256(file: File, onProgress?: (percent: number) => void): Promise<string> {
  return new Promise((resolve, reject) => {
    let worker: Worker
    try {
      worker = new Worker(new URL('../workers/sha256.worker.ts', import.meta.url), { type: 'module' })
    } catch {
      void hashOnMainThread(file, onProgress).then(resolve, reject)
      return
    }

    worker.onmessage = (event: MessageEvent<Sha256WorkerOutboundMessage>) => {
      const message = event.data
      if (message.type === 'progress') {
        onProgress?.(message.progress)
      } else if (message.type === 'done') {
        worker.terminate()
        resolve(message.hash)
      } else {
        worker.terminate()
        reject(new ApiError('IAM_FILE_HASH_FAILED', message.message))
      }
    }
    worker.onerror = () => {
      worker.terminate()
      void hashOnMainThread(file, onProgress).then(resolve, reject)
    }

    const payload: Sha256WorkerInboundMessage = { file }
    worker.postMessage(payload)
  })
}
