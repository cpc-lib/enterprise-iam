package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.command.RefreshRotationCommand;
import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.RefreshRotationResult;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.RefreshTokenRotator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.application.service.RefreshRotationPolicy;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredLoginDeliveryRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredRefreshTokenRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredSessionRow;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.SecureOpaqueRefreshTokenGenerator;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.AccessTokenSigningRequest;
import com.enterprise.iam.common.security.access.SignedAccessToken;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Owns the commit-before-return transaction for RFC 9700 refresh rotation:
 * CAS the presented token to ROTATED, insert exactly one successor, slide the
 * session idle window, append the session projection outbox event and only
 * then hand out the new credential pair. Replay of a rotated token revokes the
 * whole family, the session included.
 */
public final class TransactionalJdbcRefreshTokenRotator implements RefreshTokenRotator {

    private final AuthPersistenceMapper mapper;
    private final TransactionTemplate transaction;
    private final AccessTokenSigner accessTokenSigner;
    private final PositiveIdGenerator idGenerator;
    private final SecureOpaqueRefreshTokenGenerator refreshTokenGenerator;
    private final HmacSha256RefreshTokenHasher refreshTokenHasher;
    private final SessionProjectionOutboxAppender projectionAppender;
    private final RefreshRotationPolicy policy;
    private final Clock clock;
    private final SessionIssuanceProperties properties;

    public TransactionalJdbcRefreshTokenRotator(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            Clock clock,
            SessionIssuanceProperties properties) {
        this(
                mapper,
                transactionManager,
                accessTokenSigner,
                idGenerator,
                refreshTokenGenerator,
                refreshTokenHasher,
                projectionAppender,
                new RefreshRotationPolicy(),
                clock,
                properties);
    }

    TransactionalJdbcRefreshTokenRotator(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            RefreshRotationPolicy policy,
            Clock clock,
            SessionIssuanceProperties properties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.accessTokenSigner = Objects.requireNonNull(
                accessTokenSigner, "accessTokenSigner must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.refreshTokenGenerator = Objects.requireNonNull(
                refreshTokenGenerator, "refreshTokenGenerator must not be null");
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher, "refreshTokenHasher must not be null");
        this.projectionAppender = Objects.requireNonNull(
                projectionAppender, "projectionAppender must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        properties.validateEnabled();
        if (!properties.isEnabled()) {
            throw new IllegalStateException("session issuance properties are not enabled");
        }
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
    }

    @Override
    public RefreshRotationResult rotate(RefreshRotationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        SensitiveRefreshToken[] generated = new SensitiveRefreshToken[1];
        try {
            RefreshRotationResult result = transaction.execute(
                    status -> rotateInsideTransaction(command, generated));
            if (result == null) {
                throw new RefreshRotationException(
                        "refresh rotation transaction returned no result");
            }
            return result;
        } catch (RuntimeException exception) {
            if (generated[0] != null) {
                generated[0].destroy();
            }
            if (exception instanceof SessionIssuanceException issuanceException) {
                throw issuanceException;
            }
            throw new RefreshRotationException("refresh rotation failed", exception);
        } finally {
            command.destroy();
        }
    }

    private RefreshRotationResult rotateInsideTransaction(
            RefreshRotationCommand command,
            SensitiveRefreshToken[] generated) {
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());

        byte[] presentedHash = hashPresentedToken(command);
        final StoredRefreshTokenRow token;
        try {
            token = findTokenByHash(presentedHash);
        } finally {
            Arrays.fill(presentedHash, (byte) 0);
        }
        if (token == null) {
            return RefreshRotationResult.rejected();
        }

        lockLoginDelivery(token);
        long tokenVersion = lockTokenVersion(token);
        StoredSessionRow session = findSessionForUpdate(token);

        RefreshRotationPolicy.Decision decision = session == null
                ? RefreshRotationPolicy.Decision.REUSE_DETECTED
                : policy.decide(
                        new RefreshRotationPolicy.RefreshTokenState(
                                tokenStatus(token.status()), token.expireAt()),
                        new RefreshRotationPolicy.SessionState(
                                sessionStatus(session.status()),
                                session.idleExpireAt(),
                                session.absoluteExpireAt()),
                        now);

        return switch (decision) {
            case ROTATE -> rotateActiveToken(token, session, tokenVersion, now, generated);
            case REUSE_DETECTED -> {
                revokeFamily(token, session, tokenVersion, now);
                yield RefreshRotationResult.rejected();
            }
            case REJECT -> RefreshRotationResult.rejected();
        };
    }

    private RefreshRotationResult rotateActiveToken(
            StoredRefreshTokenRow token,
            StoredSessionRow session,
            long tokenVersion,
            Instant now,
            SensitiveRefreshToken[] generated) {
        SensitiveRefreshToken successor = refreshTokenGenerator.generate();
        generated[0] = successor;
        byte[] successorHash = refreshTokenHasher.hash(successor);
        try {
            long successorId = idGenerator.nextId();
            int rotated = mapper.rotateRefreshToken(
                    successorId, now, token.id(), token.tenantId());
            if (rotated != 1) {
                generated[0] = null;
                successor.destroy();
                revokeFamily(token, session, tokenVersion, now);
                return RefreshRotationResult.rejected();
            }

            Instant successorExpiry = earliest(
                    now.plus(properties.getRefreshTokenTtl()), session.absoluteExpireAt());
            insertSuccessorToken(
                    token, successorId, successorHash, now, successorExpiry);

            Instant newIdleExpiry = earliest(
                    now.plus(properties.getIdleTtl()), session.absoluteExpireAt());
            int slid = mapper.slideSession(
                    token.tenantId(), token.sessionId(), now, newIdleExpiry);
            requireOneRow(slid, "session idle slide");
            // A valid refresh cookie proves the original login response reached this client.
            mapper.confirmLoginDelivery(token.tenantId(), token.sessionId(), now);

            long successorSessionVersion = session.sessionVersion() + 1;
            projectionAppender.append(
                    idGenerator.nextId(),
                    new SessionSecurityProjection(
                            token.tenantId(),
                            token.userId(),
                            token.sessionId(),
                            tokenVersion,
                            successorSessionVersion,
                            SessionProjectionStatus.ACTIVE,
                            newIdleExpiry,
                            session.absoluteExpireAt()),
                    now);

            SignedAccessToken accessToken = accessTokenSigner.sign(
                    new AccessTokenSigningRequest(
                            token.tenantId(),
                            token.userId(),
                            token.sessionId(),
                            tokenVersion,
                            successorSessionVersion));
            validateSignedAccessToken(accessToken, now, session.absoluteExpireAt());

            generated[0] = null;
            return RefreshRotationResult.rotated(new IssuedLoginSession(
                    accessToken.compact(),
                    accessToken.expiresInSeconds(),
                    successor,
                    Duration.between(now, successorExpiry).toSeconds(),
                    token.sessionId(),
                    token.userId(),
                    token.tenantId()));
        } finally {
            Arrays.fill(successorHash, (byte) 0);
        }
    }

    private void revokeFamily(
            StoredRefreshTokenRow token,
            StoredSessionRow session,
            long tokenVersion,
            Instant now) {
        mapper.markPresentedTokenReused(token.id(), token.tenantId(), now);
        mapper.revokeRefreshTokenFamily(token.tenantId(), token.tokenFamilyId(), now);
        if (session != null) {
            int revoked = mapper.revokeSessionForTokenReuse(
                    token.tenantId(), token.sessionId(), now);
            if (revoked == 1) {
                projectionAppender.append(
                        idGenerator.nextId(),
                        new SessionSecurityProjection(
                                token.tenantId(),
                                token.userId(),
                                token.sessionId(),
                                tokenVersion,
                                session.sessionVersion() + 1,
                                SessionProjectionStatus.REVOKED,
                                session.idleExpireAt(),
                                session.absoluteExpireAt()),
                        now);
            }
        }
    }

    private byte[] hashPresentedToken(RefreshRotationCommand command) {
        char[] presented = command.presentedTokenCopy();
        try (SensitiveRefreshToken token = new SensitiveRefreshToken(presented)) {
            return refreshTokenHasher.hash(token);
        } finally {
            Arrays.fill(presented, '\0');
        }
    }

    private StoredRefreshTokenRow findTokenByHash(byte[] tokenHash) {
        List<StoredRefreshTokenRow> matches = mapper.findRefreshTokenByHash(tokenHash);
        if (matches.size() > 1) {
            throw new RefreshRotationException(
                    "refresh token hash is not unique which must be impossible");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private long lockTokenVersion(StoredRefreshTokenRow token) {
        List<Long> versions = mapper.lockTokenVersion(token.tenantId(), token.userId());
        if (versions.size() != 1 || versions.get(0) == null || versions.get(0) <= 0) {
            throw new RefreshRotationException(
                    "authoritative user security state is missing or invalid");
        }
        return versions.get(0);
    }

    private void lockLoginDelivery(StoredRefreshTokenRow token) {
        List<StoredLoginDeliveryRow> deliveries = mapper.findLoginDeliveryForUpdate(
                token.tenantId(), token.sessionId());
        if (deliveries.size() > 1) {
            throw new RefreshRotationException("login delivery is not unique");
        }
        if (!deliveries.isEmpty() && deliveries.get(0).userId() != token.userId()) {
            throw new RefreshRotationException(
                    "refresh token points at a login delivery owned by another user");
        }
    }

    private StoredSessionRow findSessionForUpdate(StoredRefreshTokenRow token) {
        List<StoredSessionRow> sessions = mapper.findSessionForUpdate(
                token.tenantId(), token.sessionId());
        if (sessions.size() > 1) {
            throw new RefreshRotationException(
                    "login session is not unique which must be impossible");
        }
        if (!sessions.isEmpty() && sessions.get(0).userId() != token.userId()) {
            throw new RefreshRotationException(
                    "refresh token points at a session owned by another user");
        }
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    private void insertSuccessorToken(
            StoredRefreshTokenRow token,
            long successorId,
            byte[] successorHash,
            Instant now,
            Instant expiry) {
        int updated = mapper.insertSuccessorRefreshToken(
                successorId,
                token.tenantId(),
                token.userId(),
                token.sessionId(),
                token.tokenFamilyId(),
                successorHash,
                token.id(),
                now,
                expiry);
        requireOneRow(updated, "successor refresh token insert");
    }

    private static RefreshRotationPolicy.RefreshTokenStatus tokenStatus(String status) {
        try {
            return RefreshRotationPolicy.RefreshTokenStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new RefreshRotationException("refresh token status is not recognized");
        }
    }

    private static RefreshRotationPolicy.SessionStatus sessionStatus(String status) {
        try {
            return RefreshRotationPolicy.SessionStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new RefreshRotationException("login session status is not recognized");
        }
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static void validateSignedAccessToken(
            SignedAccessToken token,
            Instant transactionTime,
            Instant sessionAbsoluteExpiry) {
        Objects.requireNonNull(token, "accessTokenSigner returned null");
        Duration allowedSkew = Duration.ofSeconds(30);
        if (token.issuedAt().isBefore(transactionTime.minus(allowedSkew))
                || token.issuedAt().isAfter(transactionTime.plus(allowedSkew))
                || token.expiresAt().isAfter(sessionAbsoluteExpiry)) {
            throw new RefreshRotationException(
                    "signed access-token time is inconsistent with the rotation transaction");
        }
    }

    private static void requireOneRow(int updated, String operation) {
        if (updated != 1) {
            throw new RefreshRotationException(
                    operation + " did not affect exactly one row");
        }
    }

}
