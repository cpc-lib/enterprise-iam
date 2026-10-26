package com.enterprise.iam.common.core.datascope;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DataPredicateViewTest {

    private static final String EXPIRES = "2026-09-26T12:05:00Z";

    private static Map<String, Object> baseWire() {
        return new java.util.HashMap<>(Map.of(
                "ref", "iam:data-predicate:1001",
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
        wire.put("scopes", List.of(Map.of(
                "scopeType", "SPECIFIED_TEAM",
                "mergeMode", "UNION",
                "scopeCode", "scope-a",
                "teamIds", List.of("21", "22"))));

        Optional<DataPredicateView> view = DataPredicateView.fromArtifact(wire);

        assertThat(view).isPresent();
        assertThat(view.get().tenantId()).isEqualTo(1L);
        assertThat(view.get().subjectId()).isEqualTo(100L);
        assertThat(view.get().permissionVersion()).isEqualTo(5L);
        assertThat(view.get().expiresAt()).isEqualTo(Instant.parse(EXPIRES));
        assertThat(view.get().scopes()).hasSize(1);
        assertThat(view.get().scopes().get(0).teamIds()).containsExactly(21L, 22L);
    }

    @Test
    void parsesNumericIdsAsWell() {
        Map<String, Object> wire = baseWire();
        wire.put("tenantId", 1);
        wire.put("scopes", List.of(Map.of(
                "scopeType", "SHARED",
                "mergeMode", "INTERSECT",
                "customPolicyId", 55)));

        Optional<DataPredicateView> view = DataPredicateView.fromArtifact(wire);

        assertThat(view).isPresent();
        assertThat(view.get().scopes().get(0).customPolicyId()).isEqualTo(55L);
    }

    @Test
    void missingScopesYieldsEmptyList() {
        Optional<DataPredicateView> view = DataPredicateView.fromArtifact(baseWire());
        assertThat(view).isPresent();
        assertThat(view.get().scopes()).isEmpty();
    }

    @Test
    void malformedFieldsFailClosed() {
        Map<String, Object> noRef = baseWire();
        noRef.remove("ref");
        assertThat(DataPredicateView.fromArtifact(noRef)).isEmpty();

        Map<String, Object> badExpiry = baseWire();
        badExpiry.put("expiresAt", "not-a-date");
        assertThat(DataPredicateView.fromArtifact(badExpiry)).isEmpty();

        Map<String, Object> badId = baseWire();
        badId.put("tenantId", "abc");
        assertThat(DataPredicateView.fromArtifact(badId)).isEmpty();

        Map<String, Object> badScope = baseWire();
        badScope.put("scopes", List.of(Map.of("mergeMode", "UNION")));
        assertThat(DataPredicateView.fromArtifact(badScope)).isEmpty();

        Map<String, Object> badTeamId = baseWire();
        badTeamId.put("scopes", List.of(Map.of(
                "scopeType", "SPECIFIED_TEAM",
                "mergeMode", "UNION",
                "teamIds", List.of("not-a-number"))));
        assertThat(DataPredicateView.fromArtifact(badTeamId)).isEmpty();
    }
}
