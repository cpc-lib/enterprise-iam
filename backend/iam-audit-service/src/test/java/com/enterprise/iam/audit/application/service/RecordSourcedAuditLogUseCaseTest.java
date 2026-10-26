package com.enterprise.iam.audit.application.service;

import com.enterprise.iam.audit.application.command.RecordSourcedAuditLogCommand;
import com.enterprise.iam.audit.application.port.out.AuditLogRepository;
import com.enterprise.iam.audit.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.audit.domain.model.AuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordSourcedAuditLogUseCaseTest {

    @Test
    void preservesEventTimeAndReturnsRepositoryIdempotencyOutcome() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        PositiveIdGenerator idGenerator = mock(PositiveIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(501L);
        when(repository.saveIfSourceEventAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        RecordSourcedAuditLogUseCase useCase =
                new RecordSourcedAuditLogUseCase(repository, idGenerator);
        Instant occurredAt = Instant.parse("2026-10-04T12:30:00Z");

        boolean inserted = useCase.record(new RecordSourcedAuditLogCommand(
                "iam-auth-service", 9001L, 7L, 11L, "LOGIN_SUCCESS",
                "SESSION", 101L, "{}", occurredAt));

        assertThat(inserted).isFalse();
        ArgumentCaptor<AuditLog> auditLog = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).saveIfSourceEventAbsent(
                auditLog.capture(),
                org.mockito.ArgumentMatchers.eq("iam-auth-service"),
                org.mockito.ArgumentMatchers.eq(9001L));
        assertThat(auditLog.getValue().id()).isEqualTo(501L);
        assertThat(auditLog.getValue().createdAt()).isEqualTo(occurredAt);
    }
}
