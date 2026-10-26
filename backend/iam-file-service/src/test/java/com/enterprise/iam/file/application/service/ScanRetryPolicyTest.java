package com.enterprise.iam.file.application.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanRetryPolicyTest {

    private final ScanRetryPolicy policy = new ScanRetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10));

    @Test
    void backoffDoublesFromBaseAndCaps() {
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(policy.backoffFor(20)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void lowCapIsHonoured() {
        var capped = new ScanRetryPolicy(Duration.ofSeconds(30), Duration.ofSeconds(100));
        assertThat(capped.backoffFor(3)).isEqualTo(Duration.ofSeconds(100));
    }

    @Test
    void attemptMustBePositive() {
        assertThatThrownBy(() -> policy.backoffFor(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
