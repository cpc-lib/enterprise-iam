package com.enterprise.iam.audit.application.service;

import com.enterprise.iam.audit.application.port.out.AuditLogRepository;
import com.enterprise.iam.audit.application.query.AuditLogQuery;
import com.enterprise.iam.audit.domain.model.AuditLog;

import java.util.List;
import java.util.Objects;

public final class QueryAuditLogUseCase {

    private static final int DEFAULT_LIMIT = 100;

    private final AuditLogRepository auditLogRepository;

    public QueryAuditLogUseCase(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
    }

    public List<AuditLog> query(AuditLogQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        int limit = query.limit() > 0 ? query.limit() : DEFAULT_LIMIT;
        return auditLogRepository.findByCriteria(
                query.tenantId(),
                query.actorId(),
                query.action(),
                query.from(),
                query.to(),
                limit);
    }
}
