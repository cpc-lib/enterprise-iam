package com.enterprise.iam.file.fieldpolicy;

import com.enterprise.iam.common.core.fieldpolicy.FieldPolicyEnforcer;
import com.enterprise.iam.common.core.fieldpolicy.FieldPolicyMetadata;
import com.enterprise.iam.common.core.fieldpolicy.ObligationFieldPolicyResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demonstrates the end-to-end field-grant enforcement chain: authz
 * obligations → FieldPolicyMetadata → FieldPolicyEnforcer.filterResponse
 * (read) and FieldPolicyEnforcer.guardWrite (write).
 */
class ObligationFieldPolicyEnforcementTest {

    @Test
    void obligationsFromAuthzResolveToMasksAppliedByEnforcer() {
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "301", "policy", "READ"),
                        Map.of("fieldId", "302", "policy", "MASK", "maskStrategyId", "800"),
                        Map.of("fieldId", "303", "policy", "HIDE")));

        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("file", obligations);

        Map<String, Object> fileMetadata = new LinkedHashMap<>();
        fileMetadata.put("301", "alice@example.com");
        fileMetadata.put("302", "confidential-value");
        fileMetadata.put("303", "secret-data");
        fileMetadata.put("999", "unregistered-field");

        Map<String, Object> filtered = FieldPolicyEnforcer.filterResponse(fileMetadata, metadata);

        assertThat(filtered).containsEntry("301", "alice@example.com");
        assertThat(filtered.get("302")).asString().isNotEqualTo("confidential-value");
        assertThat(filtered).doesNotContainKey("303");
        assertThat(filtered).doesNotContainKey("999");
    }

    @Test
    void emptyObligationsYieldAllHidden() {
        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("file", Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fieldA", "value");

        Map<String, Object> filtered = FieldPolicyEnforcer.filterResponse(response, metadata);
        assertThat(filtered).isEmpty();
    }

    @Test
    void writableFlagDrivesGuardWriteAllowingExplicitlyMutableFields() {
        // SPEC 26 §66: phone readable+writable, salary readable but
        // WRITE=false. A PATCH touching salary must be rejected before the DB
        // write; a PATCH touching only phone passes.
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "phone", "policy", "MASK",
                                "writable", true, "maskStrategyId", "800"),
                        Map.of("fieldId", "salary", "policy", "READ",
                                "writable", false)));
        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("employee", obligations);

        Map<String, Object> allowedPatch = new LinkedHashMap<>();
        allowedPatch.put("phone", "13800001111");
        FieldPolicyEnforcer.guardWrite(allowedPatch, metadata);

        Map<String, Object> deniedPatch = new LinkedHashMap<>();
        deniedPatch.put("salary", 99999);
        assertThatThrownBy(() -> FieldPolicyEnforcer.guardWrite(deniedPatch, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salary");

        Map<String, Object> unknownFieldPatch = new LinkedHashMap<>();
        unknownFieldPatch.put("idNumber", "x");
        assertThatThrownBy(() -> FieldPolicyEnforcer.guardWrite(unknownFieldPatch, metadata))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingWritableFlagFailsClosedForWrites() {
        // Obligations produced before the writable flag existed carry no
        // write decision; the write direction must fail closed.
        Map<String, Object> obligations = Map.of(
                "fieldGrants", List.of(
                        Map.of("fieldId", "phone", "policy", "READ")));
        FieldPolicyMetadata metadata =
                ObligationFieldPolicyResolver.fromObligations("employee", obligations);

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("phone", "13800001111");
        assertThatThrownBy(() -> FieldPolicyEnforcer.guardWrite(patch, metadata))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
