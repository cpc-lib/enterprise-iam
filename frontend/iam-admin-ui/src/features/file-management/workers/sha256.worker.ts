import { sha256 } from '@noble/hashes/sha2.js'
import type {
  Sha256WorkerInboundMessage,
  Sha256WorkerOutboundMessage,
} from './sha256-protocol'
import { SHA256_SUBTLE_LIMIT } from './sha256-protocol'

const CHUNK_SIZE = 8 * 1024 * 1024

interface WorkerCtx {
  postMessage(message: Sha256WorkerOutboundMessage): void
  close(): void
  onmessage: ((event: MessageEvent<Sha256WorkerInboundMessage>) => void) | null
}

function toHex(bytes: Uint8Array): string {
  let hex = ''
  for (const byte of bytes) {
    hex += byte.toString(16).padStart(2, '0')
  }
  return hex
}

const ctx = self as unknown as WorkerCtx

ctx.onmessage = (event: MessageEvent<Sha256WorkerInboundMessage>) => {
  const file = event.data.file

  const fail = (message: string): void => {
    ctx.postMessage({ type: 'error', message })
    ctx.close()
  }

  // 小文件：Worker 内整读 + WebCrypto 一次摘要（快）
  if (file.size <= SHA256_SUBTLE_LIMIT) {
    file
      .arrayBuffer()
      .then((buffer) => crypto.subtle.digest('SHA-256', buffer))
      .then((digest) => {
        ctx.postMessage({ type: 'done', hash: toHex(new Uint8Array(digest)) })
        ctx.close()
      })
      .catch(() => fail('读取文件内容失败，无法计算校验值'))
    return
  }

  // 大文件：分片读取 + 增量 SHA-256，边读边回报进度
  const hasher = sha256.create()
  let offset = 0
  const readNext = (): void => {
    if (offset >= file.size) {
      ctx.postMessage({ type: 'done', hash: toHex(hasher.digest()) })
      ctx.close()
      return
    }
    const end = Math.min(offset + CHUNK_SIZE, file.size)
    file
      .slice(offset, end)
      .arrayBuffer()
      .then((buffer) => {
        hasher.update(new Uint8Array(buffer))
        offset = end
        ctx.postMessage({ type: 'progress', progress: Math.round((offset / file.size) * 100) })
        readNext()
      })
      .catch(() => fail('读取文件分片失败，无法计算校验值'))
  }
  readNext()
}
