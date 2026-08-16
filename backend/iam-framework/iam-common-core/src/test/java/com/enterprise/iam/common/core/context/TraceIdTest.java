package com.enterprise.iam.common.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TraceIdTest {

    @Test
    void trimsAndPreservesValidValue() {
        TraceId traceId = TraceId.of("  trace-01  ");

        assertThat(traceId.value()).isEqualTo("trace-01");
        assertThat(traceId).hasToString("trace-01");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> TraceId.of("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsOversizedValue() {
        String oversized = "x".repeat(TraceId.MAX_LENGTH + 1);

        assertThatThrownBy(() -> TraceId.of(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128");
    }
}
