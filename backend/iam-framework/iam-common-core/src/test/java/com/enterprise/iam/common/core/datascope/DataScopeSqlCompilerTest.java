package com.enterprise.iam.common.core.datascope;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeSqlCompilerTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final DataScopeColumnMapping COLUMNS =
            new DataScopeColumnMapping("owner_user_id", "owner_team_id", "instance_key");
    private static final DataScopeSqlCompiler.Binding BINDING =
            new DataScopeSqlCompiler.Binding(1L, 100L, 300L, 400L, 5L);
    private static final DataScopeEnforcer.Principal PRINCIPAL =
            new DataScopeEnforcer.Principal(100L, Set.of(7L, 8L), Set.of("file-1", "file-2"));

    private static DataPredicateView artifact(List<DataScopeObligation> scopes) {
        return new DataPredicateView(
                "iam:data-predicate:1001", 1L, 100L, 300L, 400L, 5L,
                NOW.plusSeconds(300), scopes);
    }

    private static DataScopeObligation scope(String type, String merge, List<Long> teamIds) {
        return new DataScopeObligation(type, null, null, merge, teamIds);
    }

    @Test
    void allScopeCompilesToTrue() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("ALL", "UNION", List.of()))), BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("1=1");
        assertThat(sql.params()).isEmpty();
    }

    @Test
    void selfScopeBindsPrincipalUserId() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("SELF", "UNION", List.of()))), BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("owner_user_id = :dpUserId");
        assertThat(sql.params()).containsEntry("dpUserId", 100L);
    }

    @Test
    void teamScopeBindsPrincipalTeams() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("TEAM", "UNION", List.of()))), BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("owner_team_id IN (:dpPrincipalTeams)");
        assertThat(sql.params()).containsEntry("dpPrincipalTeams", List.of(7L, 8L));
    }

    @Test
    void teamScopeFailsClosedWhenPrincipalHasNoTeams() {
        DataScopeEnforcer.Principal noTeams = new DataScopeEnforcer.Principal(100L, Set.of(), Set.of("file-1"));
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("TEAM", "UNION", List.of()))), BINDING, COLUMNS, noTeams, NOW);
        assertThat(sql.whereClause()).isEqualTo("1=0");
        assertThat(sql.params()).isEmpty();
    }

    @Test
    void specifiedTeamScopeBakesStaticTeamList() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("SPECIFIED_TEAM", "UNION", List.of(21L, 22L)))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("owner_team_id IN (:dpScopeTeams0)");
        assertThat(sql.params()).containsEntry("dpScopeTeams0", List.of(21L, 22L));
    }

    @Test
    void specifiedTeamScopeFailsClosedOnEmptyList() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("TEAM_AND_CHILDREN", "UNION", List.of()))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("1=0");
    }

    @Test
    void sharedScopeBindsSharedInstanceKeys() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("SHARED", "UNION", List.of()))), BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("instance_key IN (:dpSharedKeys)");
        assertThat(sql.params()).containsEntry("dpSharedKeys", List.of("file-1", "file-2"));
    }

    @Test
    void customPolicyAndUnknownScopesFailClosed() {
        DataScopeSqlCompiler.DataScopeSql custom = DataScopeSqlCompiler.compile(
                artifact(List.of(new DataScopeObligation("CUSTOM_POLICY", null, 55L, "UNION", List.of()))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(custom.whereClause()).isEqualTo("1=0");
        DataScopeSqlCompiler.DataScopeSql future = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("FUTURE_SCOPE", "UNION", List.of()))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(future.whereClause()).isEqualTo("1=0");
    }

    @Test
    void unionFoldsWithOrPreservingOrder() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(
                        scope("SELF", "UNION", List.of()),
                        scope("SPECIFIED_TEAM", "UNION", List.of(21L)))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause())
                .isEqualTo("(owner_user_id = :dpUserId OR owner_team_id IN (:dpScopeTeams1))");
        assertThat(sql.params())
                .containsEntry("dpUserId", 100L)
                .containsEntry("dpScopeTeams1", List.of(21L));
    }

    @Test
    void intersectFoldsWithAnd() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(
                        scope("TEAM", "UNION", List.of()),
                        scope("SELF", "INTERSECT", List.of()))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause())
                .isEqualTo("(owner_team_id IN (:dpPrincipalTeams) AND owner_user_id = :dpUserId)");
    }

    @Test
    void unionWithCustomPolicyBranchDeniesThatBranchOnly() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(
                        scope("SELF", "UNION", List.of()),
                        new DataScopeObligation("CUSTOM_POLICY", null, 55L, "UNION", List.of()))),
                BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("(owner_user_id = :dpUserId OR 1=0)");
    }

    @Test
    void emptyScopesDenyAll() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of()), BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("1=0");
    }

    @Test
    void bindingMismatchDeniesAll() {
        for (DataScopeSqlCompiler.Binding wrong : List.of(
                new DataScopeSqlCompiler.Binding(2L, 100L, 300L, 400L, 5L),
                new DataScopeSqlCompiler.Binding(1L, 101L, 300L, 400L, 5L),
                new DataScopeSqlCompiler.Binding(1L, 100L, 301L, 400L, 5L),
                new DataScopeSqlCompiler.Binding(1L, 100L, 300L, 401L, 5L),
                new DataScopeSqlCompiler.Binding(1L, 100L, 300L, 400L, 6L))) {
            DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                    artifact(List.of(scope("ALL", "UNION", List.of()))), wrong, COLUMNS, PRINCIPAL, NOW);
            assertThat(sql.whereClause()).isEqualTo("1=0");
        }
    }

    @Test
    void expiredArtifactDeniesAll() {
        DataPredicateView expired = new DataPredicateView(
                "iam:data-predicate:1001", 1L, 100L, 300L, 400L, 5L,
                NOW, List.of(scope("ALL", "UNION", List.of())));
        DataScopeSqlCompiler.DataScopeSql sql =
                DataScopeSqlCompiler.compile(expired, BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("1=0");
    }

    @Test
    void sameFragmentServesSelectAndCountParity() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                artifact(List.of(scope("TEAM", "UNION", List.of()))), BINDING, COLUMNS, PRINCIPAL, NOW);
        String select = "SELECT * FROM customer WHERE " + sql.whereClause();
        String count = "SELECT COUNT(*) FROM customer WHERE " + sql.whereClause();
        assertThat(select).contains("owner_team_id IN (:dpPrincipalTeams)");
        assertThat(count).contains("owner_team_id IN (:dpPrincipalTeams)");
        assertThat(sql.whereClause()).doesNotContain(";", "--", "'", "\"");
    }

    @Test
    void columnMappingRejectsNonIdentifiers() {
        assertThatThrownBy(() -> new DataScopeColumnMapping("owner_user_id; DROP TABLE t", "b", "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataScopeColumnMapping("a", "owner-team-id", "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataScopeColumnMapping("a", "b", "1col"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compileFromWireMapParsesStringIds() {
        Map<String, Object> wire = Map.of(
                "ref", "iam:data-predicate:1001",
                "tenantId", "1",
                "subjectId", "100",
                "resourceId", "300",
                "operationId", "400",
                "permissionVersion", "5",
                "expiresAt", NOW.plusSeconds(300).toString(),
                "scopes", List.of(Map.of(
                        "scopeType", "SELF",
                        "mergeMode", "UNION")));
        DataScopeSqlCompiler.DataScopeSql sql =
                DataScopeSqlCompiler.compile(wire, BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("owner_user_id = :dpUserId");
    }

    @Test
    void compileFromMalformedWireMapDeniesAll() {
        DataScopeSqlCompiler.DataScopeSql sql = DataScopeSqlCompiler.compile(
                Map.of("ref", "iam:data-predicate:1001"), BINDING, COLUMNS, PRINCIPAL, NOW);
        assertThat(sql.whereClause()).isEqualTo("1=0");
    }
}
