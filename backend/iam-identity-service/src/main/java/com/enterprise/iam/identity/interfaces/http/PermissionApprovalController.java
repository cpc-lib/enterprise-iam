package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.service.PermissionApprovalUseCase;
import com.enterprise.iam.identity.domain.model.ApprovalRequestType;
import com.enterprise.iam.identity.domain.model.PermissionApprovalRequest;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/approvals")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class PermissionApprovalController {

    private final PermissionApprovalUseCase permissionApprovalUseCase;

    public PermissionApprovalController(PermissionApprovalUseCase permissionApprovalUseCase) {
        this.permissionApprovalUseCase = permissionApprovalUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionApprovalDto>>> listRequests(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String status) {
        String traceId = UUID.randomUUID().toString();
        List<PermissionApprovalDto> dtos = permissionApprovalUseCase.listRequests(tenantId, status).stream()
                .map(PermissionApprovalDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionApprovalDto>> submitRequest(
            @Valid @RequestBody SubmitApprovalRequest request) {
        String traceId = UUID.randomUUID().toString();
        try {
            PermissionApprovalRequest saved = permissionApprovalUseCase.submitRequest(
                    request.tenantId(),
                    request.applicantUserId(),
                    ApprovalRequestType.valueOf(request.requestType()),
                    request.targetId(),
                    request.targetName(),
                    request.justification(),
                    request.durationHours() == null ? 168 : request.durationHours());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(PermissionApprovalDto.from(saved), traceId));
        } catch (IllegalStateException ex) {
            String code = ex.getMessage() != null && ex.getMessage().contains("IAM_SOD_CONFLICT_VIOLATION")
                    ? "IAM_SOD_CONFLICT_VIOLATION"
                    : "IAM_APPROVAL_SUBMIT_REJECTED";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(code, traceId));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PermissionApprovalDto>> approveRequest(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = UUID.randomUUID().toString();
        try {
            PermissionApprovalRequest approved = permissionApprovalUseCase.approveRequest(
                    request.tenantId(),
                    id,
                    request.approverUserId(),
                    request.comment());
            return ResponseEntity.ok(ApiResponse.ok(PermissionApprovalDto.from(approved), traceId));
        } catch (IllegalStateException ex) {
            String code = ex.getMessage() != null && ex.getMessage().contains("IAM_SOD_CONFLICT_VIOLATION")
                    ? "IAM_SOD_CONFLICT_VIOLATION"
                    : ex.getMessage() != null && ex.getMessage().contains("IAM_APPROVAL_SELF_FORBIDDEN")
                            ? "IAM_APPROVAL_SELF_FORBIDDEN"
                            : "IAM_APPROVAL_CONFLICT";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(code, traceId));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PermissionApprovalDto>> rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = UUID.randomUUID().toString();
        try {
            PermissionApprovalRequest rejected = permissionApprovalUseCase.rejectRequest(
                    request.tenantId(),
                    id,
                    request.approverUserId(),
                    request.comment());
            return ResponseEntity.ok(ApiResponse.ok(PermissionApprovalDto.from(rejected), traceId));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("IAM_APPROVAL_CONFLICT", traceId));
        }
    }

    public record SubmitApprovalRequest(
            @NotNull Long tenantId,
            @NotNull Long applicantUserId,
            @NotBlank String requestType,
            @NotNull Long targetId,
            @NotBlank String targetName,
            @NotBlank String justification,
            Integer durationHours) {
    }

    public record ApprovalDecisionRequest(
            @NotNull Long tenantId,
            @NotNull Long approverUserId,
            String comment) {
    }

    public record PermissionApprovalDto(
            String id,
            String tenantId,
            String applicantUserId,
            String requestType,
            String targetId,
            String targetName,
            String justification,
            int durationHours,
            String status,
            String approverUserId,
            String decisionComment,
            Instant decidedAt,
            long version,
            Instant createdAt) {

        public static PermissionApprovalDto from(PermissionApprovalRequest r) {
            return new PermissionApprovalDto(
                    Long.toUnsignedString(r.id()),
                    Long.toUnsignedString(r.tenantId()),
                    Long.toUnsignedString(r.applicantUserId()),
                    r.requestType().name(),
                    Long.toUnsignedString(r.targetId()),
                    r.targetName(),
                    r.justification(),
                    r.durationHours(),
                    r.status().name(),
                    r.approverUserId() == null ? null : Long.toUnsignedString(r.approverUserId()),
                    r.decisionComment(),
                    r.decidedAt(),
                    r.version(),
                    r.createdAt());
        }
    }
}
