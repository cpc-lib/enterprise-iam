package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.PermissionApprovalRepository;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.ResourceShareExecutionPort;
import com.enterprise.iam.identity.application.port.out.TeamRoleAssignmentPort;
import com.enterprise.iam.identity.application.port.out.UserRoleRepository;
import com.enterprise.iam.identity.domain.model.ApprovalRequestType;
import com.enterprise.iam.identity.domain.model.ApprovalStatus;
import com.enterprise.iam.identity.domain.model.PermissionApprovalRequest;
import com.enterprise.iam.identity.domain.model.SodConflictCheckResult;
import com.enterprise.iam.identity.domain.model.UserRole;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PermissionApprovalUseCase {

    private final PermissionApprovalRepository approvalRepository;
    private final UserRoleRepository userRoleRepository;
    private final SodPolicyUseCase sodPolicyUseCase;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;
    private final TeamRoleAssignmentPort teamRoleAssignmentPort;
    private final ResourceShareExecutionPort resourceShareExecutionPort;

    public PermissionApprovalUseCase(
            PermissionApprovalRepository approvalRepository,
            UserRoleRepository userRoleRepository,
            SodPolicyUseCase sodPolicyUseCase,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this(approvalRepository, userRoleRepository, sodPolicyUseCase, idGenerator, clock, null, null);
    }

    public PermissionApprovalUseCase(
            PermissionApprovalRepository approvalRepository,
            UserRoleRepository userRoleRepository,
            SodPolicyUseCase sodPolicyUseCase,
            PositiveIdGenerator idGenerator,
            Clock clock,
            TeamRoleAssignmentPort teamRoleAssignmentPort,
            ResourceShareExecutionPort resourceShareExecutionPort) {
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.sodPolicyUseCase = sodPolicyUseCase;
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.teamRoleAssignmentPort = teamRoleAssignmentPort;
        this.resourceShareExecutionPort = resourceShareExecutionPort;
    }

    public PermissionApprovalRequest submitRequest(
            long tenantId,
            long applicantUserId,
            ApprovalRequestType requestType,
            long targetId,
            String targetName,
            String justification,
            int durationHours) {
        Objects.requireNonNull(requestType, "requestType must not be null");
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("targetName must not be blank");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("justification must not be blank");
        }
        if (requestType == ApprovalRequestType.ROLE_ASSIGN && sodPolicyUseCase != null) {
            Set<Long> candidateRoles = new LinkedHashSet<>();
            for (UserRole active : userRoleRepository.findActiveByUser(tenantId, applicantUserId)) {
                candidateRoles.add(active.roleId());
            }
            candidateRoles.add(targetId);
            SodConflictCheckResult check = sodPolicyUseCase.checkRoleConflict(tenantId, candidateRoles);
            if (check.violated()) {
                throw new IllegalStateException(
                        "IAM_SOD_CONFLICT_VIOLATION: Role request violates SoD policy ["
                                + check.violatedPolicyCode() + " - " + check.violatedPolicyName() + "]");
            }
        }

        Instant now = clock.instant();
        PermissionApprovalRequest request = new PermissionApprovalRequest(
                idGenerator.nextId(),
                tenantId,
                applicantUserId,
                requestType,
                targetId,
                targetName.trim(),
                justification.trim(),
                durationHours <= 0 ? 168 : durationHours,
                ApprovalStatus.PENDING,
                null,
                null,
                null,
                1L,
                now);
        return approvalRepository.save(request);
    }

    public List<PermissionApprovalRequest> listRequests(long tenantId, String status) {
        return approvalRepository.findByTenant(tenantId, status);
    }

    public PermissionApprovalRequest approveRequest(
            long tenantId,
            long requestId,
            long approverUserId,
            String comment) {
        PermissionApprovalRequest request = approvalRepository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));
        if (request.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("IAM_APPROVAL_ALREADY_DECIDED: request is not in PENDING status");
        }
        if (request.applicantUserId() == approverUserId) {
            throw new IllegalStateException("IAM_APPROVAL_SELF_FORBIDDEN: applicant cannot approve own request");
        }

        Instant now = clock.instant();
        if (request.requestType() == ApprovalRequestType.ROLE_ASSIGN) {
            if (sodPolicyUseCase != null) {
                Set<Long> candidateRoles = new LinkedHashSet<>();
                for (UserRole active : userRoleRepository.findActiveByUser(tenantId, request.applicantUserId())) {
                    candidateRoles.add(active.roleId());
                }
                candidateRoles.add(request.targetId());
                SodConflictCheckResult check = sodPolicyUseCase.checkRoleConflict(tenantId, candidateRoles);
                if (check.violated()) {
                    throw new IllegalStateException(
                            "IAM_SOD_CONFLICT_VIOLATION: Role assignment violates SoD policy ["
                                    + check.violatedPolicyCode() + " - " + check.violatedPolicyName() + "]");
                }
            }
            if (userRoleRepository.findActive(tenantId, request.applicantUserId(), request.targetId()).isEmpty()) {
                Instant expireTime = now.plus(Duration.ofHours(request.durationHours()));
                UserRole binding = new UserRole(
                        idGenerator.nextId(),
                        tenantId,
                        request.applicantUserId(),
                        request.targetId(),
                        UserRole.Status.ACTIVE,
                        now,
                        expireTime,
                        0L);
                userRoleRepository.save(binding);
            }
        } else if (request.requestType() == ApprovalRequestType.TEAM_ROLE_ASSIGN) {
            if (teamRoleAssignmentPort != null) {
                teamRoleAssignmentPort.assignTeamRole(
                        tenantId, request.applicantUserId(), request.targetId(), request.durationHours());
            }
        } else if (request.requestType() == ApprovalRequestType.RESOURCE_SHARE) {
            if (resourceShareExecutionPort != null) {
                resourceShareExecutionPort.grantResourceShare(
                        tenantId, request.applicantUserId(), request.targetId(), request.durationHours());
            }
        }

        String resolvedComment = (comment == null || comment.isBlank()) ? "Approved" : comment.trim();
        if (!approvalRepository.updateDecision(
                tenantId, requestId, ApprovalStatus.APPROVED, approverUserId, resolvedComment, now)) {
            throw new IllegalStateException("IAM_APPROVAL_CONFLICT: failed to transition approval status");
        }
        return approvalRepository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalStateException("Approved request could not be reloaded"));
    }

    public PermissionApprovalRequest rejectRequest(
            long tenantId,
            long requestId,
            long approverUserId,
            String comment) {
        PermissionApprovalRequest request = approvalRepository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));
        if (request.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("IAM_APPROVAL_ALREADY_DECIDED: request is not in PENDING status");
        }
        Instant now = clock.instant();
        String resolvedComment = (comment == null || comment.isBlank()) ? "Rejected" : comment.trim();
        if (!approvalRepository.updateDecision(
                tenantId, requestId, ApprovalStatus.REJECTED, approverUserId, resolvedComment, now)) {
            throw new IllegalStateException("IAM_APPROVAL_CONFLICT: failed to transition approval status");
        }
        return approvalRepository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalStateException("Rejected request could not be reloaded"));
    }
}
