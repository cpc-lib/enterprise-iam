package com.enterprise.iam.audit.application.port.out;

import com.enterprise.iam.audit.domain.model.AuditLog;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    boolean saveIfSourceEventAbsent(
            AuditLog auditLog,
            String sourceService,
            long sourceEventId);

    List<AuditLog> findByCriteria(
            long tenantId,
            Long actorId,
            String action,
            Instant from,
            Instant to,
            int limit);
}
