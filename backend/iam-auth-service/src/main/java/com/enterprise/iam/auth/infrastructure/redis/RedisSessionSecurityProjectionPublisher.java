package com.enterprise.iam.auth.infrastructure.redis;

import com.enterprise.iam.auth.application.port.out.ProjectionWriteResult;
import com.enterprise.iam.auth.application.port.out.SessionSecurityProjectionPublisher;
import com.enterprise.iam.common.security.session.SessionProjectionSchema;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Atomically publishes the Gateway security projection. Stale versions and
 * attempts to reactivate a terminal session are ignored; malformed existing
 * hashes fail closed and require repair.
 */
@Component
public final class RedisSessionSecurityProjectionPublisher
        implements SessionSecurityProjectionPublisher {

    static final String MONOTONIC_UPSERT_SCRIPT = """
            local key = KEYS[1]
            local function isPositiveLong(value)
                if not value or string.len(value) > 19
                        or not string.match(value, '^[1-9][0-9]*$') then
                    return false
                end
                if string.len(value) == 19 and value > '9223372036854775807' then
                    return false
                end
                return true
            end
            for index = 2, 6 do
                if not isPositiveLong(ARGV[index]) then
                    return -4
                end
            end
            if not isPositiveLong(ARGV[8]) or not isPositiveLong(ARGV[9])
                    or tonumber(ARGV[8]) > tonumber(ARGV[9]) then
                return -4
            end
            local incomingTokenVersion = tonumber(ARGV[5])
            local incomingSessionVersion = tonumber(ARGV[6])
            local incomingAbsoluteExpiry = tonumber(ARGV[9])
            if not incomingTokenVersion or not incomingSessionVersion
                    or not incomingAbsoluteExpiry then
                return -4
            end

            if redis.call('EXISTS', key) == 1 then
                local current = redis.call('HMGET', key,
                    'schemaVersion', 'tenantId', 'subjectId', 'sessionId',
                    'tokenVersion', 'sessionVersion', 'status',
                    'idleExpiresAtEpochMs', 'absoluteExpiresAtEpochMs')
                for index = 1, 9 do
                    if not current[index] then
                        return -2
                    end
                end
                if current[1] ~= ARGV[1] then
                    return -2
                end
                for index = 2, 6 do
                    if not isPositiveLong(current[index]) then
                        return -2
                    end
                end
                if not isPositiveLong(current[8]) or not isPositiveLong(current[9])
                        or tonumber(current[8]) > tonumber(current[9]) then
                    return -2
                end
                if current[2] ~= ARGV[2] or current[3] ~= ARGV[3]
                        or current[4] ~= ARGV[4] then
                    return -3
                end
                local currentTokenVersion = tonumber(current[5])
                local currentSessionVersion = tonumber(current[6])
                if not currentTokenVersion or not currentSessionVersion then
                    return -2
                end
                if current[7] ~= 'ACTIVE' and current[7] ~= 'REVOKED'
                        and current[7] ~= 'EXPIRED' then
                    return -2
                end
                if incomingTokenVersion < currentTokenVersion
                        or incomingSessionVersion < currentSessionVersion then
                    return 0
                end
                if current[7] ~= 'ACTIVE' and ARGV[7] == 'ACTIVE' then
                    return 0
                end
            end

            redis.call('HSET', key,
                'schemaVersion', ARGV[1],
                'tenantId', ARGV[2],
                'subjectId', ARGV[3],
                'sessionId', ARGV[4],
                'tokenVersion', ARGV[5],
                'sessionVersion', ARGV[6],
                'status', ARGV[7],
                'idleExpiresAtEpochMs', ARGV[8],
                'absoluteExpiresAtEpochMs', ARGV[9])
            redis.call('PEXPIREAT', key, incomingAbsoluteExpiry)
            return 1
            """;

    private static final RedisScript<Long> MONOTONIC_UPSERT =
            new DefaultRedisScript<>(MONOTONIC_UPSERT_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisSessionSecurityProjectionPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public ProjectionWriteResult publish(SessionSecurityProjection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        Map<String, String> values = SessionProjectionSchema.encode(projection);
        Object[] arguments = SessionProjectionSchema.FIELDS.stream()
                .map(values::get)
                .toArray();
        Long result = redisTemplate.execute(
                MONOTONIC_UPSERT,
                List.of(SessionProjectionSchema.redisKey(
                        projection.tenantId(), projection.sessionId())),
                arguments);
        return decodeResult(result);
    }

    static ProjectionWriteResult decodeResult(Long result) {
        if (Long.valueOf(1L).equals(result)) {
            return ProjectionWriteResult.APPLIED;
        }
        if (Long.valueOf(0L).equals(result)) {
            return ProjectionWriteResult.STALE_IGNORED;
        }
        throw new SessionProjectionPublicationException(
                "Redis session projection was not safely published; result=" + result);
    }
}
