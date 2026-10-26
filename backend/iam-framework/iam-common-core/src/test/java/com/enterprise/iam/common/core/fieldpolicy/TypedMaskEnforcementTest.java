package com.enterprise.iam.common.core.fieldpolicy;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end typed masking: obligations carrying maskStrategyType/maskConfig
 * resolve into MaskStrategySpec entries that drive TypedMasker execution;
 * unknown or absent types fail closed to the generic mask.
 */
class TypedMaskEnforcementTest {

    @Test
    void typedStrategyFromObligationsExecutesOnMaskedField() {
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "phone", "policy", "MASK",
                                "maskStrategyId", 800, "maskStrategyType", "PHONE",
                                "maskConfig", Map.of()),
                        Map.of("fieldId", "email", "policy", "MASK",
                                "maskStrategyId", 801, "maskStrategyType", "EMAIL",
                                "maskConfig", Map.of())));

        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("employee", obligations);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("phone", "13812345678");
        source.put("email", "alice@example.com");
        Map<String, Object> filtered = FieldPolicyEnforcer.filterResponse(source, metadata);

        assertThat(filtered).containsEntry("phone", "138***5678");
        assertThat(filtered).containsEntry("email", "a***@example.com");
    }

    @Test
    void genericPartialConfigFromObligationsIsHonored() {
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "remark", "policy", "MASK",
                                "maskStrategyId", 802,
                                "maskStrategyType", "GENERIC_PARTIAL",
                                "maskConfig", Map.of("keepStart", 2, "keepEnd", 0))));

        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("order", obligations);

        Map<String, Object> filtered = FieldPolicyEnforcer.filterResponse(
                Map.of("remark", "fragile-goods"), metadata);

        assertThat(filtered).containsEntry("remark", "fr***");
    }

    @Test
    void unknownStrategyTypeFallsBackToGenericMask() {
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "phone", "policy", "MASK",
                                "maskStrategyId", 800,
                                "maskStrategyType", "FUTURE_TYPE",
                                "maskConfig", Map.of())));

        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("employee", obligations);

        assertThat(metadata.maskStrategyFor("phone")).isNull();
        Map<String, Object> filtered = FieldPolicyEnforcer.filterResponse(
                Map.of("phone", "13812345678"), metadata);
        // Generic fallback: first + last char only.
        assertThat(filtered).containsEntry("phone", "1***8");
    }

    @Test
    void missingStrategyTypeKeepsLegacyGenericMask() {
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "phone", "policy", "MASK",
                                "maskStrategyId", 800)));

        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("employee", obligations);

        assertThat(metadata.maskStrategyFor("phone")).isNull();
        Map<String, Object> filtered = FieldPolicyEnforcer.filterResponse(
                Map.of("phone", "13812345678"), metadata);
        assertThat(filtered).containsEntry("phone", "1***8");
    }
}
