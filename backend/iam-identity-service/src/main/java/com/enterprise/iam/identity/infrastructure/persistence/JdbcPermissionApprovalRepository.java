package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.PermissionApprovalRepository;
import com.enterprise.iam.identity.domain.model.ApprovalRequestType;
import com.enterprise.iam.identity.domain.model.ApprovalStatus;
import com.enterprise.iam.identity.domain.model.PermissionApprovalRequest;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcPermissionApprovalRepository implements PermissionApprovalRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcPermissionApprovalRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public PermissionApprovalRequest save(PermissionApprovalRequest request) {
        int rows = mapper.insertPermissionApprovalRequest(
                request.id(),
                request.tenantId(),
                request.applicantUserId(),
                request.requestType().name(),
                request.targetId(),
                request.targetName(),
                request.justification(),
                request.durationHours(),
                request.status().name(),
                request.version(),
                request.createdAt());
        if (rows != 1) {
            throw new IllegalStateException("Failed to insert permission approval request");
        }
        return request;
    }

    @Override
    public Optional<PermissionApprovalRequest> findById(long tenantId, long id) {
        return Optional.ofNullable(mapper.selectPermissionApprovalById(tenantId, id))
                .map(JdbcPermissionApprovalRepository::toDomain);
    }

    @Override
    public List<PermissionApprovalRequest> findByTenant(long tenantId, String status) {
        return mapper.selectPermissionApprovalsByTenant(tenantId, status).stream()
                .map(JdbcPermissionApprovalRepository::toDomain)
                .toList();
    }

    @Override
    public boolean updateDecision(
            long tenantId,
            long id,
            ApprovalStatus status,
            long approverUserId,
            String comment,
            Instant decidedAt) {
        return mapper.updatePermissionApprovalDecision(
                tenantId,
                id,
                status.name(),
                approverUserId,
                comment,
                decidedAt) == 1;
    }

    private static PermissionApprovalRequest toDomain(IdentityPersistenceMapper.PermissionApprovalRow row) {
        return new PermissionApprovalRequest(
                row.id(),
                row.tenantId(),
                row.applicantUserId(),
                ApprovalRequestType.valueOf(row.requestType()),
                row.targetId(),
                row.targetName(),
                row.justification(),
                row.durationHours(),
                ApprovalStatus.valueOf(row.status()),
                row.approverUserId(),
                row.decisionComment(),
                row.decidedAt(),
                row.version(),
                row.createdAt());
    }
}
