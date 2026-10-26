package com.enterprise.iam.file.datascope;

import com.enterprise.iam.common.core.datascope.DataScopeEnforcer;
import com.enterprise.iam.common.core.datascope.DataScopeEnforcer.Principal;
import com.enterprise.iam.common.core.datascope.DataScopeMetadata;
import com.enterprise.iam.common.core.datascope.ObligationDataScopeResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the SPEC 26 §65 acceptance chain: authz dataScopes
 * obligations → DataScopeMetadata → row filtering / detail / update / count
 * parity for TEAM(A).
 */
class ObligationDataScopeEnforcementTest {

    private static final long ALICE = 11L;
    private static final long TEAM_A = 2L;
    private static final long TEAM_B = 3L;

    private final Principal aliceInTeamA = new Principal(
            ALICE, DataScopeEnforcer.teamSet(TEAM_A), Set.of());

    private Map<String, Object> customer(String key, long ownerTeamId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(DataScopeEnforcer.INSTANCE_KEY, key);
        row.put(DataScopeEnforcer.OWNER_TEAM_ID, ownerTeamId);
        return row;
    }

    @Test
    void teamScopeFiltersListsDeniesDetailAndUpdateAndExcludesFromCount() {
        Map<String, Object> obligations = Map.of(
                "dataScopes", List.of(
                        Map.of("scopeType", "TEAM",
                                "scopeCode", "TEAM_A",
                                "mergeMode", "UNION")));
        DataScopeMetadata metadata =
                ObligationDataScopeResolver.fromObligations("customer", obligations);
        Map<String, Object> c1 = customer("C1", TEAM_A);
        Map<String, Object> c2 = customer("C2", TEAM_B);
        List<Map<String, Object>> rows = List.of(c1, c2);

        // list: only C1
        List<Map<String, Object>> visible =
                DataScopeEnforcer.filterRows(rows, metadata, aliceInTeamA);
        assertThat(visible).containsExactly(c1);

        // detail and update on C2 denied
        assertThat(DataScopeEnforcer.allows(c2, metadata, aliceInTeamA)).isFalse();

        // count excludes C2
        long count = rows.stream()
                .filter(row -> DataScopeEnforcer.allows(row, metadata, aliceInTeamA))
                .count();
        assertThat(count).isOne();
    }

    @Test
    void specifiedTeamScopeUsesConcreteTeamIdsFromObligation() {
        Map<String, Object> obligations = Map.of(
                "dataScopes", List.of(
                        Map.of("scopeType", "SPECIFIED_TEAM",
                                "scopeCode", "SPEC_B",
                                "mergeMode", "INTERSECT",
                                "teamIds", List.of(TEAM_B))));
        DataScopeMetadata metadata =
                ObligationDataScopeResolver.fromObligations("customer", obligations);

        assertThat(DataScopeEnforcer.allows(customer("C1", TEAM_A), metadata, aliceInTeamA))
                .isFalse();
        assertThat(DataScopeEnforcer.allows(customer("C2", TEAM_B), metadata, aliceInTeamA))
                .isTrue();
    }

    @Test
    void selfScopeMatchesOnlyRowsOwnedByPrincipal() {
        Map<String, Object> obligations = Map.of(
                "dataScopes", List.of(
                        Map.of("scopeType", "SELF", "mergeMode", "UNION")));
        DataScopeMetadata metadata =
                ObligationDataScopeResolver.fromObligations("customer", obligations);
        Map<String, Object> own = new LinkedHashMap<>();
        own.put(DataScopeEnforcer.INSTANCE_KEY, "C9");
        own.put(DataScopeEnforcer.OWNER_USER_ID, ALICE);
        Map<String, Object> other = new LinkedHashMap<>();
        other.put(DataScopeEnforcer.INSTANCE_KEY, "C8");
        other.put(DataScopeEnforcer.OWNER_USER_ID, 99L);

        assertThat(DataScopeEnforcer.allows(own, metadata, aliceInTeamA)).isTrue();
        assertThat(DataScopeEnforcer.allows(other, metadata, aliceInTeamA)).isFalse();
    }

    @Test
    void missingAndCustomPolicyObligationsFailClosed() {
        DataScopeMetadata absent =
                ObligationDataScopeResolver.fromObligations("customer", Map.of());
        assertThat(DataScopeEnforcer.allows(customer("C1", TEAM_A), absent, aliceInTeamA))
                .isFalse();

        Map<String, Object> custom = Map.of(
                "dataScopes", List.of(
                        Map.of("scopeType", "CUSTOM_POLICY",
                                "customPolicyId", 700,
                                "mergeMode", "UNION")));
        DataScopeMetadata customMetadata =
                ObligationDataScopeResolver.fromObligations("customer", custom);
        assertThat(DataScopeEnforcer.allows(customer("C1", TEAM_A), customMetadata, aliceInTeamA))
                .isFalse();
    }

    @Test
    void unionAndIntersectModesCombineScopes() {
        // SELF ∪ TEAM_A: someone else's row inside team A is visible.
        Map<String, Object> union = Map.of(
                "dataScopes", List.of(
                        Map.of("scopeType", "SELF", "mergeMode", "UNION"),
                        Map.of("scopeType", "TEAM", "mergeMode", "UNION")));
        DataScopeMetadata unionMetadata =
                ObligationDataScopeResolver.fromObligations("customer", union);
        Map<String, Object> teammatesRow = new LinkedHashMap<>();
        teammatesRow.put(DataScopeEnforcer.INSTANCE_KEY, "C7");
        teammatesRow.put(DataScopeEnforcer.OWNER_USER_ID, 99L);
        teammatesRow.put(DataScopeEnforcer.OWNER_TEAM_ID, TEAM_A);
        assertThat(DataScopeEnforcer.allows(teammatesRow, unionMetadata, aliceInTeamA)).isTrue();

        // SELF ∩ TEAM_A: the teammate's row (not self) is denied.
        Map<String, Object> intersect = Map.of(
                "dataScopes", List.of(
                        Map.of("scopeType", "SELF", "mergeMode", "INTERSECT"),
                        Map.of("scopeType", "TEAM", "mergeMode", "INTERSECT")));
        DataScopeMetadata intersectMetadata =
                ObligationDataScopeResolver.fromObligations("customer", intersect);
        assertThat(DataScopeEnforcer.allows(teammatesRow, intersectMetadata, aliceInTeamA))
                .isFalse();
    }
}
