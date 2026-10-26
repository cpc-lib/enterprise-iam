package com.enterprise.iam.audit.application.service;

import com.enterprise.iam.audit.application.command.RecordAuditLogCommand;
import com.enterprise.iam.audit.application.model.RecordAuditLogResult;
import com.enterprise.iam.audit.application.port.out.AuditLogRepository;
import com.enterprise.iam.audit.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.audit.domain.model.AuditLog;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RecordAuditLogUseCase {

    private final AuditLogRepository auditLogRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public RecordAuditLogUseCase(
            AuditLogRepository auditLogRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public RecordAuditLogResult record(RecordAuditLogCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        long id = idGenerator.nextId();
        AuditLog auditLog = new AuditLog(
                id,
                command.tenantId(),
                command.actorId(),
                command.action(),
                command.resourceType(),
                command.resourceId(),
                command.detail(),
                now);
        auditLogRepository.save(auditLog);
        return new RecordAuditLogResult(
                auditLog.id(),
                auditLog.tenantId(),
                auditLog.actorId(),
                auditLog.action());
    }
}
