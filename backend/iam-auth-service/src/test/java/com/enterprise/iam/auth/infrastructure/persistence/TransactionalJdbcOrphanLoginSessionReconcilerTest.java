package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredLoginDeliveryRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredSessionRow;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalJdbcOrphanLoginSessionReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:31:00Z");

    private AuthPersistenceMapper mapper;
    private SessionProjectionOutboxAppender projectionAppender;
    private LoginAuditOutboxAppender auditAppender;
    private TransactionalJdbcOrphanLoginSessionReconciler reconciler;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthPersistenceMapper.class);
        projectionAppender = mock(SessionProjectionOutboxAppender.class);
        auditAppender = mock(LoginAuditOutboxAppender.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AtomicLong ids = new AtomicLong(9000L);
        reconciler = new TransactionalJdbcOrphanLoginSessionReconciler(
                mapper,
                transactionManager,
                ids::incrementAndGet,
                projectionAppender,
                auditAppender,
                Clock.fixed(NOW, ZoneOffset.UTC),
                enabledProperties());
    }

    @Test
    void revokesOneExpiredPendingDeliveryAndEmitsBothDurableFacts() {
        StoredLoginDeliveryRow delivery = delivery(101L);
        when(mapper.findNextExpiredLoginDeliveryForUpdate(NOW))
                .thenReturn(List.of(delivery))
                .thenReturn(List.of());
        when(mapper.lockTokenVersion(7L, 11L)).thenReturn(List.of(5L));
        when(mapper.findSessionForUpdate(7L, 101L)).thenReturn(List.of(
                new StoredSessionRow(
                        11L, "ACTIVE", NOW.plusSeconds(300), NOW.plusSeconds(3600), 3L)));
        when(mapper.revokeOrphanSession(7L, 101L, NOW)).thenReturn(1);
        when(mapper.revokeOrphanSessionRefreshTokens(7L, 101L, NOW)).thenReturn(1);
        when(mapper.markLoginDeliveryReconciled(7L, 101L, NOW)).thenReturn(1);

        assertThat(reconciler.reconcileExpired()).isEqualTo(1);

        ArgumentCaptor<SessionSecurityProjection> projection =
                ArgumentCaptor.forClass(SessionSecurityProjection.class);
        verify(projectionAppender).append(org.mockito.ArgumentMatchers.eq(9001L),
                projection.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(projection.getValue().status()).isEqualTo(SessionProjectionStatus.REVOKED);
        assertThat(projection.getValue().sessionVersion()).isEqualTo(4L);
        assertThat(projection.getValue().tokenVersion()).isEqualTo(5L);
        verify(auditAppender).appendOrphanReconciled(
                9002L, 7L, 11L, 101L, 4L, "req-1", NOW);
        verify(mapper).markLoginDeliveryReconciled(7L, 101L, NOW);
    }

    @Test
    void terminalSessionOnlyClosesDeliveryWithoutDuplicateEvents() {
        StoredLoginDeliveryRow delivery = delivery(101L);
        when(mapper.findNextExpiredLoginDeliveryForUpdate(NOW))
                .thenReturn(List.of(delivery))
                .thenReturn(List.of());
        when(mapper.lockTokenVersion(7L, 11L)).thenReturn(List.of(5L));
        when(mapper.findSessionForUpdate(7L, 101L)).thenReturn(List.of(
                new StoredSessionRow(
                        11L, "REVOKED", NOW.minusSeconds(1), NOW.plusSeconds(3600), 4L)));
        when(mapper.markLoginDeliveryReconciled(7L, 101L, NOW)).thenReturn(1);

        assertThat(reconciler.reconcileExpired()).isEqualTo(1);

        verify(projectionAppender, never()).append(
                org.mockito.ArgumentMatchers.anyLong(),
                any(SessionSecurityProjection.class),
                any(Instant.class));
        verify(auditAppender, never()).appendOrphanReconciled(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                any(Instant.class));
        verify(mapper).revokeOrphanSessionRefreshTokens(7L, 101L, NOW);
        verify(mapper).markLoginDeliveryReconciled(7L, 101L, NOW);
    }

    private static StoredLoginDeliveryRow delivery(long sessionId) {
        return new StoredLoginDeliveryRow(
                7L, 11L, sessionId, "req-1", "PENDING", NOW.minusSeconds(1));
    }

    private static SessionIssuanceProperties enabledProperties() {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(1);
        return properties;
    }
}
