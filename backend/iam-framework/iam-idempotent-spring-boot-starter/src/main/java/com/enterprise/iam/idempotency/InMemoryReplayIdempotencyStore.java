package com.enterprise.iam.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单节点内存实现（ConcurrentHashMap，原子 claim）。重启丢失重放缓存，
 * 届时同 key 重试得到 409 IN_FLIGHT 而非重复执行业务 —— 仍然安全。
 */
public final class InMemoryReplayIdempotencyStore implements ReplayIdempotencyStore {

    private record Entry(String requestHash, Instant expiresAt, CachedHttpResponse response) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryReplayIdempotencyStore(Duration ttl) {
        this.ttl = ttl;
    }

    private static String mapKey(long tenantId, String key) {
        return tenantId + ":" + key;
    }

    @Override
    public ClaimOutcome claim(long tenantId, String key, String requestHash, Instant now) {
        String mapKey = mapKey(tenantId, key);
        while (true) {
            Entry fresh = new Entry(requestHash, now.plus(ttl), null);
            Entry existing = store.putIfAbsent(mapKey, fresh);
            if (existing == null) {
                return new ClaimOutcome.New();
            }
            if (!existing.expiresAt().isAfter(now)) {
                // 过期：抢到删除权则重建，否则重读最新条目
                store.remove(mapKey, existing);
                continue;
            }
            if (!existing.requestHash().equals(requestHash)) {
                return new ClaimOutcome.DifferentBody();
            }
            return existing.response() == null
                    ? new ClaimOutcome.InFlight()
                    : new ClaimOutcome.Replay(existing.response());
        }
    }

    @Override
    public void commit(long tenantId, String key, CachedHttpResponse response, Instant now) {
        store.computeIfPresent(mapKey(tenantId, key), (k, e) ->
                new Entry(e.requestHash(), e.expiresAt(), response));
    }

    @Override
    public void release(long tenantId, String key) {
        store.remove(mapKey(tenantId, key));
    }
}
