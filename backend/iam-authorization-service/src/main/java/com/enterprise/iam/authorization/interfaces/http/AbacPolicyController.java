package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.AbacPolicyUseCase;
import com.enterprise.iam.authorization.domain.model.AbacPolicy;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.service.ConditionDslEvaluator;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.common.web.json.NumericLong;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/abac-policies")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class AbacPolicyController {

    private final AbacPolicyUseCase abacPolicyUseCase;

    public AbacPolicyController(AbacPolicyUseCase abacPolicyUseCase) {
        this.abacPolicyUseCase = Objects.requireNonNull(abacPolicyUseCase, "abacPolicyUseCase must not be null");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AbacPolicyResponse>>> list(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId);
        List<AbacPolicyResponse> responses = abacPolicyUseCase.listByTenant(resolvedTenantId).stream()
                .map(AbacPolicyController::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses, traceId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AbacPolicyResponse>> create(
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @Valid @RequestBody CreateAbacPolicyRequest request) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(
                request.tenantId() != null ? request.tenantId() : queryTenantId,
                headerTenantId);
        AbacPolicy saved = abacPolicyUseCase.create(
                resolvedTenantId,
                request.policyCode(),
                request.policyName(),
                request.resourceType(),
                request.operation(),
                request.effect(),
                request.priority(),
                request.conditionExpr(),
                request.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(toResponse(saved), traceId));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AbacPolicyResponse>> updateStatus(
            @PathVariable("id") long id,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @Valid @RequestBody UpdateAbacPolicyStatusRequest request) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(
                request.tenantId() != null ? request.tenantId() : queryTenantId,
                headerTenantId);
        AbacPolicy updated = abacPolicyUseCase.updateStatus(resolvedTenantId, id, request.status());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(updated), traceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(
            @PathVariable("id") long id,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId);
        abacPolicyUseCase.delete(resolvedTenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", String.valueOf(id), "deleted", true), traceId));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<EvaluateConditionResponse>> evaluate(
            @Valid @RequestBody EvaluateConditionRequest request) {
        String traceId = UUID.randomUUID().toString();
        ConditionDslEvaluator.EvaluationResult result = abacPolicyUseCase.evaluateDryRun(
                request.conditionExpr(), request.attributes());
        EvaluateConditionResponse response = new EvaluateConditionResponse(
                result.valid(),
                result.matched(),
                result.tokenCount(),
                result.depth(),
                result.detail());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    private static long resolveTenantId(Long primary, Long fallback) {
        if (primary != null && primary > 0) {
            return primary;
        }
        if (fallback != null && fallback > 0) {
            return fallback;
        }
        throw new IllegalArgumentException("tenantId must be provided and positive");
    }

    private static AbacPolicyResponse toResponse(AbacPolicy policy) {
        return new AbacPolicyResponse(
                String.valueOf(policy.id()),
                String.valueOf(policy.tenantId()),
                policy.policyCode(),
                policy.policyName(),
                policy.resourceType(),
                policy.operation(),
                policy.effect().name(),
                policy.priority(),
                policy.conditionExpr(),
                policy.description(),
                policy.status(),
                policy.version(),
                policy.createdAt(),
                policy.updatedAt());
    }

    public record CreateAbacPolicyRequest(
            Long tenantId,
            @NotBlank String policyCode,
            @NotBlank String policyName,
            @NotBlank String resourceType,
            @NotBlank String operation,
            @NotNull GrantEffect effect,
            Integer priority,
            @NotBlank String conditionExpr,
            String description) {
    }

    public record UpdateAbacPolicyStatusRequest(
            Long tenantId,
            @NotBlank String status) {
    }

    public record EvaluateConditionRequest(
            @NotBlank String conditionExpr,
            Map<String, Object> attributes) {
    }

    public record EvaluateConditionResponse(
            boolean valid,
            boolean matched,
            int tokenCount,
            int depth,
            String detail) {
    }

    public record AbacPolicyResponse(
            String id,
            String tenantId,
            String policyCode,
            String policyName,
            String resourceType,
            String operation,
            String effect,
            int priority,
            String conditionExpr,
            String description,
            String status,
            @NumericLong long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}
