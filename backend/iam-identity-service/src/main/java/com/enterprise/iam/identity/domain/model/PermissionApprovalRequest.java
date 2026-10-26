package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public record PermissionApprovalRequest(
        long id,
        long tenantId,
        long applicantUserId,
        ApprovalRequestType requestType,
        long targetId,
        String targetName,
        String justification,
        int durationHours,
        ApprovalStatus status,
        Long approverUserId,
        String decisionComment,
        Instant decidedAt,
        long version,
        Instant createdAt) {

    public PermissionApprovalRequest {
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(targetName, "targetName must not be null");
        Objects.requireNonNull(justification, "justification must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (durationHours <= 0) {
            throw new IllegalArgumentException("durationHours must be positive");
        }
    }
}
