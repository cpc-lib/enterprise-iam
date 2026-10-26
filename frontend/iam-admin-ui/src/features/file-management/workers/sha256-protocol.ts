/**
 * SHA-256 计算协议（主线程 <-> Worker）。
 * Worker 侧对大文件走增量 SHA-256（WebCrypto 无增量接口，整读大文件会占满内存）；
 * 小文件（<= 256MB）在 Worker 内整读后用 WebCrypto digest 一次完成。
 */
export interface Sha256WorkerInboundMessage {
  file: File
}

export type Sha256WorkerOutboundMessage =
  | { type: 'progress'; progress: number }
  | { type: 'done'; hash: string }
  | { type: 'error'; message: string }

export const SHA256_SUBTLE_LIMIT = 256 * 1024 * 1024
