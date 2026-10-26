package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.ResolveEffectivePoliciesUseCase;
import com.enterprise.iam.authorization.domain.model.DataPredicateArtifact;
import com.enterprise.iam.authorization.domain.model.FieldReadPlanArtifact;
import com.enterprise.iam.authorization.domain.model.FieldWritePlanArtifact;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.DataPredicateArtifactResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.EffectivePolicyRequest;
import com.enterprise.iam.authorization.interfaces.http.dto.FieldReadPlanArtifactResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.FieldWritePlanArtifactResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/authorization")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class AuthorizationPolicyController {

    private final ResolveEffectivePoliciesUseCase resolveEffectivePoliciesUseCase;

    public AuthorizationPolicyController(ResolveEffectivePoliciesUseCase resolveEffectivePoliciesUseCase) {
        this.resolveEffectivePoliciesUseCase = resolveEffectivePoliciesUseCase;
    }

    @PostMapping("/effective-policies")
    public ResponseEntity<ApiResponse<ResolveEffectivePoliciesUseCase.Result>> resolveEffectivePolicies(
            @Valid @RequestBody EffectivePolicyRequest request) {
        String traceId = UUID.randomUUID().toString();
        var result = resolveEffectivePoliciesUseCase.resolve(
                request.tenantId(), request.subjectId(), request.resourceId(),
                request.operationId(), request.permissionVersion(), request.requestId());
        return ResponseEntity.ok(ApiResponse.ok(result, traceId));
    }

    @GetMapping("/data-predicates/{ref}")
    public ResponseEntity<ApiResponse<DataPredicateArtifactResponse>> getDataPredicate(
            @PathVariable String ref) {
        String traceId = UUID.randomUUID().toString();
        Optional<DataPredicateArtifact> artifact = resolveEffectivePoliciesUseCase.resolveDataPredicate(ref);
        return artifact
                .map(a -> ResponseEntity.ok(
                        ApiResponse.ok(DataPredicateArtifactResponse.fromArtifact(a), traceId)))
                .orElseGet(() -> notFound("IAM_AUTHZ_DATA_PREDICATE_NOT_FOUND", traceId));
    }

    @GetMapping("/field-read-plans/{ref}")
    public ResponseEntity<ApiResponse<FieldReadPlanArtifactResponse>> getFieldReadPlan(
            @PathVariable String ref) {
        String traceId = UUID.randomUUID().toString();
        Optional<FieldReadPlanArtifact> artifact = resolveEffectivePoliciesUseCase.resolveFieldReadPlan(ref);
        return artifact
                .map(a -> ResponseEntity.ok(
                        ApiResponse.ok(FieldReadPlanArtifactResponse.fromArtifact(a), traceId)))
                .orElseGet(() -> notFound("IAM_AUTHZ_FIELD_READ_PLAN_NOT_FOUND", traceId));
    }

    @GetMapping("/field-write-plans/{ref}")
    public ResponseEntity<ApiResponse<FieldWritePlanArtifactResponse>> getFieldWritePlan(
            @PathVariable String ref) {
        String traceId = UUID.randomUUID().toString();
        Optional<FieldWritePlanArtifact> artifact = resolveEffectivePoliciesUseCase.resolveFieldWritePlan(ref);
        return artifact
                .map(a -> ResponseEntity.ok(
                        ApiResponse.ok(FieldWritePlanArtifactResponse.fromArtifact(a), traceId)))
                .orElseGet(() -> notFound("IAM_AUTHZ_FIELD_WRITE_PLAN_NOT_FOUND", traceId));
    }

    private static <T> ResponseEntity<ApiResponse<T>> notFound(String code, String traceId) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, code, null, traceId));
    }
}
