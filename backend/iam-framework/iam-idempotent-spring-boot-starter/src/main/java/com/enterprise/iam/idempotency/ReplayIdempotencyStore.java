package com.enterprise.iam.idempotency;

import java.time.Instant;

/**
 * 支持响应重放的幂等存储。V1 默认实现为单节点内存存储；
 * 多实例生产部署必须替换为 JDBC/Redis 实现（SPEC 18，V1.2）。
 */
public interface ReplayIdempotencyStore {

    /**
     * 原子占用 (tenantId, key)。
     *
     * @param requestHash 方法+路径+请求体的哈希；同 key 不同哈希必须冲突
     * @return 占用结果
     */
    ClaimOutcome claim(long tenantId, String key, String requestHash, Instant now);

    /** 下游成功后提交响应快照。 */
    void commit(long tenantId, String key, CachedHttpResponse response, Instant now);

    /** 下游失败时释放占用，使同一 key 可被安全重试。 */
    void release(long tenantId, String key);

    sealed interface ClaimOutcome {
        /** 首次占用成功。 */
        record New() implements ClaimOutcome {
        }

        /** 同 key 已携带不同请求体（SPEC 36 §22 → 409）。 */
        record DifferentBody() implements ClaimOutcome {
        }

        /** 同请求体重入，但响应尚未提交（并发重复或原请求失败中断）→ 409。 */
        record InFlight() implements ClaimOutcome {
        }

        /** 同请求体重入且已有成功响应，可直接重放。 */
        record Replay(CachedHttpResponse response) implements ClaimOutcome {
        }
    }
}
