package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.session.SessionProjectionFormatException;
import com.enterprise.iam.common.security.session.SessionProjectionSchema;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveHashOperations;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisReactiveSessionSnapshotReaderTest {

    @SuppressWarnings("unchecked")
    private final ReactiveHashOperations<String, String, String> hashOperations =
            mock(ReactiveHashOperations.class);
    private final RedisReactiveSessionSnapshotReader reader =
            new RedisReactiveSessionSnapshotReader(hashOperations);

    @Test
    void readsTheSharedProjectionInFrozenFieldOrder() {
        SessionSecurityProjection projection = projection();
        var encoded = SessionProjectionSchema.encode(projection);
        var values = SessionProjectionSchema.FIELDS.stream().map(encoded::get).toList();
        when(hashOperations.multiGet(
                SessionProjectionSchema.redisKey(10, 30),
                SessionProjectionSchema.FIELDS)).thenReturn(Mono.just(values));

        assertThat(reader.find(10, 20, 30).block()).isEqualTo(projection);
    }

    @Test
    void mapsACompletelyMissingHashToEmptyButRejectsPartialState() {
        String key = SessionProjectionSchema.redisKey(10, 30);
        when(hashOperations.multiGet(key, SessionProjectionSchema.FIELDS))
                .thenReturn(Mono.just(Collections.nCopies(
                        SessionProjectionSchema.FIELDS.size(), null)));
        assertThat(reader.find(10, 20, 30).blockOptional()).isEmpty();

        var partial = new ArrayList<String>(Collections.nCopies(
                SessionProjectionSchema.FIELDS.size(), null));
        partial.set(0, SessionProjectionSchema.SCHEMA_VERSION);
        when(hashOperations.multiGet(key, SessionProjectionSchema.FIELDS))
                .thenReturn(Mono.just(partial));
        assertThatThrownBy(() -> reader.find(10, 20, 30).block())
                .isInstanceOf(SessionProjectionFormatException.class);
    }

    @Test
    void propagatesRedisFailureWithoutAnInMemoryFallback() {
        when(hashOperations.multiGet(
                SessionProjectionSchema.redisKey(10, 30),
                SessionProjectionSchema.FIELDS))
                .thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));

        assertThatThrownBy(() -> reader.find(10, 20, 30).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis unavailable");
    }

    private static SessionSecurityProjection projection() {
        return new SessionSecurityProjection(
                10, 20, 30, 4, 5, SessionProjectionStatus.ACTIVE,
                Instant.parse("2026-08-12T12:05:00Z"),
                Instant.parse("2026-08-12T13:00:00Z"));
    }
}
