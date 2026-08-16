package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.session.SessionProjectionSchema;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** Strict HMGET adapter for the authoritative Redis session security hash. */
public final class RedisReactiveSessionSnapshotReader
        implements ReactiveSessionSnapshotReader {

    private final ReactiveHashOperations<String, String, String> hashOperations;

    public RedisReactiveSessionSnapshotReader(ReactiveStringRedisTemplate redisTemplate) {
        this(Objects.requireNonNull(redisTemplate, "redisTemplate must not be null")
                .opsForHash());
    }

    RedisReactiveSessionSnapshotReader(
            ReactiveHashOperations<String, String, String> hashOperations) {
        this.hashOperations = Objects.requireNonNull(
                hashOperations, "hashOperations must not be null");
    }

    @Override
    public Mono<SessionSecurityProjection> find(
            long tenantId,
            long subjectId,
            long sessionId) {
        requirePositive(subjectId, "subjectId");
        String key = SessionProjectionSchema.redisKey(tenantId, sessionId);
        return hashOperations.multiGet(key, SessionProjectionSchema.FIELDS)
                .flatMap(values -> SessionProjectionSchema.isCompletelyAbsent(values)
                        ? Mono.empty()
                        : Mono.just(SessionProjectionSchema.decode(values)));
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
