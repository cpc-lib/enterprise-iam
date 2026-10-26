package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.command.ChangePasswordCommand;
import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.out.PasswordHasher;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.domain.exception.PasswordChangeRejectedException;
import com.enterprise.iam.auth.domain.exception.PasswordPolicyViolationException;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.LockedCredentialRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.LockedSecurityStateRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.PasswordChangeSessionRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredLoginDeliveryRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredRefreshTokenRow;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalJdbcPasswordChangerTest {

    private static final Instant NOW = Instant.parse("2026-10-11T04:00:00Z");
    private static final String RAW_TOKEN = "rt1.key." + "a".repeat(43);
    private static final String CURRENT_PASSWORD = "current-password";
    private static final String NEW_PASSWORD = "replacement-password";
    private static final String CURRENT_PHC = "$argon2id$current";
    private static final String NEW_PHC =
            "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$bmV3LWhhc2g";

    private AuthPersistenceMapper mapper;
    private PasswordHasher passwordHasher;
    private SessionProjectionOutboxAppender projectionAppender;
    private LoginAuditOutboxAppender auditAppender;
    private PlatformTransactionManager transactionManager;
    private TransactionalJdbcPasswordChanger changer;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthPersistenceMapper.class);
        passwordHasher = mock(PasswordHasher.class);
        projectionAppender = mock(SessionProjectionOutboxAppender.class);
        auditAppender = mock(LoginAuditOutboxAppender.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AtomicLong ids = new AtomicLong(9000L);
        changer = new TransactionalJdbcPasswordChanger(
                mapper,
                transactionManager,
                passwordHasher,
                refreshTokenHasher(),
                ids::incrementAndGet,
                projectionAppender,
                auditAppender,
                Clock.fixed(NOW, ZoneOffset.UTC),
                enabledProperties());
    }

    @Test
    void changesCredentialKeepsCurrentSessionAndRevokesEveryOtherActiveSession() {
        stubLockedState();
        when(passwordHasher.verify(any(char[].class), eq(CURRENT_PHC)))
                .thenReturn(true, false);
        when(passwordHasher.encode(any(char[].class))).thenReturn(NEW_PHC);
        when(mapper.updatePasswordCredential(7L, 11L, NEW_PHC, 3L, NOW)).thenReturn(1);
        when(mapper.advanceSecurityStateForPasswordChange(7L, 11L, 5L, 3L, NOW))
                .thenReturn(1);
        when(mapper.revokeSessionsForPasswordChange(7L, 11L, List.of(102L), NOW))
                .thenReturn(1);

        ChangePasswordCommand command = command(NEW_PASSWORD);
        changer.changePassword(command);

        assertThat(command.isDestroyed()).isTrue();
        verify(mapper).revokeSessionsForPasswordChange(7L, 11L, List.of(102L), NOW);
        verify(mapper).revokeSessionRefreshTokensForPasswordChange(
                7L, 11L, List.of(102L), NOW);

        ArgumentCaptor<SessionSecurityProjection> projections =
                ArgumentCaptor.forClass(SessionSecurityProjection.class);
        verify(projectionAppender).append(eq(9001L), projections.capture(), eq(NOW));
        verify(projectionAppender).append(eq(9002L), projections.capture(), eq(NOW));
        assertThat(projections.getAllValues()).containsExactly(
                new SessionSecurityProjection(
                        7L,
                        11L,
                        101L,
                        6L,
                        4L,
                        SessionProjectionStatus.ACTIVE,
                        NOW.plusSeconds(300),
                        NOW.plusSeconds(3600)),
                new SessionSecurityProjection(
                        7L,
                        11L,
                        102L,
                        6L,
                        9L,
                        SessionProjectionStatus.REVOKED,
                        NOW.plusSeconds(200),
                        NOW.plusSeconds(3500)));
        verify(auditAppender).appendPasswordChanged(
                9003L, 7L, 11L, 101L, 4L, "request-0001", NOW);

        InOrder locks = inOrder(mapper);
        locks.verify(mapper).findRefreshTokenByHash(any(byte[].class));
        locks.verify(mapper).findLoginDeliveryForUpdate(7L, 101L);
        locks.verify(mapper).findSecurityStateForUpdate(7L, 11L);
        locks.verify(mapper).findCredentialForUpdate(7L, 11L);
        locks.verify(mapper).findActiveSessionsForPasswordChange(7L, 11L);
        locks.verify(mapper).findRefreshTokenByHashForUpdate(any(byte[].class));
    }

    @Test
    void rejectsWrongCurrentPasswordBeforeAnyMutation() {
        stubLockedState();
        when(passwordHasher.verify(any(char[].class), eq(CURRENT_PHC))).thenReturn(false);
        ChangePasswordCommand command = command(NEW_PASSWORD);

        assertThatThrownBy(() -> changer.changePassword(command))
                .isInstanceOf(PasswordChangeRejectedException.class);

        assertThat(command.isDestroyed()).isTrue();
        verify(mapper, never()).updatePasswordCredential(
                anyLong(), anyLong(), any(), anyLong(), any());
        verify(mapper, never()).advanceSecurityStateForPasswordChange(
                anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(projectionAppender, never()).append(anyLong(), any(), any());
        verify(auditAppender, never()).appendPasswordChanged(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void rejectsPasswordOutsideFrozenPolicyBeforeAnyMutation() {
        stubLockedState();
        when(passwordHasher.verify(any(char[].class), eq(CURRENT_PHC))).thenReturn(true);
        ChangePasswordCommand command = command("too-short");

        assertThatThrownBy(() -> changer.changePassword(command))
                .isInstanceOf(PasswordPolicyViolationException.class);

        assertThat(command.isDestroyed()).isTrue();
        verify(passwordHasher, never()).encode(any(char[].class));
        verify(mapper, never()).updatePasswordCredential(
                anyLong(), anyLong(), any(), anyLong(), any());
        verify(projectionAppender, never()).append(anyLong(), any(), any());
        verify(auditAppender, never()).appendPasswordChanged(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void rejectsReuseOfCurrentPasswordBeforeAnyMutation() {
        stubLockedState();
        when(passwordHasher.verify(any(char[].class), eq(CURRENT_PHC)))
                .thenReturn(true, true);
        ChangePasswordCommand command = command(NEW_PASSWORD);

        assertThatThrownBy(() -> changer.changePassword(command))
                .isInstanceOf(PasswordPolicyViolationException.class);

        assertThat(command.isDestroyed()).isTrue();
        verify(passwordHasher, never()).encode(any(char[].class));
        verify(mapper, never()).updatePasswordCredential(
                anyLong(), anyLong(), any(), anyLong(), any());
    }

    @Test
    void unknownRefreshRunsDummyPasswordWorkAndRejectsBeforeTransaction() {
        when(mapper.findRefreshTokenByHash(any(byte[].class))).thenReturn(List.of());
        ChangePasswordCommand command = command(NEW_PASSWORD);

        assertThatThrownBy(() -> changer.changePassword(command))
                .isInstanceOf(PasswordChangeRejectedException.class);

        verify(passwordHasher).verifyAgainstDummy(any(char[].class));
        verify(transactionManager, never()).getTransaction(any());
        verify(mapper, never()).findSecurityStateForUpdate(anyLong(), anyLong());
        assertThat(command.isDestroyed()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "8, 11, 101",
            "7, 12, 101",
            "7, 11, 999"
    })
    void refreshMustBelongToTheTrustedGatewayPrincipal(
            long trustedTenantId,
            long trustedUserId,
            long trustedSessionId) {
        StoredRefreshTokenRow currentToken = new StoredRefreshTokenRow(
                201L, 7L, 11L, 101L, 301L, "ACTIVE", NOW.plusSeconds(600));
        when(mapper.findRefreshTokenByHash(any(byte[].class))).thenReturn(List.of(currentToken));
        ChangePasswordCommand command = new ChangePasswordCommand(
                RAW_TOKEN.toCharArray(),
                CURRENT_PASSWORD.toCharArray(),
                NEW_PASSWORD.toCharArray(),
                trustedTenantId,
                trustedUserId,
                trustedSessionId,
                "request-0001");

        assertThatThrownBy(() -> changer.changePassword(command))
                .isInstanceOf(PasswordChangeRejectedException.class);

        verify(passwordHasher).verifyAgainstDummy(any(char[].class));
        verify(transactionManager, never()).getTransaction(any());
        assertThat(command.isDestroyed()).isTrue();
    }

    @Test
    void projectionFailureMarksTheWholePasswordTransactionForRollback() {
        stubLockedState();
        when(passwordHasher.verify(any(char[].class), eq(CURRENT_PHC)))
                .thenReturn(true, false);
        when(passwordHasher.encode(any(char[].class))).thenReturn(NEW_PHC);
        when(mapper.updatePasswordCredential(7L, 11L, NEW_PHC, 3L, NOW)).thenReturn(1);
        when(mapper.advanceSecurityStateForPasswordChange(7L, 11L, 5L, 3L, NOW))
                .thenReturn(1);
        when(mapper.revokeSessionsForPasswordChange(7L, 11L, List.of(102L), NOW))
                .thenReturn(1);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(projectionAppender).append(anyLong(), any(), eq(NOW));

        assertThatThrownBy(() -> changer.changePassword(command(NEW_PASSWORD)))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessageContaining("transaction failed");

        verify(transactionManager).rollback(any());
        verify(auditAppender, never()).appendPasswordChanged(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
    }

    private void stubLockedState() {
        StoredRefreshTokenRow currentToken = new StoredRefreshTokenRow(
                201L, 7L, 11L, 101L, 301L, "ACTIVE", NOW.plusSeconds(600));
        when(mapper.findRefreshTokenByHash(any(byte[].class))).thenReturn(List.of(currentToken));
        when(mapper.findLoginDeliveryForUpdate(7L, 101L)).thenReturn(List.of(
                new StoredLoginDeliveryRow(
                        7L, 11L, 101L, "login-request", "CONFIRMED", NOW.minusSeconds(1))));
        when(mapper.findSecurityStateForUpdate(7L, 11L))
                .thenReturn(List.of(new LockedSecurityStateRow(5L, 3L)));
        when(mapper.findCredentialForUpdate(7L, 11L))
                .thenReturn(List.of(new LockedCredentialRow(CURRENT_PHC, 3L, "ACTIVE")));
        when(mapper.findActiveSessionsForPasswordChange(7L, 11L)).thenReturn(List.of(
                new PasswordChangeSessionRow(
                        101L, NOW.plusSeconds(300), NOW.plusSeconds(3600), 4L),
                new PasswordChangeSessionRow(
                        102L, NOW.plusSeconds(200), NOW.plusSeconds(3500), 8L)));
        when(mapper.findRefreshTokenByHashForUpdate(any(byte[].class)))
                .thenReturn(List.of(currentToken));
    }

    private static ChangePasswordCommand command(String newPassword) {
        return new ChangePasswordCommand(
                RAW_TOKEN.toCharArray(),
                CURRENT_PASSWORD.toCharArray(),
                newPassword.toCharArray(),
                7L,
                11L,
                101L,
                "request-0001");
    }

    private static HmacSha256RefreshTokenHasher refreshTokenHasher() {
        return new HmacSha256RefreshTokenHasher(new RefreshTokenHashKey(
                "key", new SecretKeySpec(new byte[32], "HmacSHA256")));
    }

    private static SessionIssuanceProperties enabledProperties() {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(1);
        return properties;
    }
}
