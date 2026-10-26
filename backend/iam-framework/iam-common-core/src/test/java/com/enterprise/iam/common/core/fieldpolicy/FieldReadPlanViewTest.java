package com.enterprise.iam.common.core.fieldpolicy;

import com.enterprise.iam.common.core.masking.MaskStrategyType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FieldReadPlanViewTest {

    private static final String EXPIRES = "2026-09-26T12:05:00Z";

    private static Map<String, Object> baseWire() {
        return new HashMap<>(Map.of(
                "ref", "iam:field-read:1001",
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
                Map.of("fieldId", "101", "policy", "READ"),
                Map.of("fieldId", "102", "policy", "HIDE"),
                Map.of("fieldId", "103", "policy", "MASK",
                        "maskStrategyType", "PHONE",
                        "maskConfig", Map.of("visibleTail", "4"))));

        Optional<FieldReadPlanView> view = FieldReadPlanView.fromArtifact(wire);

        assertThat(view).isPresent();
        FieldReadPlanView parsed = view.get();
        assertThat(parsed.tenantId()).isEqualTo(1L);
        assertThat(parsed.permissionVersion()).isEqualTo(5L);
        assertThat(parsed.expiresAt()).isEqualTo(Instant.parse(EXPIRES));
        assertThat(parsed.bindsTo(1L, 100L, 300L, 400L, 5L)).isTrue();
        assertThat(parsed.bindsTo(1L, 100L, 300L, 400L, 6L)).isFalse();
        assertThat(parsed.policyFields())
                .containsEntry("101", FieldPolicy.READ)
                .containsEntry("102", FieldPolicy.HIDE)
                .containsEntry("103", FieldPolicy.MASK);
        assertThat(parsed.maskStrategyFields()).containsOnlyKeys("103");
        assertThat(parsed.maskStrategyFields().get("103").type())
                .isEqualTo(MaskStrategyType.PHONE);
    }

    @Test
    void parsesNumericIdsAsWell() {
        Map<String, Object> wire = baseWire();
        wire.put("tenantId", 1);
        wire.put("entries", List.of(Map.of("fieldId", 101, "policy", "READ")));

        Optional<FieldReadPlanView> view = FieldReadPlanView.fromArtifact(wire);

        assertThat(view).isPresent();
        assertThat(view.get().entries().get(0).fieldId()).isEqualTo(101L);
    }

    @Test
    void emptyWhenEntryPolicyUnknown() {
        Map<String, Object> wire = baseWire();
        wire.put("entries", List.of(Map.of("fieldId", "101", "policy", "WRITE_DENIED")));

        assertThat(FieldReadPlanView.fromArtifact(wire)).isEmpty();
    }

    @Test
    void emptyWhenMaskTypeUnknown() {
        Map<String, Object> wire = baseWire();
        wire.put("entries", List.of(Map.of(
                "fieldId", "101", "policy", "MASK", "maskStrategyType", "SQL_DROP")));

        assertThat(FieldReadPlanView.fromArtifact(wire)).isEmpty();
    }

    @Test
    void emptyWhenBindingMissing() {
        Map<String, Object> wire = baseWire();
        wire.remove("resourceId");

        assertThat(FieldReadPlanView.fromArtifact(wire)).isEmpty();
    }

    @Test
    void emptyWhenEntriesMalformed() {
        Map<String, Object> wire = baseWire();
        wire.put("entries", List.of("not-an-object"));

        assertThat(FieldReadPlanView.fromArtifact(wire)).isEmpty();
    }
}
