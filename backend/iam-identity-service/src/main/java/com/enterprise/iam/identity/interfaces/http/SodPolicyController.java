package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.service.SodPolicyUseCase;
import com.enterprise.iam.identity.domain.model.SodConflictCheckResult;
import com.enterprise.iam.identity.domain.model.SodPolicy;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sod-policies")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class SodPolicyController {

    private final SodPolicyUseCase sodPolicyUseCase;

    public SodPolicyController(SodPolicyUseCase sodPolicyUseCase) {
        this.sodPolicyUseCase = sodPolicyUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SodPolicyDto>>> listPolicies(@RequestParam Long tenantId) {
        String traceId = UUID.randomUUID().toString();
        List<SodPolicyDto> dtos = sodPolicyUseCase.listPolicies(tenantId).stream()
                .map(SodPolicyDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SodPolicyDto>> createPolicy(
            @Valid @RequestBody CreateSodPolicyRequest request) {
        String traceId = UUID.randomUUID().toString();
        SodPolicy created = sodPolicyUseCase.createPolicy(
                request.tenantId(),
                request.policyCode(),
                request.policyName(),
                request.description(),
                request.conflictingRoleIds(),
                request.maxAllowedRoles() == null ? 1 : request.maxAllowedRoles(),
                request.enforcementMode(),
                request.severity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(SodPolicyDto.from(created), traceId));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SodPolicyDto>> toggleStatus(
            @PathVariable Long id,
            @Valid @RequestBody ToggleSodStatusRequest request) {
        String traceId = UUID.randomUUID().toString();
        SodPolicy updated = sodPolicyUseCase.toggleStatus(request.tenantId(), id, request.status());
        return ResponseEntity.ok(ApiResponse.ok(SodPolicyDto.from(updated), traceId));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<SodConflictEvaluationDto>> evaluateConflict(
            @Valid @RequestBody EvaluateSodConflictRequest request) {
        String traceId = UUID.randomUUID().toString();
        SodConflictCheckResult result = sodPolicyUseCase.checkRoleConflict(
                request.tenantId(),
                request.roleIds());
        return ResponseEntity.ok(ApiResponse.ok(SodConflictEvaluationDto.from(result), traceId));
    }

    public record CreateSodPolicyRequest(
            @NotNull Long tenantId,
            @NotBlank String policyCode,
            @NotBlank String policyName,
            String description,
            @NotEmpty List<Long> conflictingRoleIds,
            Integer maxAllowedRoles,
            String enforcementMode,
            String severity) {
    }

    public record ToggleSodStatusRequest(
            @NotNull Long tenantId,
            @NotBlank String status) {
    }

    public record EvaluateSodConflictRequest(
            @NotNull Long tenantId,
            @NotEmpty List<Long> roleIds) {
    }

    public record SodPolicyDto(
            String id,
            String tenantId,
            String policyCode,
            String policyName,
            String description,
            List<String> conflictingRoleIds,
            int maxAllowedRoles,
            String enforcementMode,
            String severity,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public static SodPolicyDto from(SodPolicy p) {
            return new SodPolicyDto(
                    Long.toUnsignedString(p.id()),
                    Long.toUnsignedString(p.tenantId()),
                    p.policyCode(),
                    p.policyName(),
                    p.description(),
                    p.conflictingRoleIds().stream().map(Long::toUnsignedString).toList(),
                    p.maxAllowedRoles(),
                    p.enforcementMode(),
                    p.severity(),
                    p.status(),
                    p.version(),
                    p.createdAt(),
                    p.updatedAt());
        }
    }

    public record SodConflictEvaluationDto(
            boolean violated,
            String violatedPolicyCode,
            String violatedPolicyName,
            List<String> conflictingRoleIds) {

        public static SodConflictEvaluationDto from(SodConflictCheckResult r) {
            return new SodConflictEvaluationDto(
                    r.violated(),
                    r.violatedPolicyCode(),
                    r.violatedPolicyName(),
                    r.conflictingRoleIds().stream().map(Long::toUnsignedString).toList());
        }
    }
}
