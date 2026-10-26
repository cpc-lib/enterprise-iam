package com.enterprise.iam.authorization.domain.service;

import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;
import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;
import com.enterprise.iam.authorization.domain.model.ShareGrant;
import com.enterprise.iam.authorization.domain.model.ShareFieldGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAuthorizationEngineTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private final AtomicLong ids = new AtomicLong(1000);
    private DefaultAuthorizationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultAuthorizationEngine(
                Clock.fixed(NOW, ZoneOffset.UTC),
                ids::incrementAndGet);
    }

    @Test
    @DisplayName("SEC-FAILCLOSED-001 dependency loss never falls back to ALLOW")
    void deniesWhenAuthoritativeDependencyIsUnavailable() {
        AuthorizationRequest request = request(7, 11);

        AuthorizationResult result = engine.evaluate(request, AuthorizationFacts.unavailable(request));

        assertDenied(result, AuthorizationReason.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    @DisplayName("PROP-AUTHZ-003 cross-tenant facts never allow")
    void deniesCrossTenantFactsEvenWhenTheyContainAllow() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts otherTenant = facts(
                8,
                11,
                true,
                List.of(grant(1, 8, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null)));

        AuthorizationResult result = engine.evaluate(request, otherTenant);

        assertDenied(result, AuthorizationReason.TENANT_MISMATCH);
    }

    @Test
    void deniesStalePermissionVersionBeforeEvaluatingAllow() {
        AuthorizationRequest request = request(7, 10);
        AuthorizationFacts facts = facts(
                7,
                11,
                true,
                List.of(grant(1, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null)));

        AuthorizationResult result = engine.evaluate(request, facts);

        assertDenied(result, AuthorizationReason.STALE_PERMISSION_VERSION);
        assertThat(result.permissionVersion()).isEqualTo(11);
    }

    @Test
    void deniesVersionAheadOfAuthoritativeState() {
        AuthorizationRequest request = request(7, 12);

        AuthorizationResult result = engine.evaluate(request, facts(7, 11, true, List.of()));

        assertDenied(result, AuthorizationReason.PERMISSION_VERSION_MISMATCH);
    }

    @Test
    void deniesDisabledResourceOperation() {
        AuthorizationRequest request = request(7, 11);

        AuthorizationResult result = engine.evaluate(request, facts(7, 11, false, List.of()));

        assertDenied(result, AuthorizationReason.RESOURCE_OPERATION_DISABLED);
    }

    @Test
    void explicitDenyOverridesAllowAndReturnsOnlyDenyEvidence() {
        AuthorizationRequest request = request(7, 11);
        List<ResolvedGrant> grants = List.of(
                grant(20, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null),
                grant(10, 7, 100, GrantEffect.DENY, true, NOW.minusSeconds(60), null));

        AuthorizationResult result = engine.evaluate(request, facts(7, 11, true, grants));

        assertDenied(result, AuthorizationReason.EXPLICIT_DENY);
        assertThat(result.matchedGrantIds()).containsExactly(10L);
    }

    @Test
    void allowsOnlyExactActiveGrantAtCurrentVersion() {
        AuthorizationRequest request = request(7, 11);
        List<ResolvedGrant> grants = List.of(
                grant(30, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null),
                grant(31, 7, 999, GrantEffect.DENY, true, NOW.minusSeconds(60), null),
                grant(32, 8, 100, GrantEffect.DENY, true, NOW.minusSeconds(60), null),
                grant(33, 7, 100, GrantEffect.ALLOW, false, NOW.minusSeconds(60), null));

        AuthorizationResult result = engine.evaluate(request, facts(7, 11, true, grants));

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.GRANT_ALLOW);
        assertThat(result.reasonCode()).isEqualTo("IAM_AUTHZ_GRANT_ALLOW");
        assertThat(result.matchedGrantIds()).containsExactly(30L);
        assertThat(result.permissionVersion()).isEqualTo(11);
        assertThat(result.obligations()).isEmpty();
    }

    @Test
    void expiredGrantDoesNotAllow() {
        AuthorizationRequest request = request(7, 11);
        ResolvedGrant expired = grant(
                40,
                7,
                100,
                GrantEffect.ALLOW,
                true,
                NOW.minusSeconds(120),
                NOW.minusSeconds(1));

        AuthorizationResult result = engine.evaluate(request, facts(7, 11, true, List.of(expired)));

        assertDenied(result, AuthorizationReason.NO_MATCHING_GRANT);
    }

    @Test
    @DisplayName("PROP-AUTHZ-001 same input and version preserves decision semantics")
    void sameInputsAtSameClockProduceSameDecisionSemantics() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = facts(
                7,
                11,
                true,
                List.of(grant(50, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(1), null)));

        AuthorizationResult first = engine.evaluate(request, facts);
        AuthorizationResult second = engine.evaluate(request, facts);

        assertThat(second.decision()).isEqualTo(first.decision());
        assertThat(second.reason()).isEqualTo(first.reason());
        assertThat(second.permissionVersion()).isEqualTo(first.permissionVersion());
        assertThat(second.matchedGrantIds()).isEqualTo(first.matchedGrantIds());
        assertThat(second.evaluatedAt()).isEqualTo(first.evaluatedAt());
        assertThat(second.decisionId()).isNotEqualTo(first.decisionId());
    }

    @Test
    void explicitDenyWinsOverMatchingShareGrant() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = shareFacts(
                List.of(grant(60, 7, 100, GrantEffect.DENY, true, NOW.minusSeconds(60), null)),
                List.of(userShare(900, 100)),
                1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertDenied(result, AuthorizationReason.EXPLICIT_DENY);
        assertThat(result.matchedGrantIds()).containsExactly(60L);
    }

    @Test
    void roleAllowTakesPrecedenceOverShareGrant() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = shareFacts(
                List.of(grant(70, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null)),
                List.of(userShare(900, 100)),
                1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.GRANT_ALLOW);
        assertThat(result.matchedGrantIds()).containsExactly(70L);
    }

    @Test
    void allowsOnTimeValidUserShareGrantWithoutRoleGrant() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = shareFacts(
                List.of(), List.of(userShare(901, 100)), 1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.SHARE_GRANT);
        assertThat(result.reasonCode()).isEqualTo("IAM_AUTHZ_SHARE_GRANT");
        assertThat(result.matchedGrantIds()).containsExactly(901L);
    }

    @Test
    void allowsTeamShareGrantOnlyWhenTeamIdsContextContainsMembership() {
        AuthorizationRequest requestWithoutTeam = request(7, 11);
        AuthorizationRequest requestWithTeam = new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42", 11, "request-0002",
                Map.of("teamIds", List.of(55, 56)));
        ShareGrant teamShare = new ShareGrant(
                902, 7, 300, "invoice-42", ShareGrant.TARGET_TEAM, 56, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1);

        AuthorizationResult denied = engine.evaluate(
                requestWithoutTeam, shareFacts(List.of(), List.of(teamShare), 1L, 1L));
        AuthorizationResult allowed = engine.evaluate(
                requestWithTeam, shareFacts(List.of(), List.of(teamShare), 1L, 1L));

        assertDenied(denied, AuthorizationReason.NO_MATCHING_GRANT);
        assertThat(allowed.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(allowed.reason()).isEqualTo(AuthorizationReason.SHARE_GRANT);
        assertThat(allowed.matchedGrantIds()).containsExactly(902L);
    }

    @Test
    void ignoresExpiredAndFutureShareGrants() {
        AuthorizationRequest request = request(7, 11);
        List<ShareGrant> shares = List.of(
                new ShareGrant(903, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                        NOW.minusSeconds(120), NOW.minusSeconds(1), 1),
                new ShareGrant(904, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                        NOW.plusSeconds(60), NOW.plusSeconds(120), 1));

        AuthorizationResult result = engine.evaluate(
                request, shareFacts(List.of(), shares, 1L, 1L));

        assertDenied(result, AuthorizationReason.NO_MATCHING_GRANT);
    }

    @Test
    @DisplayName("spec16 addendum: expected>applied epoch fails closed even with matching rows")
    void deniesStaleShareProjectionBeforeEvaluatingShares() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = shareFacts(
                List.of(), List.of(userShare(905, 100)), 3L, 2L);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertDenied(result, AuthorizationReason.SHARE_PROJECTION_STALE);
    }

    @Test
    void shareForOtherUserOrInstanceDoesNotAllow() {
        AuthorizationRequest request = request(7, 11);
        List<ShareGrant> shares = List.of(
                new ShareGrant(906, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 101, 400,
                        NOW.minusSeconds(60), NOW.plusSeconds(60), 1),
                new ShareGrant(907, 7, 300, "invoice-99", ShareGrant.TARGET_USER, 100, 400,
                        NOW.minusSeconds(60), NOW.plusSeconds(60), 1));

        AuthorizationResult result = engine.evaluate(
                request, shareFacts(List.of(), shares, 1L, 1L));

        assertDenied(result, AuthorizationReason.NO_MATCHING_GRANT);
    }

    @Test
    @DisplayName("SPEC 26 §64 team role grant allows when context carries the team")
    void teamRoleGrantAllowsWhenRequestContextCarriesTheTeam() {
        AuthorizationRequest request = requestWithTeams(7, 11, List.of(56L));
        AuthorizationFacts facts = facts(7, 11, true,
                List.of(teamGrant(200, 56L, GrantEffect.ALLOW)));

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.TEAM_ROLE_GRANT);
        assertThat(result.reasonCode()).isEqualTo("IAM_AUTHZ_TEAM_ROLE_GRANT");
        assertThat(result.matchedGrantIds()).containsExactly(200L);
    }

    @Test
    @DisplayName("SPEC 26 §64 removal from the team denies immediately")
    void teamRoleGrantDoesNotMatchWhenTeamAbsentFromContext() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = facts(7, 11, true,
                List.of(teamGrant(200, 56L, GrantEffect.ALLOW)));

        AuthorizationResult result = engine.evaluate(request, facts);

        assertDenied(result, AuthorizationReason.NO_MATCHING_GRANT);
    }

    @Test
    void teamRoleDenyOverridesTenantRoleAllow() {
        AuthorizationRequest request = requestWithTeams(7, 11, List.of(56L));
        AuthorizationFacts facts = facts(7, 11, true, List.of(
                grant(100, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null),
                teamGrant(200, 56L, GrantEffect.DENY)));

        AuthorizationResult result = engine.evaluate(request, facts);

        assertDenied(result, AuthorizationReason.EXPLICIT_DENY);
        assertThat(result.matchedGrantIds()).containsExactly(200L);
    }

    @Test
    void mixedTenantAndTeamAllowsResolveToGenericGrantReason() {
        AuthorizationRequest request = requestWithTeams(7, 11, List.of(56L));
        AuthorizationFacts facts = facts(7, 11, true, List.of(
                grant(100, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null),
                teamGrant(200, 56L, GrantEffect.ALLOW)));

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.GRANT_ALLOW);
        assertThat(result.matchedGrantIds()).containsExactly(100L, 200L);
    }

    @Test
    void teamRoleFieldPoliciesAreEmittedAsObligations() {
        AuthorizationRequest request = requestWithTeams(7, 11, List.of(56L));
        AuthorizationFacts facts = rolePolicyFacts(
                List.of(teamGrant(200, 56L, GrantEffect.ALLOW)),
                List.of(new ResolvedFieldPolicy(200, 301, true, true, false, null, 0)),
                List.of());

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.obligations()).containsKey("fieldGrants");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(1);
        assertThat(fieldGrants.get(0))
                .containsEntry("fieldId", 301L)
                .containsEntry("policy", "READ")
                .containsEntry("writable", true);
    }

    private AuthorizationRequest request(long tenantId, long permissionVersion) {
        return new AuthorizationRequest(
                tenantId,
                100,
                200,
                300,
                400,
                "invoice-42",
                permissionVersion,
                "request-0001",
                Map.of("ipRisk", "LOW"));
    }

    private AuthorizationRequest requestWithTeams(
            long tenantId, long permissionVersion, List<Long> teamIds) {
        java.util.Map<String, Object> context = new java.util.HashMap<>();
        context.put("ipRisk", "LOW");
        context.put("teamIds", teamIds);
        return new AuthorizationRequest(
                tenantId,
                100,
                200,
                300,
                400,
                "invoice-42",
                permissionVersion,
                "request-0001",
                context);
    }

    private AuthorizationFacts facts(
            long tenantId,
            long permissionVersion,
            boolean enabled,
            List<ResolvedGrant> grants) {
        return new AuthorizationFacts(true, tenantId, 300, 400, permissionVersion, enabled, grants);
    }

    private AuthorizationFacts shareFacts(
            List<ResolvedGrant> grants,
            List<ShareGrant> shareGrants,
            long expectedEpoch,
            long appliedEpoch) {
        return new AuthorizationFacts(
                true, 7, 300, 400, 11, true, grants,
                shareGrants, expectedEpoch == appliedEpoch, expectedEpoch, appliedEpoch,
                List.of(), List.of());
    }

    private AuthorizationFacts rolePolicyFacts(
            List<ResolvedGrant> grants,
            List<ResolvedFieldPolicy> fieldPolicies,
            List<ResolvedDataScope> dataScopes) {
        return new AuthorizationFacts(
                true, 7, 300, 400, 11, true, grants,
                List.of(), true, 0L, 0L, fieldPolicies, dataScopes);
    }

    private ShareGrant userShare(long shareId, long targetId) {
        return new ShareGrant(
                shareId, 7, 300, "invoice-42", ShareGrant.TARGET_USER, targetId, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1);
    }

    private ResolvedGrant grant(
            long grantId,
            long tenantId,
            long subjectId,
            GrantEffect effect,
            boolean active,
            Instant activeFrom,
            Instant expiresAt) {
        return new ResolvedGrant(
                grantId,
                tenantId,
                subjectId,
                300,
                400,
                effect,
                active,
                activeFrom,
                expiresAt);
    }

    private ResolvedGrant teamGrant(long grantId, long teamId, GrantEffect effect) {
        return new ResolvedGrant(
                grantId, 7, 100, 300, 400,
                effect, true, NOW.minusSeconds(60), null, teamId);
    }

    private void assertDenied(AuthorizationResult result, AuthorizationReason reason) {
        assertThat(result.decision()).isEqualTo(AuthorizationDecision.DENY);
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.reasonCode()).isEqualTo(reason.code());
    }

    @Test
    void shareGrantWithFieldGrantsPopulatesObligations() {
        AuthorizationRequest request = request(7, 11);
        ShareGrant share = new ShareGrant(
                910, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1,
                List.of(
                        new ShareFieldGrant(301, true, true, false, null),
                        new ShareFieldGrant(302, false, true, false, 800L),
                        new ShareFieldGrant(303, false, false, true, null)));
        AuthorizationFacts facts = shareFacts(List.of(), List.of(share), 1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.obligations()).containsKey("fieldGrants");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(3);
        assertThat(fieldGrants.get(0)).containsEntry("fieldId", 301L)
                .containsEntry("policy", "READ").containsEntry("writable", true);
        assertThat(fieldGrants.get(1)).containsEntry("fieldId", 302L)
                .containsEntry("policy", "MASK").containsEntry("maskStrategyId", 800L)
                .containsEntry("writable", true);
        assertThat(fieldGrants.get(2)).containsEntry("fieldId", 303L)
                .containsEntry("policy", "HIDE").containsEntry("writable", false);
    }

    @Test
    void overlappingShareFieldGrantsMergeToMostRestrictiveAndDenyWinsWritable() {
        AuthorizationRequest request = request(7, 11);
        // Field 301: share 910 says READ+writable, share 911 says MASK+read
        // only. MASK is more restrictive (SPEC 38 §82) and writable must be
        // the AND of both, so the merged entry is MASK and not writable.
        ShareGrant permissive = new ShareGrant(
                910, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1,
                List.of(new ShareFieldGrant(301, true, true, false, null)));
        ShareGrant restrictive = new ShareGrant(
                911, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1,
                List.of(new ShareFieldGrant(301, false, false, false, 800L)));
        AuthorizationFacts facts = shareFacts(List.of(), List.of(permissive, restrictive), 1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(1);
        assertThat(fieldGrants.get(0))
                .containsEntry("fieldId", 301L)
                .containsEntry("policy", "MASK")
                .containsEntry("writable", false)
                .containsEntry("maskStrategyId", 800L);
    }

    @Test
    void shareFieldGrantCarriesTypedMaskStrategyWhenResolved() {
        AuthorizationRequest request = request(7, 11);
        ShareGrant share = new ShareGrant(
                910, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1,
                List.of(new ShareFieldGrant(302, false, true, false, 800L)));
        AuthorizationFacts facts = new AuthorizationFacts(
                true, 7, 300, 400, 11, true, List.of(),
                List.of(share), true, 1L, 1L, List.of(), List.of(),
                List.of(new ResolvedMaskStrategy(800, "PHONE", Map.of("keepStart", 3, "keepEnd", 4))));

        AuthorizationResult result = engine.evaluate(request, facts);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(1);
        assertThat(fieldGrants.get(0))
                .containsEntry("fieldId", 302L)
                .containsEntry("policy", "MASK")
                .containsEntry("maskStrategyId", 800L)
                .containsEntry("maskStrategyType", "PHONE")
                .containsEntry("maskConfig", Map.of("keepStart", 3, "keepEnd", 4));
    }

    @Test
    void shareFieldGrantWithoutResolvedStrategyFailsClosedWithoutType() {
        AuthorizationRequest request = request(7, 11);
        ShareGrant share = new ShareGrant(
                910, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1,
                List.of(new ShareFieldGrant(302, false, true, false, 800L)));
        // Strategy 800 is referenced but absent from the resolved facts
        // (missing / DISABLED / deleted): fail closed, no type or config keys.
        AuthorizationFacts facts = shareFacts(List.of(), List.of(share), 1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(1);
        assertThat(fieldGrants.get(0))
                .containsEntry("fieldId", 302L)
                .containsEntry("policy", "MASK")
                .containsEntry("maskStrategyId", 800L)
                .doesNotContainKey("maskStrategyType")
                .doesNotContainKey("maskConfig");
    }

    @Test
    void shareGrantWithoutFieldGrantsHasEmptyObligations() {
        AuthorizationRequest request = request(7, 11);
        AuthorizationFacts facts = shareFacts(List.of(), List.of(userShare(911, 100)), 1L, 1L);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.obligations()).isEmpty();
    }

    @Test
    void roleAllowWithFieldPoliciesPopulatesObligations() {
        // SPEC 26 §66: phone MASK+writable, salary HIDE+not writable, plus a
        // plain readable+writable field.
        AuthorizationRequest request = request(7, 11);
        List<ResolvedGrant> grants = List.of(
                grant(100, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null));
        List<ResolvedFieldPolicy> policies = List.of(
                new ResolvedFieldPolicy(100, 301, true, true, false, null, 0),
                new ResolvedFieldPolicy(100, 302, false, true, false, 800L, 0),
                new ResolvedFieldPolicy(100, 303, false, false, true, null, 0));
        AuthorizationFacts facts = rolePolicyFacts(grants, policies, List.of());

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(3);
        assertThat(fieldGrants.get(0)).containsEntry("fieldId", 301L)
                .containsEntry("policy", "READ").containsEntry("writable", true);
        assertThat(fieldGrants.get(1)).containsEntry("fieldId", 302L)
                .containsEntry("policy", "MASK").containsEntry("writable", true);
        assertThat(fieldGrants.get(2)).containsEntry("fieldId", 303L)
                .containsEntry("policy", "HIDE").containsEntry("writable", false);
    }

    @Test
    void higherPriorityFieldPolicyWinsAndEqualPriorityIsMostRestrictive() {
        AuthorizationRequest request = request(7, 11);
        List<ResolvedGrant> grants = List.of(
                grant(100, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null));
        List<ResolvedFieldPolicy> policies = List.of(
                // field 301: low-priority HIDE loses to high-priority READ
                new ResolvedFieldPolicy(100, 301, false, false, true, null, 5),
                new ResolvedFieldPolicy(100, 301, true, true, false, null, 10),
                // field 302: equal priority READ+writable vs MASK+read-only
                new ResolvedFieldPolicy(100, 302, true, true, false, null, 5),
                new ResolvedFieldPolicy(100, 302, false, false, false, 800L, 5));
        AuthorizationFacts facts = rolePolicyFacts(grants, policies, List.of());

        AuthorizationResult result = engine.evaluate(request, facts);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(2);
        assertThat(fieldGrants.get(0)).containsEntry("fieldId", 301L)
                .containsEntry("policy", "READ").containsEntry("writable", true);
        assertThat(fieldGrants.get(1)).containsEntry("fieldId", 302L)
                .containsEntry("policy", "MASK").containsEntry("writable", false);
    }

    @Test
    void fieldPoliciesBoundToUnmatchedGrantAreNotEmitted() {
        AuthorizationRequest request = request(7, 11);
        List<ResolvedGrant> grants = List.of(
                grant(100, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null));
        // Policies hang off grant 101, which is not part of these facts.
        List<ResolvedFieldPolicy> policies = List.of(
                new ResolvedFieldPolicy(101, 301, true, true, false, null, 0));
        AuthorizationFacts facts = rolePolicyFacts(grants, policies, List.of());

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.obligations()).doesNotContainKey("fieldGrants");
    }

    @Test
    void roleAllowWithDataScopesPopulatesObligations() {
        // SPEC 26 §65: DataScope=TEAM(A) flows to the consumer as a typed
        // dataScopes obligation; SPECIFIED_TEAM carries concrete team ids.
        AuthorizationRequest request = request(7, 11);
        List<ResolvedGrant> grants = List.of(
                grant(100, 7, 100, GrantEffect.ALLOW, true, NOW.minusSeconds(60), null));
        List<ResolvedDataScope> scopes = List.of(
                new ResolvedDataScope(100, 501, "TEAM_A", "TEAM", null, "UNION", List.of()),
                new ResolvedDataScope(100, 502, "SPEC_B_C", "SPECIFIED_TEAM",
                        null, "INTERSECT", List.of(2L, 3L)));
        AuthorizationFacts facts = rolePolicyFacts(grants, List.of(), scopes);

        AuthorizationResult result = engine.evaluate(request, facts);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataScopes =
                (List<Map<String, Object>>) result.obligations().get("dataScopes");
        assertThat(dataScopes).hasSize(2);
        assertThat(dataScopes.get(0))
                .containsEntry("scopeType", "TEAM")
                .containsEntry("scopeCode", "TEAM_A")
                .containsEntry("mergeMode", "UNION")
                .doesNotContainKey("teamIds");
        assertThat(dataScopes.get(1))
                .containsEntry("scopeType", "SPECIFIED_TEAM")
                .containsEntry("mergeMode", "INTERSECT")
                .containsEntry("teamIds", List.of(2L, 3L));
    }
}
