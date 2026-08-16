package com.enterprise.iam.auth.infrastructure.redis;

import com.enterprise.iam.auth.application.port.out.ProjectionWriteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSessionSecurityProjectionPublisherTest {

    @Test
    void freezesAtomicExpiryVersionAndTerminalStateControls() {
        assertThat(RedisSessionSecurityProjectionPublisher.MONOTONIC_UPSERT_SCRIPT)
                .contains("HMGET", "HSET", "PEXPIREAT")
                .contains("isPositiveLong", "9223372036854775807")
                .contains("incomingTokenVersion < currentTokenVersion")
                .contains("incomingSessionVersion < currentSessionVersion")
                .contains("current[7] ~= 'ACTIVE' and ARGV[7] == 'ACTIVE'");
    }

    @Test
    void distinguishesAppliedStaleAndIndeterminateResults() {
        assertThat(RedisSessionSecurityProjectionPublisher.decodeResult(1L))
                .isEqualTo(ProjectionWriteResult.APPLIED);
        assertThat(RedisSessionSecurityProjectionPublisher.decodeResult(0L))
                .isEqualTo(ProjectionWriteResult.STALE_IGNORED);
        assertThatThrownBy(() ->
                RedisSessionSecurityProjectionPublisher.decodeResult(-2L))
                .isInstanceOf(SessionProjectionPublicationException.class);
        assertThatThrownBy(() ->
                RedisSessionSecurityProjectionPublisher.decodeResult(null))
                .isInstanceOf(SessionProjectionPublicationException.class);
    }
}
