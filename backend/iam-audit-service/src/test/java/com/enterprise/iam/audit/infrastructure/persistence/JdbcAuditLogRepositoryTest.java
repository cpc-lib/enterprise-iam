package com.enterprise.iam.audit.infrastructure.persistence;

import com.enterprise.iam.audit.domain.model.AuditLog;
import com.enterprise.iam.audit.infrastructure.persistence.mapper.AuditLogPersistenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcAuditLogRepositoryTest {

    @Test
    void reportsDuplicateWhenSourceEventAlreadyExists() {
        AuditLogPersistenceMapper mapper = mock(AuditLogPersistenceMapper.class);
        when(mapper.insertAuditLogIfSourceEventAbsent(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(),
                any(), any(), anyString(), anyLong()))
                .thenReturn(1, 0);
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(mapper);
        AuditLog auditLog = new AuditLog(
                501L, 7L, 11L, "LOGIN_SUCCESS", "SESSION", 101L,
                "{}", Instant.parse("2026-10-04T12:30:00Z"));

        assertThat(repository.saveIfSourceEventAbsent(auditLog, "iam-auth-service", 9001L))
                .isTrue();
        assertThat(repository.saveIfSourceEventAbsent(auditLog, "iam-auth-service", 9001L))
                .isFalse();
    }

    @Test
    void distinguishesSourceEventRedeliveryFromUnrelatedKeyCollision() {
        AuditLogPersistenceMapper mapper = mock(AuditLogPersistenceMapper.class);
        when(mapper.insertAuditLogIfSourceEventAbsent(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(),
                any(), any(), anyString(), anyLong()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectIdBySourceEvent("iam-auth-service", 9001L)).thenReturn(501L);
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(mapper);
        AuditLog auditLog = new AuditLog(
                501L, 7L, 11L, "LOGIN_SUCCESS", "SESSION", 101L,
                "{}", Instant.parse("2026-10-04T12:30:00Z"));

        assertThat(repository.saveIfSourceEventAbsent(auditLog, "iam-auth-service", 9001L))
                .isFalse();
    }
}
