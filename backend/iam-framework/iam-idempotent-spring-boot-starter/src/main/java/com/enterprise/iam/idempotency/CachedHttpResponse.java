package com.enterprise.iam.idempotency;

/**
 * 已完成请求的可重放响应快照（仅 2xx 才会被缓存）。
 */
public record CachedHttpResponse(int status, String contentType, byte[] body) {
}
