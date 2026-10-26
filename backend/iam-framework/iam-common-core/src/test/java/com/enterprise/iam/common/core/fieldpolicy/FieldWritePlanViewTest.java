package com.enterprise.iam.common.core.fieldpolicy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FieldWritePlanViewTest {

    private static final String EXPIRES = "2026-09-26T12:05:00Z";

    private static Map<String, Object> baseWire() {
        return new HashMap<>(Map.of(
                "ref", "iam:field-write:1001",
                "tenantId", "1",
                "subjectId", "100",
                "resourceId", "300",
                "operationId", "400",
                "permissionVersion", "5",
                "expiresAt", EXPIRES));
    }

    @Test
    void parsesWellFormedArtifactWithStringIds() {
        Map<String, Object> wire = baseWire();
        wire.put("entries", List.of(
                Map.of("fieldId", "101", "writable", true),
                Map.of("fieldId", "102", "writable", false)));

        Optional<FieldWritePlanView> view = FieldWritePlanView.fromArtifact(wire);

        assertThat(view).isPresent();
        FieldWritePlanView parsed = view.get();
        assertThat(parsed.permissionVersion()).isEqualTo(5L);
        assertThat(parsed.expiresAt()).isEqualTo(Instant.parse(EXPIRES));
        assertThat(parsed.bindsTo(1L, 100L, 300L, 400L, 5L)).isTrue();
        assertThat(parsed.writableFields())
                .containsEntry("101", true)
                .containsEntry("102", false);
    }

    @Test
    void parsesNumericIdsAsWell() {
        Map<String, Object> wire = baseWire();
        wire.put("entries", List.of(Map.of("fieldId", 101, "writable", true)));

        Optional<FieldWritePlanView> view = FieldWritePlanView.fromArtifact(wire);

        assertThat(view).isPresent();
        assertThat(view.get().entries().get(0).fieldId()).isEqualTo(101L);
        assertThat(view.get().entries().get(0).writable()).isTrue();
    }

    @Test
    void emptyWhenWritableNotBoolean() {
        Map<String, Object> wire = baseWire();
        wire.put("entries", List.of(Map.of("fieldId", "101", "writable", "yes")));

        assertThat(FieldWritePlanView.fromArtifact(wire)).isEmpty();
    }

    @Test
    void emptyWhenBindingMissing() {
        Map<String, Object> wire = baseWire();
        wire.remove("operationId");

        assertThat(FieldWritePlanView.fromArtifact(wire)).isEmpty();
    }

    @Test
    void emptyWhenExpiresMalformed() {
        Map<String, Object> wire = baseWire();
        wire.put("expiresAt", "not-a-timestamp");

        assertThat(FieldWritePlanView.fromArtifact(wire)).isEmpty();
    }
}
