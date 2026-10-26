package com.enterprise.iam.audit.infrastructure.persistence;

import com.enterprise.iam.audit.application.port.out.AuditLogRepository;
import com.enterprise.iam.audit.domain.model.AuditLog;
import com.enterprise.iam.audit.infrastructure.persistence.mapper.AuditLogPersistenceMapper;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JdbcAuditLogRepository implements AuditLogRepository {

    private final AuditLogPersistenceMapper mapper;

    public JdbcAuditLogRepository(AuditLogPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        int updated = mapper.insertAuditLog(
                auditLog.id(),
                auditLog.tenantId(),
                auditLog.actorId(),
                auditLog.action(),
                auditLog.resourceType(),
                auditLog.resourceId(),
                auditLog.detail(),
                auditLog.createdAt());
        if (updated != 1) {
            throw new IllegalStateException("audit log insert did not affect exactly one row");
        }
        return auditLog;
    }

    @Override
    public boolean saveIfSourceEventAbsent(
            AuditLog auditLog,
            String sourceService,
            long sourceEventId) {
        try {
            return mapper.insertAuditLogIfSourceEventAbsent(
                    auditLog.id(),
                    auditLog.tenantId(),
                    auditLog.actorId(),
                    auditLog.action(),
                    auditLog.resourceType(),
                    auditLog.resourceId(),
                    auditLog.detail(),
                    auditLog.createdAt(),
                    sourceService,
                    sourceEventId) == 1;
        } catch (DuplicateKeyException exception) {
            Long existingId = mapper.selectIdBySourceEvent(sourceService, sourceEventId);
            if (existingId != null) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public List<AuditLog> findByCriteria(
            long tenantId,
            Long actorId,
            String action,
            Instant from,
            Instant to,
            int limit) {
        String normalizedAction = action == null || action.isBlank() ? null : action;
        return mapper.selectByCriteria(tenantId, actorId, normalizedAction, from, to, limit);
    }
}
