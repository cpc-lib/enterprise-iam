/**
 * 生成幂等键（OpenAPI 契约要求 command 请求携带 Idempotency-Key 请求头）。
 * 优先使用 UUIDv4，运行环境缺失时回退到时间戳 + 随机串（≥122 bits 熵不可保证，仅兜底）。
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}${Math.random()
    .toString(36)
    .slice(2)}`
}
