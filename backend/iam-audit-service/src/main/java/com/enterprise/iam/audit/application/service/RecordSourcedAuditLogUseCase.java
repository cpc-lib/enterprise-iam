package com.enterprise.iam.audit.application.service;

import com.enterprise.iam.audit.application.command.RecordSourcedAuditLogCommand;
import com.enterprise.iam.audit.application.port.out.AuditLogRepository;
import com.enterprise.iam.audit.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.audit.domain.model.AuditLog;

import java.util.Objects;

public final class RecordSourcedAuditLogUseCase {

    private final AuditLogRepository auditLogRepository;
    private final PositiveIdGenerator idGenerator;

    public RecordSourcedAuditLogUseCase(
            AuditLogRepository auditLogRepository,
            PositiveIdGenerator idGenerator) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    public boolean record(RecordSourcedAuditLogCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        AuditLog auditLog = new AuditLog(
                idGenerator.nextId(),
                command.tenantId(),
                command.actorId(),
                command.action(),
                command.resourceType(),
                command.resourceId(),
                command.detail(),
                command.occurredAt());
        return auditLogRepository.saveIfSourceEventAbsent(
                auditLog,
                command.sourceService(),
                command.sourceEventId());
    }
}
