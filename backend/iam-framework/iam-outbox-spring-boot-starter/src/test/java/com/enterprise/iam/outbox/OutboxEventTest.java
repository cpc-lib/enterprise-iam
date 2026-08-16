package com.enterprise.iam.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    @Test
    void acceptsBoundedMetadataAndPayload() {
        assertThatCode(() -> event("{}"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidTypesAndUtf8PayloadAboveByteLimit() {
        assertThatThrownBy(() -> new OutboxEvent(
                1, 2, 3, "bad type", 4, 5, "event.type", 1,
                "{}", 0, Instant.EPOCH.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        String oversized = "界".repeat(OutboxEvent.MAX_PAYLOAD_BYTES / 3 + 1);
        assertThatThrownBy(() -> event(oversized))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxEvent event(String payload) {
        return new OutboxEvent(
                1, 2, 3, "LOGIN_SESSION", 4, 5,
                "iam.auth.session-projection", 1, payload, 0,
                Instant.EPOCH.plusSeconds(1));
    }
}
