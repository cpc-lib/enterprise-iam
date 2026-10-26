package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.DataPredicateArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.FieldReadPlanArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.FieldWritePlanArtifactRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.DataPredicateArtifact;
import com.enterprise.iam.authorization.domain.model.FieldReadPlanArtifact;
import com.enterprise.iam.authorization.domain.model.FieldWritePlanArtifact;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;
import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import com.enterprise.iam.authorization.infrastructure.persistence.InMemoryAuthorizationDecisionRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveEffectivePoliciesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void resolvesAllowWithSyntheticRefs() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(1000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationFactRepository factRepo = new AllowFactsRepository();
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(factRepo, engine, recordRepo);
        ResolveEffectivePoliciesUseCase useCase =
                new ResolveEffectivePoliciesUseCase(checkUseCase, Clock.fixed(NOW, ZoneOffset.UTC));

        ResolveEffectivePoliciesUseCase.Result result = useCase.resolve(
                1L, 100L, 300L, 400L, 5L, "request-1");

        assertThat(result.decision()).isEqualTo("ALLOW");
        assertThat(result.permissionVersion()).isEqualTo(5L);
        assertThat(result.dataPredicateRef()).startsWith("iam:data-predicate:");
        assertThat(result.fieldReadPlanRef()).startsWith("iam:field-read:");
        assertThat(result.fieldWritePlanRef()).startsWith("iam:field-write:");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void resolvesDenyWithNullRefs() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(2000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationFactRepository factRepo = new DenyFactsRepository();
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(factRepo, engine, recordRepo);
        ResolveEffectivePoliciesUseCase useCase =
                new ResolveEffectivePoliciesUseCase(checkUseCase, Clock.fixed(NOW, ZoneOffset.UTC));

        ResolveEffectivePoliciesUseCase.Result result = useCase.resolve(
                1L, 100L, 300L, 400L, 5L, "request-2");

        assertThat(result.decision()).isEqualTo("DENY");
        assertThat(result.dataPredicateRef()).isNull();
        assertThat(result.fieldReadPlanRef()).isNull();
        assertThat(result.fieldWritePlanRef()).isNull();
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void allowStoresTypedArtifactBoundToDecisionContext() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(3000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationFactRepository factRepo = new ScopedAllowFactsRepository();
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(factRepo, engine, recordRepo);
        InMemoryDataPredicateArtifactRepository artifactRepo = new InMemoryDataPredicateArtifactRepository();
        ResolveEffectivePoliciesUseCase useCase = new ResolveEffectivePoliciesUseCase(
                checkUseCase, artifactRepo, Clock.fixed(NOW, ZoneOffset.UTC));

        ResolveEffectivePoliciesUseCase.Result result = useCase.resolve(
                1L, 100L, 300L, 400L, 5L, "request-3");

        assertThat(result.decision()).isEqualTo("ALLOW");
        DataPredicateArtifact artifact = artifactRepo.stored.values().stream().findFirst().orElseThrow();
        assertThat(artifact.ref()).isEqualTo(result.dataPredicateRef());
        assertThat(artifact.tenantId()).isEqualTo(1L);
        assertThat(artifact.subjectId()).isEqualTo(100L);
        assertThat(artifact.resourceId()).isEqualTo(300L);
        assertThat(artifact.operationId()).isEqualTo(400L);
        assertThat(artifact.permissionVersion()).isEqualTo(5L);
        assertThat(artifact.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(artifact.scopes()).hasSize(1);
        DataPredicateArtifact.Entry scope = artifact.scopes().get(0);
        assertThat(scope.scopeType()).isEqualTo("SPECIFIED_TEAM");
        assertThat(scope.mergeMode()).isEqualTo("UNION");
        assertThat(scope.teamIds()).containsExactly(21L, 22L);
    }

    @Test
    void denyStoresNoArtifact() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(4000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(new DenyFactsRepository(), engine, recordRepo);
        InMemoryDataPredicateArtifactRepository artifactRepo = new InMemoryDataPredicateArtifactRepository();
        ResolveEffectivePoliciesUseCase useCase = new ResolveEffectivePoliciesUseCase(
                checkUseCase, artifactRepo, Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.resolve(1L, 100L, 300L, 400L, 5L, "request-4");

        assertThat(artifactRepo.stored).isEmpty();
    }

    @Test
    void resolveDataPredicateFailsClosedOnUnknownOrBlankRef() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(5000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(new DenyFactsRepository(), engine, recordRepo);
        InMemoryDataPredicateArtifactRepository artifactRepo = new InMemoryDataPredicateArtifactRepository();
        ResolveEffectivePoliciesUseCase useCase = new ResolveEffectivePoliciesUseCase(
                checkUseCase, artifactRepo, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(useCase.resolveDataPredicate("iam:data-predicate:999")).isEmpty();
        assertThat(useCase.resolveDataPredicate(" ")).isEmpty();
        assertThat(useCase.resolveDataPredicate(null)).isEmpty();
    }

    @Test
    void allowStoresFieldReadAndWritePlansBoundToDecisionContext() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(6000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(new FieldPoliciesFactsRepository(), engine, recordRepo);
        InMemoryFieldReadPlanArtifactRepository readRepo = new InMemoryFieldReadPlanArtifactRepository();
        InMemoryFieldWritePlanArtifactRepository writeRepo = new InMemoryFieldWritePlanArtifactRepository();
        ResolveEffectivePoliciesUseCase useCase = new ResolveEffectivePoliciesUseCase(
                checkUseCase, null, readRepo, writeRepo, Clock.fixed(NOW, ZoneOffset.UTC));

        ResolveEffectivePoliciesUseCase.Result result = useCase.resolve(
                1L, 100L, 300L, 400L, 5L, "request-field-1");

        assertThat(result.decision()).isEqualTo("ALLOW");
        FieldReadPlanArtifact readPlan = readRepo.stored.get(result.fieldReadPlanRef());
        FieldWritePlanArtifact writePlan = writeRepo.stored.get(result.fieldWritePlanRef());
        assertThat(readPlan).isNotNull();
        assertThat(writePlan).isNotNull();
        assertThat(readPlan.ref()).isEqualTo(result.fieldReadPlanRef());
        assertThat(readPlan.tenantId()).isEqualTo(1L);
        assertThat(readPlan.subjectId()).isEqualTo(100L);
        assertThat(readPlan.resourceId()).isEqualTo(300L);
        assertThat(readPlan.operationId()).isEqualTo(400L);
        assertThat(readPlan.permissionVersion()).isEqualTo(5L);
        assertThat(readPlan.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(writePlan.expiresAt()).isEqualTo(NOW.plusSeconds(300));

        Map<Long, FieldReadPlanArtifact.Entry> readByField = readPlan.entries().stream()
                .collect(java.util.stream.Collectors.toMap(
                        FieldReadPlanArtifact.Entry::fieldId, e -> e));
        assertThat(readByField.get(101L).policy()).isEqualTo("READ");
        assertThat(readByField.get(101L).maskStrategyType()).isNull();
        assertThat(readByField.get(102L).policy()).isEqualTo("HIDE");
        assertThat(readByField.get(103L).policy()).isEqualTo("MASK");
        assertThat(readByField.get(103L).maskStrategyType()).isEqualTo("PHONE");
        assertThat(readByField.get(103L).maskConfig()).containsEntry("visibleTail", 4);

        Map<Long, FieldWritePlanArtifact.Entry> writeByField = writePlan.entries().stream()
                .collect(java.util.stream.Collectors.toMap(
                        FieldWritePlanArtifact.Entry::fieldId, e -> e));
        assertThat(writeByField.get(101L).writable()).isTrue();
        assertThat(writeByField.get(102L).writable()).isFalse();
        assertThat(writeByField.get(103L).writable()).isFalse();
    }

    @Test
    void denyStoresNoFieldPlans() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(7000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(new DenyFactsRepository(), engine, recordRepo);
        InMemoryFieldReadPlanArtifactRepository readRepo = new InMemoryFieldReadPlanArtifactRepository();
        InMemoryFieldWritePlanArtifactRepository writeRepo = new InMemoryFieldWritePlanArtifactRepository();
        ResolveEffectivePoliciesUseCase useCase = new ResolveEffectivePoliciesUseCase(
                checkUseCase, null, readRepo, writeRepo, Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.resolve(1L, 100L, 300L, 400L, 5L, "request-field-2");

        assertThat(readRepo.stored).isEmpty();
        assertThat(writeRepo.stored).isEmpty();
    }

    @Test
    void resolveFieldPlansFailClosedOnUnknownOrBlankRef() {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(8000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(new DenyFactsRepository(), engine, recordRepo);
        ResolveEffectivePoliciesUseCase useCase = new ResolveEffectivePoliciesUseCase(
                checkUseCase, null,
                new InMemoryFieldReadPlanArtifactRepository(),
                new InMemoryFieldWritePlanArtifactRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(useCase.resolveFieldReadPlan("iam:field-read:999")).isEmpty();
        assertThat(useCase.resolveFieldReadPlan(" ")).isEmpty();
        assertThat(useCase.resolveFieldReadPlan(null)).isEmpty();
        assertThat(useCase.resolveFieldWritePlan("iam:field-write:999")).isEmpty();
        assertThat(useCase.resolveFieldWritePlan(" ")).isEmpty();
        assertThat(useCase.resolveFieldWritePlan(null)).isEmpty();
    }

    static class FieldPoliciesFactsRepository implements AuthorizationFactRepository {
        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            ResolvedGrant grant = new ResolvedGrant(
                    50L, request.tenantId(), request.subjectId(),
                    request.resourceId(), request.operationId(),
                    GrantEffect.ALLOW, true, NOW.minusSeconds(60), null);
            ResolvedFieldPolicy readable =
                    new ResolvedFieldPolicy(50L, 101L, true, true, false, null, 10);
            ResolvedFieldPolicy hidden =
                    new ResolvedFieldPolicy(50L, 102L, false, false, true, null, 10);
            ResolvedFieldPolicy masked =
                    new ResolvedFieldPolicy(50L, 103L, true, false, false, 77L, 10);
            return new AuthorizationFacts(
                    true, request.tenantId(), request.resourceId(), request.operationId(),
                    request.permissionVersion(), true, List.of(grant),
                    List.of(), true, 0L, 0L,
                    List.of(readable, hidden, masked), List.of(),
                    List.of(new ResolvedMaskStrategy(77L, "PHONE", Map.of("visibleTail", 4))));
        }
    }

    static class InMemoryFieldReadPlanArtifactRepository implements FieldReadPlanArtifactRepository {
        final Map<String, FieldReadPlanArtifact> stored = new ConcurrentHashMap<>();

        @Override
        public void save(FieldReadPlanArtifact artifact) {
            stored.put(artifact.ref(), artifact);
        }

        @Override
        public Optional<FieldReadPlanArtifact> findActiveByRef(String ref, Instant now) {
            FieldReadPlanArtifact artifact = stored.get(ref);
            if (artifact == null || !now.isBefore(artifact.expiresAt())) {
                return Optional.empty();
            }
            return Optional.of(artifact);
        }
    }

    static class InMemoryFieldWritePlanArtifactRepository implements FieldWritePlanArtifactRepository {
        final Map<String, FieldWritePlanArtifact> stored = new ConcurrentHashMap<>();

        @Override
        public void save(FieldWritePlanArtifact artifact) {
            stored.put(artifact.ref(), artifact);
        }

        @Override
        public Optional<FieldWritePlanArtifact> findActiveByRef(String ref, Instant now) {
            FieldWritePlanArtifact artifact = stored.get(ref);
            if (artifact == null || !now.isBefore(artifact.expiresAt())) {
                return Optional.empty();
            }
            return Optional.of(artifact);
        }
    }

    static class ScopedAllowFactsRepository implements AuthorizationFactRepository {
        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            ResolvedGrant grant = new ResolvedGrant(
                    50L, request.tenantId(), request.subjectId(),
                    request.resourceId(), request.operationId(),
                    GrantEffect.ALLOW, true, NOW.minusSeconds(60), null);
            ResolvedDataScope dataScope = new ResolvedDataScope(
                    50L, 900L, "scope-team", "SPECIFIED_TEAM", null, "UNION", List.of(21L, 22L));
            return new AuthorizationFacts(
                    true, request.tenantId(), request.resourceId(), request.operationId(),
                    request.permissionVersion(), true, List.of(grant),
                    List.of(), true, 0L, 0L,
                    List.of(), List.of(dataScope), List.of());
        }
    }

    static class InMemoryDataPredicateArtifactRepository implements DataPredicateArtifactRepository {
        final Map<String, DataPredicateArtifact> stored = new ConcurrentHashMap<>();

        @Override
        public void save(DataPredicateArtifact artifact) {
            stored.put(artifact.ref(), artifact);
        }

        @Override
        public Optional<DataPredicateArtifact> findActiveByRef(String ref, Instant now) {
            DataPredicateArtifact artifact = stored.get(ref);
            if (artifact == null || !now.isBefore(artifact.expiresAt())) {
                return Optional.empty();
            }
            return Optional.of(artifact);
        }
    }

    static class AllowFactsRepository implements AuthorizationFactRepository {
        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            ResolvedGrant grant = new ResolvedGrant(
                    50L, request.tenantId(), request.subjectId(),
                    request.resourceId(), request.operationId(),
                    GrantEffect.ALLOW, true, NOW.minusSeconds(60), null);
            return new AuthorizationFacts(true, request.tenantId(),
                    request.resourceId(), request.operationId(),
                    request.permissionVersion(), true, List.of(grant));
        }
    }

    static class DenyFactsRepository implements AuthorizationFactRepository {
        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            return new AuthorizationFacts(true, request.tenantId(),
                    request.resourceId(), request.operationId(),
                    request.permissionVersion(), true, List.of());
        }
    }
}
