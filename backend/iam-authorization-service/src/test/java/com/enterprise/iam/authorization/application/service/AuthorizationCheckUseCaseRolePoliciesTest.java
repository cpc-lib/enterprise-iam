package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedFieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedMaskStrategyRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;
import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCheckUseCaseRolePoliciesTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private final DefaultAuthorizationEngine engine = new DefaultAuthorizationEngine(
            Clock.fixed(NOW, ZoneOffset.UTC), new AtomicLong(1)::incrementAndGet);
    private final AuthorizationDecisionRecordRepository recordRepository =
            new AuthorizationDecisionRecordRepository() {
                @Override
                public Optional<AuthorizationResult> findById(long decisionId) {
                    return Optional.empty();
                }

                @Override
                public void record(AuthorizationResult result) {
                }
            };

    @Test
    void loadsPoliciesForMatchedGrantsAndEmitsFieldAndScopeObligations() {
        AuthorizationRequest request = new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42", 11, "request-0001", Map.of());
        RecordingFieldPolicyRepository fieldPolicies = new RecordingFieldPolicyRepository(List.of(
                new ResolvedFieldPolicy(100, 301, true, true, false, null, 0)));
        RecordingDataScopeRepository dataScopes = new RecordingDataScopeRepository(List.of(
                new ResolvedDataScope(100, 501, "TEAM_A", "TEAM", null, "UNION", List.of())));

        AuthorizationCheckUseCase useCase = new AuthorizationCheckUseCase(
                allowGrantFacts(), engine, recordRepository,
                null, Clock.fixed(NOW, ZoneOffset.UTC), null,
                fieldPolicies, dataScopes, null);

        AuthorizationResult result = useCase.check(request);

        assertThat(result.obligations()).containsKey("fieldGrants");
        assertThat(result.obligations()).containsKey("dataScopes");
        assertThat(fieldPolicies.roleGrantIds).containsExactly(100L);
        assertThat(fieldPolicies.teamRoleGrantIds).isEmpty();
        assertThat(dataScopes.roleGrantIds).containsExactly(100L);
        assertThat(dataScopes.teamRoleGrantIds).isEmpty();
    }

    @Test
    void splitsTeamRoleGrantIdsAndEmitsTheirPoliciesAsObligations() {
        AuthorizationRequest request = new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42", 11, "request-0001",
                Map.of("teamIds", List.of(56L)));
        RecordingFieldPolicyRepository fieldPolicies = new RecordingFieldPolicyRepository(List.of(
                new ResolvedFieldPolicy(200, 302, true, false, false, null, 0)));
        RecordingDataScopeRepository dataScopes = new RecordingDataScopeRepository(List.of(
                new ResolvedDataScope(200, 502, "TEAM_A", "TEAM", null, "UNION", List.of())));

        AuthorizationCheckUseCase useCase = new AuthorizationCheckUseCase(
                teamRoleGrantFacts(), engine, recordRepository,
                null, Clock.fixed(NOW, ZoneOffset.UTC), null,
                fieldPolicies, dataScopes, null);

        AuthorizationResult result = useCase.check(request);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(fieldPolicies.roleGrantIds).isEmpty();
        assertThat(fieldPolicies.teamRoleGrantIds).containsExactly(200L);
        assertThat(dataScopes.roleGrantIds).isEmpty();
        assertThat(dataScopes.teamRoleGrantIds).containsExactly(200L);
        assertThat(result.obligations()).containsKey("fieldGrants");
        assertThat(result.obligations()).containsKey("dataScopes");
    }

    @Test
    void loadsMaskStrategiesForReferencedIdsAndEmitsTypedObligations() {
        AuthorizationRequest request = new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42", 11, "request-0001", Map.of());
        RecordingFieldPolicyRepository fieldPolicies = new RecordingFieldPolicyRepository(List.of(
                new ResolvedFieldPolicy(100, 302, false, false, false, 800L, 0)));
        RecordingDataScopeRepository dataScopes = new RecordingDataScopeRepository(List.of());
        RecordingMaskStrategyRepository maskStrategies = new RecordingMaskStrategyRepository(List.of(
                new ResolvedMaskStrategy(800, "PHONE", Map.of("keepStart", 3))));

        AuthorizationCheckUseCase useCase = new AuthorizationCheckUseCase(
                allowGrantFacts(), engine, recordRepository,
                null, Clock.fixed(NOW, ZoneOffset.UTC), null,
                fieldPolicies, dataScopes, maskStrategies);

        AuthorizationResult result = useCase.check(request);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(maskStrategies.requestedIds).containsExactly(800L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants =
                (List<Map<String, Object>>) result.obligations().get("fieldGrants");
        assertThat(fieldGrants).hasSize(1);
        assertThat(fieldGrants.get(0))
                .containsEntry("fieldId", 302L)
                .containsEntry("policy", "MASK")
                .containsEntry("maskStrategyId", 800L)
                .containsEntry("maskStrategyType", "PHONE")
                .containsEntry("maskConfig", Map.of("keepStart", 3));
    }

    private AuthorizationFactRepository allowGrantFacts() {
        return request -> {
            ResolvedGrant allow = new ResolvedGrant(
                    100L, 7L, 100L, 300L, 400L, GrantEffect.ALLOW, true,
                    NOW.minusSeconds(60), null);
            return new AuthorizationFacts(
                    true, 7, 300, 400, 11, true, List.of(allow));
        };
    }

    private AuthorizationFactRepository teamRoleGrantFacts() {
        return request -> {
            ResolvedGrant allow = new ResolvedGrant(
                    200L, 7L, 100L, 300L, 400L, GrantEffect.ALLOW, true,
                    NOW.minusSeconds(60), null, 56L);
            return new AuthorizationFacts(
                    true, 7, 300, 400, 11, true, List.of(allow));
        };
    }

    private static final class RecordingFieldPolicyRepository
            implements ResolvedFieldPolicyRepository {
        private final List<Long> roleGrantIds = new ArrayList<>();
        private final List<Long> teamRoleGrantIds = new ArrayList<>();
        private final List<ResolvedFieldPolicy> result;

        private RecordingFieldPolicyRepository(List<ResolvedFieldPolicy> result) {
            this.result = result;
        }

        @Override
        public List<ResolvedFieldPolicy> findForRolePermissions(
                long tenantId, List<Long> rolePermissionIds) {
            roleGrantIds.addAll(rolePermissionIds);
            return result;
        }

        @Override
        public List<ResolvedFieldPolicy> findForRolePermissions(
                long tenantId, List<Long> rolePermissionIds, List<Long> teamRolePermissionIds) {
            assertThat(tenantId).isEqualTo(7L);
            roleGrantIds.addAll(rolePermissionIds);
            teamRoleGrantIds.addAll(teamRolePermissionIds);
            return result;
        }
    }

    private static final class RecordingMaskStrategyRepository
            implements ResolvedMaskStrategyRepository {
        private final List<Long> requestedIds = new ArrayList<>();
        private final List<ResolvedMaskStrategy> result;

        private RecordingMaskStrategyRepository(List<ResolvedMaskStrategy> result) {
            this.result = result;
        }

        @Override
        public List<ResolvedMaskStrategy> findByIds(long tenantId, Collection<Long> strategyIds) {
            assertThat(tenantId).isEqualTo(7L);
            requestedIds.addAll(strategyIds);
            return result;
        }
    }

    private static final class RecordingDataScopeRepository
            implements ResolvedDataScopeRepository {
        private final List<Long> roleGrantIds = new ArrayList<>();
        private final List<Long> teamRoleGrantIds = new ArrayList<>();
        private final List<ResolvedDataScope> result;

        private RecordingDataScopeRepository(List<ResolvedDataScope> result) {
            this.result = result;
        }

        @Override
        public List<ResolvedDataScope> findForRolePermissions(
                long tenantId, List<Long> rolePermissionIds) {
            roleGrantIds.addAll(rolePermissionIds);
            return result;
        }

        @Override
        public List<ResolvedDataScope> findForRolePermissions(
                long tenantId, List<Long> rolePermissionIds, List<Long> teamRolePermissionIds) {
            assertThat(tenantId).isEqualTo(7L);
            roleGrantIds.addAll(rolePermissionIds);
            teamRoleGrantIds.addAll(teamRolePermissionIds);
            return result;
        }
    }
}
