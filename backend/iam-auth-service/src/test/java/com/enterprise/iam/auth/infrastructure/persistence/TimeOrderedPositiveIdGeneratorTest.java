package com.enterprise.iam.auth.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeOrderedPositiveIdGeneratorTest {

    @Test
    void generatesPositiveOrderedUniqueIdsForOneNodeAndMillisecond() {
        TimeOrderedPositiveIdGenerator generator = new TimeOrderedPositiveIdGenerator(
                Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC), 17);

        long first = generator.nextId();
        long second = generator.nextId();

        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        assertThat((first >> 12) & 1_023).isEqualTo(17);
    }

    @Test
    void rejectsInvalidNodeAndClockBeforeEpoch() {
        assertThatThrownBy(() -> new TimeOrderedPositiveIdGenerator(Clock.systemUTC(), 1_024))
                .isInstanceOf(IllegalArgumentException.class);
        TimeOrderedPositiveIdGenerator beforeEpoch = new TimeOrderedPositiveIdGenerator(
                Clock.fixed(Instant.parse("2023-12-31T23:59:59Z"), ZoneOffset.UTC), 1);
        assertThatThrownBy(beforeEpoch::nextId).isInstanceOf(IllegalStateException.class);
    }
}
