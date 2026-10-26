package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.command.ChangePasswordCommand;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.in.ChangePasswordUseCase;
import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.out.PasswordHasher;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.application.service.PasswordChangePolicy;
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
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Changes one credential and advances every related revocation fence in one
 * relation transaction. A signed Gateway principal and the refresh cookie
 * must identify the same current session that remains active.
 */
public final class TransactionalJdbcPasswordChanger implements ChangePasswordUseCase {

    private final AuthPersistenceMapper mapper;
    private final TransactionTemplate transaction;
    private final PasswordHasher passwordHasher;
    private final HmacSha256RefreshTokenHasher refreshTokenHasher;
    private final PositiveIdGenerator idGenerator;
    private final SessionProjectionOutboxAppender projectionAppender;
    private final LoginAuditOutboxAppender auditAppender;
    private final PasswordChangePolicy passwordPolicy;
    private final Clock clock;

    public TransactionalJdbcPasswordChanger(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            PasswordHasher passwordHasher,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            PositiveIdGenerator idGenerator,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
            Clock clock,
            SessionIssuanceProperties properties) {
        this(
                mapper,
                transactionManager,
                passwordHasher,
                refreshTokenHasher,
                idGenerator,
                projectionAppender,
                auditAppender,
                new PasswordChangePolicy(),
                clock,
                properties);
    }

    TransactionalJdbcPasswordChanger(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            PasswordHasher passwordHasher,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            PositiveIdGenerator idGenerator,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
            PasswordChangePolicy passwordPolicy,
            Clock clock,
            SessionIssuanceProperties properties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.passwordHasher = Objects.requireNonNull(
                passwordHasher, "passwordHasher must not be null");
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher, "refreshTokenHasher must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.projectionAppender = Objects.requireNonNull(
                projectionAppender, "projectionAppender must not be null");
        this.auditAppender = Objects.requireNonNull(
                auditAppender, "auditAppender must not be null");
        this.passwordPolicy = Objects.requireNonNull(
                passwordPolicy, "passwordPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(properties, "properties must not be null").validateEnabled();
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        byte[] tokenHash = null;
        try {
            tokenHash = hashPresentedToken(command);
            StoredRefreshTokenRow located = locateCurrentToken(tokenHash);
            if (located == null || !matchesTrustedSession(command, located)) {
                verifyCurrentPasswordAgainstDummy(command);
                throw new PasswordChangeRejectedException();
            }
            byte[] transactionHash = tokenHash;
            transaction.executeWithoutResult(status -> changeInsideTransaction(
                    command, located, transactionHash));
        } catch (PasswordChangeRejectedException | PasswordPolicyViolationException exception) {
            throw exception;
        } catch (PasswordChangeException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PasswordChangeException("password change transaction failed", exception);
        } finally {
            if (tokenHash != null) {
                Arrays.fill(tokenHash, (byte) 0);
            }
            command.destroy();
        }
    }

    private void changeInsideTransaction(
            ChangePasswordCommand command,
            StoredRefreshTokenRow located,
            byte[] tokenHash) {
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());

        StoredLoginDeliveryRow delivery = lockLoginDelivery(located);
        LockedSecurityStateRow securityState = lockSecurityState(located);
        LockedCredentialRow credential = lockCredential(located);
        List<PasswordChangeSessionRow> activeSessions = lockActiveSessions(located);
        StoredRefreshTokenRow currentRefresh = lockCurrentRefresh(tokenHash);

        PasswordChangeSessionRow currentSession = requireCurrentSession(
                located, activeSessions, now);
        requireCurrentRefresh(located, currentRefresh, now);
        requireUsableDelivery(located, delivery, now);
        requireConsistentVersions(securityState, credential);
        verifyCurrentPassword(command, credential);

        char[] normalizedNewPassword = command.newPasswordCopy();
        try {
            char[] policyApproved = passwordPolicy.normalizeNewPassword(normalizedNewPassword);
            try {
                if (passwordHasher.verify(policyApproved, credential.passwordPhc())) {
                    throw new PasswordPolicyViolationException();
                }
                String newPasswordPhc = passwordHasher.encode(policyApproved);
                requireArgon2idPhc(newPasswordPhc);
                persistMutation(
                        command,
                        located,
                        delivery,
                        securityState,
                        credential,
                        activeSessions,
                        currentSession,
                        newPasswordPhc,
                        now);
            } finally {
                Arrays.fill(policyApproved, '\0');
            }
        } finally {
            Arrays.fill(normalizedNewPassword, '\0');
        }
    }

    private void persistMutation(
            ChangePasswordCommand command,
            StoredRefreshTokenRow located,
            StoredLoginDeliveryRow delivery,
            LockedSecurityStateRow securityState,
            LockedCredentialRow credential,
            List<PasswordChangeSessionRow> activeSessions,
            PasswordChangeSessionRow currentSession,
            String newPasswordPhc,
            Instant now) {
        requireOneRow(mapper.updatePasswordCredential(
                located.tenantId(),
                located.userId(),
                newPasswordPhc,
                credential.passwordVersion(),
                now), "password credential update");
        requireOneRow(mapper.advanceSecurityStateForPasswordChange(
                located.tenantId(),
                located.userId(),
                securityState.tokenVersion(),
                securityState.passwordVersion(),
                now), "security-state version update");

        List<PasswordChangeSessionRow> revokedSessions = new ArrayList<>();
        for (PasswordChangeSessionRow session : activeSessions) {
            if (session.sessionId() != located.sessionId()) {
                revokedSessions.add(session);
            }
        }
        if (!revokedSessions.isEmpty()) {
            List<Long> revokedSessionIds = revokedSessions.stream()
                    .map(PasswordChangeSessionRow::sessionId)
                    .toList();
            int revoked = mapper.revokeSessionsForPasswordChange(
                    located.tenantId(), located.userId(), revokedSessionIds, now);
            if (revoked != revokedSessions.size()) {
                throw new PasswordChangeException(
                        "other-session revoke did not affect every locked active session");
            }
            mapper.revokeSessionRefreshTokensForPasswordChange(
                    located.tenantId(), located.userId(), revokedSessionIds, now);
        }

        if (delivery != null && "PENDING".equals(delivery.deliveryStatus())) {
            requireOneRow(mapper.confirmLoginDelivery(
                    located.tenantId(), located.sessionId(), now),
                    "login delivery confirmation");
        }

        long newTokenVersion = securityState.tokenVersion() + 1;
        projectionAppender.append(
                idGenerator.nextId(),
                projection(
                        located,
                        currentSession,
                        newTokenVersion,
                        currentSession.sessionVersion(),
                        SessionProjectionStatus.ACTIVE),
                now);
        for (PasswordChangeSessionRow revokedSession : revokedSessions) {
            projectionAppender.append(
                    idGenerator.nextId(),
                    projection(
                            located,
                            revokedSession,
                            newTokenVersion,
                            revokedSession.sessionVersion() + 1,
                            SessionProjectionStatus.REVOKED),
                    now);
        }
        auditAppender.appendPasswordChanged(
                idGenerator.nextId(),
                located.tenantId(),
                located.userId(),
                located.sessionId(),
                currentSession.sessionVersion(),
                command.requestId(),
                now);
    }

    private SessionSecurityProjection projection(
            StoredRefreshTokenRow currentToken,
            PasswordChangeSessionRow session,
            long tokenVersion,
            long sessionVersion,
            SessionProjectionStatus status) {
        return new SessionSecurityProjection(
                currentToken.tenantId(),
                currentToken.userId(),
                session.sessionId(),
                tokenVersion,
                sessionVersion,
                status,
                session.idleExpireAt(),
                session.absoluteExpireAt());
    }

    private byte[] hashPresentedToken(ChangePasswordCommand command) {
        char[] presented = command.presentedRefreshTokenCopy();
        try (SensitiveRefreshToken token = new SensitiveRefreshToken(presented)) {
            return refreshTokenHasher.hash(token);
        } finally {
            Arrays.fill(presented, '\0');
        }
    }

    private StoredRefreshTokenRow locateCurrentToken(byte[] tokenHash) {
        StoredRefreshTokenRow token = uniqueToken(mapper.findRefreshTokenByHash(tokenHash));
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        if (token == null
                || !"ACTIVE".equals(token.status())
                || !token.expireAt().isAfter(now)) {
            return null;
        }
        return token;
    }

    private boolean matchesTrustedSession(
            ChangePasswordCommand command,
            StoredRefreshTokenRow token) {
        return token.tenantId() == command.expectedTenantId()
                && token.userId() == command.expectedUserId()
                && token.sessionId() == command.expectedSessionId();
    }

    private StoredLoginDeliveryRow lockLoginDelivery(StoredRefreshTokenRow token) {
        List<StoredLoginDeliveryRow> rows = mapper.findLoginDeliveryForUpdate(
                token.tenantId(), token.sessionId());
        if (rows.size() > 1) {
            throw new PasswordChangeException("login delivery is not unique");
        }
        if (rows.isEmpty()) {
            return null;
        }
        StoredLoginDeliveryRow delivery = rows.get(0);
        if (delivery.tenantId() != token.tenantId()
                || delivery.userId() != token.userId()
                || delivery.sessionId() != token.sessionId()) {
            throw new PasswordChangeException("login delivery ownership is inconsistent");
        }
        return delivery;
    }

    private LockedSecurityStateRow lockSecurityState(StoredRefreshTokenRow token) {
        List<LockedSecurityStateRow> rows = mapper.findSecurityStateForUpdate(
                token.tenantId(), token.userId());
        if (rows.size() != 1) {
            throw new PasswordChangeException("authoritative security state is missing or duplicated");
        }
        LockedSecurityStateRow state = rows.get(0);
        if (state.tokenVersion() <= 0 || state.passwordVersion() <= 0) {
            throw new PasswordChangeException("authoritative security versions are invalid");
        }
        return state;
    }

    private LockedCredentialRow lockCredential(StoredRefreshTokenRow token) {
        List<LockedCredentialRow> rows = mapper.findCredentialForUpdate(
                token.tenantId(), token.userId());
        if (rows.size() > 1) {
            throw new PasswordChangeException("password credential is duplicated");
        }
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).status())) {
            throw new PasswordChangeRejectedException();
        }
        LockedCredentialRow credential = rows.get(0);
        if (credential.passwordPhc() == null || credential.passwordPhc().isBlank()) {
            throw new PasswordChangeException("active credential has no password PHC");
        }
        return credential;
    }

    private List<PasswordChangeSessionRow> lockActiveSessions(StoredRefreshTokenRow token) {
        List<PasswordChangeSessionRow> sessions = mapper.findActiveSessionsForPasswordChange(
                token.tenantId(), token.userId());
        Set<Long> sessionIds = new HashSet<>();
        for (PasswordChangeSessionRow session : sessions) {
            if (session.sessionId() <= 0
                    || session.sessionVersion() <= 0
                    || session.idleExpireAt() == null
                    || session.absoluteExpireAt() == null
                    || session.idleExpireAt().isAfter(session.absoluteExpireAt())
                    || !sessionIds.add(session.sessionId())) {
                throw new PasswordChangeException("active login-session state is invalid");
            }
        }
        return sessions;
    }

    private StoredRefreshTokenRow lockCurrentRefresh(byte[] tokenHash) {
        return uniqueToken(mapper.findRefreshTokenByHashForUpdate(tokenHash));
    }

    private PasswordChangeSessionRow requireCurrentSession(
            StoredRefreshTokenRow token,
            List<PasswordChangeSessionRow> sessions,
            Instant now) {
        for (PasswordChangeSessionRow session : sessions) {
            if (session.sessionId() == token.sessionId()) {
                if (!session.idleExpireAt().isAfter(now)
                        || !session.absoluteExpireAt().isAfter(now)) {
                    throw new PasswordChangeRejectedException();
                }
                return session;
            }
        }
        throw new PasswordChangeRejectedException();
    }

    private void requireCurrentRefresh(
            StoredRefreshTokenRow located,
            StoredRefreshTokenRow locked,
            Instant now) {
        if (locked == null
                || locked.id() != located.id()
                || locked.tenantId() != located.tenantId()
                || locked.userId() != located.userId()
                || locked.sessionId() != located.sessionId()
                || !"ACTIVE".equals(locked.status())
                || !locked.expireAt().isAfter(now)) {
            throw new PasswordChangeRejectedException();
        }
    }

    private void requireUsableDelivery(
            StoredRefreshTokenRow token,
            StoredLoginDeliveryRow delivery,
            Instant now) {
        if (delivery == null || "CONFIRMED".equals(delivery.deliveryStatus())) {
            return;
        }
        if (!"PENDING".equals(delivery.deliveryStatus())
                || !delivery.confirmBefore().isAfter(now)) {
            throw new PasswordChangeRejectedException();
        }
        if (delivery.tenantId() != token.tenantId()
                || delivery.userId() != token.userId()
                || delivery.sessionId() != token.sessionId()) {
            throw new PasswordChangeException("login delivery ownership is inconsistent");
        }
    }

    private void requireConsistentVersions(
            LockedSecurityStateRow securityState,
            LockedCredentialRow credential) {
        if (credential.passwordVersion() <= 0
                || credential.passwordVersion() != securityState.passwordVersion()) {
            throw new PasswordChangeException("credential and security password versions differ");
        }
    }

    private void verifyCurrentPassword(
            ChangePasswordCommand command,
            LockedCredentialRow credential) {
        char[] currentPassword = command.currentPasswordCopy();
        try {
            if (!passwordHasher.verify(currentPassword, credential.passwordPhc())) {
                throw new PasswordChangeRejectedException();
            }
        } finally {
            Arrays.fill(currentPassword, '\0');
        }
    }

    private void verifyCurrentPasswordAgainstDummy(ChangePasswordCommand command) {
        char[] currentPassword = command.currentPasswordCopy();
        try {
            passwordHasher.verifyAgainstDummy(currentPassword);
        } finally {
            Arrays.fill(currentPassword, '\0');
        }
    }

    private static StoredRefreshTokenRow uniqueToken(List<StoredRefreshTokenRow> rows) {
        if (rows.size() > 1) {
            throw new PasswordChangeException("refresh token hash is not unique");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void requireArgon2idPhc(String passwordPhc) {
        if (passwordPhc == null
                || !passwordPhc.startsWith("$argon2id$")
                || passwordPhc.length() > 255) {
            throw new PasswordChangeException("password hasher returned an invalid PHC");
        }
    }

    private static void requireOneRow(int updated, String operation) {
        if (updated != 1) {
            throw new PasswordChangeException(operation + " did not affect exactly one row");
        }
    }
}
