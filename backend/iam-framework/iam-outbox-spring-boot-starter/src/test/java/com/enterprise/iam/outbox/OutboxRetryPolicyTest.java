package com.enterprise.iam.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRetryPolicyTest {

    @Test
    void appliesDeterministicBoundedExponentialBackoff() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(
                Duration.ofSeconds(1), Duration.ofSeconds(5));

        Duration first = policy.delay(41, 1);
        assertThat(first).isEqualTo(policy.delay(41, 1));
        assertThat(first).isBetween(Duration.ofMillis(800), Duration.ofMillis(1_200));
        assertThat(policy.delay(41, 20)).isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void rejectsInvalidDurationsAndAttempts() {
        assertThatThrownBy(() -> new OutboxRetryPolicy(
                Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxRetryPolicy(
                Duration.ofNanos(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        OutboxRetryPolicy policy = new OutboxRetryPolicy(
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertThatThrownBy(() -> policy.delay(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
