package com.enterprise.iam.common.security.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionProjectionSchemaTest {

    private static final Instant IDLE = Instant.parse("2026-08-12T12:05:00Z");
    private static final Instant ABSOLUTE = Instant.parse("2026-08-12T13:00:00Z");

    @Test
    void roundTripsTheFrozenHashFieldOrder() {
        var projection = projection(SessionProjectionStatus.ACTIVE);
        var encoded = SessionProjectionSchema.encode(projection);
        var values = SessionProjectionSchema.FIELDS.stream().map(encoded::get).toList();

        assertThat(SessionProjectionSchema.decode(values)).isEqualTo(projection);
        assertThat(SessionProjectionSchema.redisKey(10, 30))
                .isEqualTo("iam:session-security:{10:30}");
    }

    @Test
    void rejectsPartialUnknownOrNonCanonicalValues() {
        var encoded = SessionProjectionSchema.encode(projection(
                SessionProjectionStatus.REVOKED));
        var values = new ArrayList<>(SessionProjectionSchema.FIELDS.stream()
                .map(encoded::get).toList());

        values.set(0, "2");
        assertThatThrownBy(() -> SessionProjectionSchema.decode(values))
                .isInstanceOf(SessionProjectionFormatException.class);
        values.set(0, "1");
        values.set(4, "+4");
        assertThatThrownBy(() -> SessionProjectionSchema.decode(values))
                .isInstanceOf(SessionProjectionFormatException.class);
        values.set(4, "4");
        values.set(6, "active");
        assertThatThrownBy(() -> SessionProjectionSchema.decode(values))
                .isInstanceOf(SessionProjectionFormatException.class);
        values.set(6, "ACTIVE");
        values.set(2, null);
        assertThatThrownBy(() -> SessionProjectionSchema.decode(values))
                .isInstanceOf(SessionProjectionFormatException.class);
    }

    @Test
    void distinguishesAnAbsentHashFromACorruptProjection() {
        assertThat(SessionProjectionSchema.isCompletelyAbsent(
                java.util.Collections.nCopies(SessionProjectionSchema.FIELDS.size(), null)))
                .isTrue();
        assertThat(SessionProjectionSchema.isCompletelyAbsent(
                java.util.List.of("1")))
                .isFalse();
    }

    private static SessionSecurityProjection projection(SessionProjectionStatus status) {
        return new SessionSecurityProjection(10, 20, 30, 4, 5, status, IDLE, ABSOLUTE);
    }
}
