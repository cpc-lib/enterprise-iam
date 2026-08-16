package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionProjectionEventCodecTest {

    private final SessionProjectionEventCodec codec =
            new SessionProjectionEventCodec(new ObjectMapper());

    @Test
    void roundTripsFrozenProjectionSchema() {
        SessionSecurityProjection projection = projection();

        String encoded = codec.encode(projection);

        assertThat(encoded).contains("\"schemaVersion\":1")
                .contains("\"absoluteExpiresAtEpochMs\":1786539600000");
        assertThat(codec.decode(encoded)).isEqualTo(projection);
    }

    @Test
    void rejectsUnknownDuplicateCoercedFractionalAndTrailingInput() {
        String valid = codec.encode(projection());
        String unknown = valid.replaceFirst("\\{", "{\"unknown\":1,");
        String duplicate = valid.replaceFirst(
                "\"tenantId\":10", "\"tenantId\":10,\"tenantId\":10");
        String coerced = valid.replace("\"tenantId\":10", "\"tenantId\":\"10\"");
        String fractional = valid.replace("\"tenantId\":10", "\"tenantId\":10.5");

        assertInvalid(unknown);
        assertInvalid(duplicate);
        assertInvalid(coerced);
        assertInvalid(fractional);
        assertInvalid(valid + " true");
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidExpiryOrder() {
        String valid = codec.encode(projection());
        assertInvalid(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        assertInvalid(valid.replace(
                "\"idleExpiresAtEpochMs\":1786536300000",
                "\"idleExpiresAtEpochMs\":1786539600001"));
    }

    private void assertInvalid(String payload) {
        assertThatThrownBy(() -> codec.decode(payload))
                .isInstanceOf(SessionProjectionEventFormatException.class)
                .hasMessage("session projection event payload is invalid")
                .hasMessageNotContaining(payload);
    }

    static SessionSecurityProjection projection() {
        return new SessionSecurityProjection(
                10,
                20,
                30,
                4,
                5,
                SessionProjectionStatus.ACTIVE,
                Instant.parse("2026-08-12T12:05:00Z"),
                Instant.parse("2026-08-12T13:00:00Z"));
    }
}
