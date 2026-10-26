package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.ApprovalStatus;
import com.enterprise.iam.identity.domain.model.PermissionApprovalRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PermissionApprovalRepository {

    PermissionApprovalRequest save(PermissionApprovalRequest request);

    Optional<PermissionApprovalRequest> findById(long tenantId, long id);

    List<PermissionApprovalRequest> findByTenant(long tenantId, String status);

    boolean updateDecision(
            long tenantId,
            long id,
            ApprovalStatus status,
            long approverUserId,
            String comment,
            Instant decidedAt);
}
