package com.enterprise.iam.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Redis-backed {@link ReplayIdempotencyStore} for multi-instance deployments
 * (SPEC 18, SPEC 47 D-16). Every mutation runs as a Lua script so claim,
 * commit and release stay atomic across instances; key expiry carries the
 * TTL, so an expired claim is taken over simply by re-claiming a missing key.
 * Behaviour mirrors {@link InMemoryReplayIdempotencyStore} and
 * {@link JdbcReplayIdempotencyStore}: "same body in flight -> 409",
 * "different body -> 409", and release never removes a committed replay.
 *
 * <p>Hash layout at {@code iam:idem:{tenantId}:{key}}: {@code requestHash}
 * always present; {@code status}, {@code contentType} and {@code body}
 * (Base64) appear only after commit. A null content type is stored as an
 * empty string and decoded back to null.
 */
public final class RedisReplayIdempotencyStore implements ReplayIdempotencyStore {

    private static final String KEY_PREFIX = "iam:idem:";

    private static final String CLAIM_SCRIPT = """
            local key = KEYS[1]
            if redis.call('EXISTS', key) == 0 then
                redis.call('HSET', key, 'requestHash', ARGV[1])
                redis.call('PEXPIRE', key, ARGV[2])
                return {1}
            end
            if redis.call('HGET', key, 'requestHash') ~= ARGV[1] then
                return {2}
            end
            local status = redis.call('HGET', key, 'status')
            if not status then
                return {3}
            end
            local contentType = redis.call('HGET', key, 'contentType')
            local body = redis.call('HGET', key, 'body')
            return {4, status, contentType or '', body or ''}
            """;

    private static final String COMMIT_SCRIPT = """
            local key = KEYS[1]
            if redis.call('EXISTS', key) == 1
                    and not redis.call('HGET', key, 'status') then
                redis.call('HSET', key,
                    'status', ARGV[1], 'contentType', ARGV[2], 'body', ARGV[3])
                return 1
            end
            return 0
            """;

    private static final String RELEASE_SCRIPT = """
            local key = KEYS[1]
            if redis.call('EXISTS', key) == 1
                    and not redis.call('HGET', key, 'status') then
                return redis.call('DEL', key)
            end
            return 0
            """;

    private static final RedisScript<List> CLAIM =
            new DefaultRedisScript<>(CLAIM_SCRIPT, List.class);
    private static final RedisScript<Long> COMMIT =
            new DefaultRedisScript<>(COMMIT_SCRIPT, Long.class);
    private static final RedisScript<Long> RELEASE =
            new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisReplayIdempotencyStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    }

    @Override
    public ClaimOutcome claim(long tenantId, String key, String requestHash, Instant now) {
        List<?> result = redisTemplate.execute(CLAIM, List.of(redisKey(tenantId, key)),
                requestHash, String.valueOf(ttl.toMillis()));
        return decodeClaim(result);
    }

    @Override
    public void commit(long tenantId, String key, CachedHttpResponse response, Instant now) {
        redisTemplate.execute(COMMIT, List.of(redisKey(tenantId, key)),
                String.valueOf(response.status()),
                response.contentType() == null ? "" : response.contentType(),
                Base64.getEncoder().encodeToString(response.body()));
    }

    @Override
    public void release(long tenantId, String key) {
        redisTemplate.execute(RELEASE, List.of(redisKey(tenantId, key)));
    }

    static String redisKey(long tenantId, String key) {
        return KEY_PREFIX + tenantId + ":" + key;
    }

    private static ClaimOutcome decodeClaim(List<?> result) {
        if (result == null || result.isEmpty() || !(result.get(0) instanceof Number outcome)) {
            // Fail closed: an unreadable store must not replay or double-execute.
            throw new IllegalStateException("Redis idempotency claim returned no usable result");
        }
        return switch ((int) outcome.longValue()) {
            case 1 -> new ClaimOutcome.New();
            case 2 -> new ClaimOutcome.DifferentBody();
            case 3 -> new ClaimOutcome.InFlight();
            case 4 -> new ClaimOutcome.Replay(decodeResponse(result));
            default -> throw new IllegalStateException(
                    "Redis idempotency claim returned unknown outcome " + outcome);
        };
    }

    private static CachedHttpResponse decodeResponse(List<?> result) {
        int status = Integer.parseInt(String.valueOf(result.get(1)));
        String contentType = result.get(2) == null ? "" : String.valueOf(result.get(2));
        String body = result.get(3) == null ? "" : String.valueOf(result.get(3));
        return new CachedHttpResponse(
                status,
                contentType.isEmpty() ? null : contentType,
                Base64.getDecoder().decode(body));
    }
}
