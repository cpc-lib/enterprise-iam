package com.enterprise.iam.authorization.domain.service;

import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
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

    private AuthorizationFacts facts(
            long tenantId,
            long permissionVersion,
            boolean enabled,
            List<ResolvedGrant> grants) {
        return new AuthorizationFacts(true, tenantId, 300, 400, permissionVersion, enabled, grants);
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

    private void assertDenied(AuthorizationResult result, AuthorizationReason reason) {
        assertThat(result.decision()).isEqualTo(AuthorizationDecision.DENY);
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.reasonCode()).isEqualTo(reason.code());
    }
}
