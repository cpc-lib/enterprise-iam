package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.DataPredicateArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.FieldReadPlanArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.FieldWritePlanArtifactRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.DataPredicateArtifact;
import com.enterprise.iam.authorization.domain.model.FieldReadPlanArtifact;
import com.enterprise.iam.authorization.domain.model.FieldWritePlanArtifact;
import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ResolveEffectivePoliciesUseCase {

    private static final long DENY_TTL_SECONDS = 30L;
    private static final long ALLOW_TTL_SECONDS = 300L;

    private final AuthorizationCheckUseCase authorizationCheckUseCase;
    private final DataPredicateArtifactRepository dataPredicateArtifactRepository;
    private final FieldReadPlanArtifactRepository fieldReadPlanArtifactRepository;
    private final FieldWritePlanArtifactRepository fieldWritePlanArtifactRepository;
    private final Clock clock;

    public ResolveEffectivePoliciesUseCase(AuthorizationCheckUseCase authorizationCheckUseCase, Clock clock) {
        this(authorizationCheckUseCase, null, null, null, clock);
    }

    public ResolveEffectivePoliciesUseCase(AuthorizationCheckUseCase authorizationCheckUseCase,
                                           DataPredicateArtifactRepository dataPredicateArtifactRepository,
                                           Clock clock) {
        this(authorizationCheckUseCase, dataPredicateArtifactRepository, null, null, clock);
    }

    public ResolveEffectivePoliciesUseCase(AuthorizationCheckUseCase authorizationCheckUseCase,
                                           DataPredicateArtifactRepository dataPredicateArtifactRepository,
                                           FieldReadPlanArtifactRepository fieldReadPlanArtifactRepository,
                                           FieldWritePlanArtifactRepository fieldWritePlanArtifactRepository,
                                           Clock clock) {
        this.authorizationCheckUseCase = Objects.requireNonNull(authorizationCheckUseCase);
        this.dataPredicateArtifactRepository = dataPredicateArtifactRepository;
        this.fieldReadPlanArtifactRepository = fieldReadPlanArtifactRepository;
        this.fieldWritePlanArtifactRepository = fieldWritePlanArtifactRepository;
        this.clock = Objects.requireNonNull(clock);
    }

    public Result resolve(long tenantId, long subjectId, long resourceId, long operationId,
                          long permissionVersion, String requestId) {
        AuthorizationRequest request = new AuthorizationRequest(
                tenantId, subjectId, 1L, resourceId, operationId, null,
                permissionVersion, requestId, Map.of());
        AuthorizationResult result = authorizationCheckUseCase.check(request);
        Instant now = clock.instant();
        if (result.decision() == AuthorizationDecision.ALLOW) {
            Instant expiresAt = now.plusSeconds(ALLOW_TTL_SECONDS);
            String dataPredicateRef = DataPredicateArtifact.REF_PREFIX + result.decisionId();
            String fieldReadRef = FieldReadPlanArtifact.REF_PREFIX + result.decisionId();
            String fieldWriteRef = FieldWritePlanArtifact.REF_PREFIX + result.decisionId();
            if (dataPredicateArtifactRepository != null) {
                dataPredicateArtifactRepository.save(new DataPredicateArtifact(
                        dataPredicateRef, tenantId, subjectId, resourceId, operationId,
                        result.permissionVersion(), result.decisionId(),
                        toDataScopeEntries(result.obligations()), expiresAt));
            }
            if (fieldReadPlanArtifactRepository != null) {
                fieldReadPlanArtifactRepository.save(new FieldReadPlanArtifact(
                        fieldReadRef, tenantId, subjectId, resourceId, operationId,
                        result.permissionVersion(), result.decisionId(),
                        toFieldReadEntries(result.obligations()), expiresAt));
            }
            if (fieldWritePlanArtifactRepository != null) {
                fieldWritePlanArtifactRepository.save(new FieldWritePlanArtifact(
                        fieldWriteRef, tenantId, subjectId, resourceId, operationId,
                        result.permissionVersion(), result.decisionId(),
                        toFieldWriteEntries(result.obligations()), expiresAt));
            }
            return new Result(
                    AuthorizationDecision.ALLOW.name(),
                    result.permissionVersion(),
                    dataPredicateRef,
                    fieldReadRef,
                    fieldWriteRef,
                    expiresAt);
        }
        return new Result(
                AuthorizationDecision.DENY.name(),
                result.permissionVersion(),
                null,
                null,
                null,
                now.plusSeconds(DENY_TTL_SECONDS));
    }

    /**
     * Dereferences a dataPredicateRef. Fail-closed: unknown, expired or
     * malformed references yield empty so callers deny row access.
     */
    public Optional<DataPredicateArtifact> resolveDataPredicate(String ref) {
        if (ref == null || ref.isBlank() || dataPredicateArtifactRepository == null) {
            return Optional.empty();
        }
        return dataPredicateArtifactRepository.findActiveByRef(ref, clock.instant());
    }

    /** Dereferences a fieldReadPlanRef; empty when absent, expired or malformed. */
    public Optional<FieldReadPlanArtifact> resolveFieldReadPlan(String ref) {
        if (ref == null || ref.isBlank() || fieldReadPlanArtifactRepository == null) {
            return Optional.empty();
        }
        return fieldReadPlanArtifactRepository.findActiveByRef(ref, clock.instant());
    }

    /** Dereferences a fieldWritePlanRef; empty when absent, expired or malformed. */
    public Optional<FieldWritePlanArtifact> resolveFieldWritePlan(String ref) {
        if (ref == null || ref.isBlank() || fieldWritePlanArtifactRepository == null) {
            return Optional.empty();
        }
        return fieldWritePlanArtifactRepository.findActiveByRef(ref, clock.instant());
    }

    @SuppressWarnings("unchecked")
    private static List<DataPredicateArtifact.Entry> toDataScopeEntries(Map<String, Object> obligations) {
        Object raw = obligations.get("dataScopes");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<DataPredicateArtifact.Entry> entries = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Object scopeType = map.get("scopeType");
            Object mergeMode = map.get("mergeMode");
            if (!(scopeType instanceof String s) || !(mergeMode instanceof String m)) {
                continue;
            }
            Object scopeCode = map.get("scopeCode");
            Object customPolicyId = map.get("customPolicyId");
            entries.add(new DataPredicateArtifact.Entry(
                    s,
                    scopeCode instanceof String code ? code : null,
                    customPolicyId instanceof Number n ? n.longValue() : null,
                    m,
                    parseIdList(map.get("teamIds"))));
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static List<FieldReadPlanArtifact.Entry> toFieldReadEntries(Map<String, Object> obligations) {
        Object raw = obligations.get("fieldGrants");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<FieldReadPlanArtifact.Entry> entries = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Object fieldId = map.get("fieldId");
            Object policy = map.get("policy");
            if (!(fieldId instanceof Number id) || !(policy instanceof String p)) {
                continue;
            }
            Object maskType = map.get("maskStrategyType");
            Object maskConfig = map.get("maskConfig");
            entries.add(new FieldReadPlanArtifact.Entry(
                    id.longValue(),
                    p,
                    maskType instanceof String t ? t : null,
                    maskConfig instanceof Map<?, ?> c ? (Map<String, Object>) c : null));
        }
        return entries;
    }

    private static List<FieldWritePlanArtifact.Entry> toFieldWriteEntries(Map<String, Object> obligations) {
        Object raw = obligations.get("fieldGrants");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<FieldWritePlanArtifact.Entry> entries = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Object fieldId = map.get("fieldId");
            if (fieldId instanceof Number id) {
                entries.add(new FieldWritePlanArtifact.Entry(
                        id.longValue(), Boolean.TRUE.equals(map.get("writable"))));
            }
        }
        return entries;
    }

    private static List<Long> parseIdList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Number n) {
                ids.add(n.longValue());
            }
        }
        return ids;
    }

    public record Result(
            String decision,
            @NumericLong long permissionVersion,
            String dataPredicateRef,
            String fieldReadPlanRef,
            String fieldWritePlanRef,
            Instant expiresAt) {
    }
}
