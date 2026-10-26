package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
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
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Owns the commit-before-return transaction for a new login credential pair. */
public final class TransactionalJdbcLoginSessionIssuer implements LoginSessionIssuer {

    private final AuthPersistenceMapper mapper;
    private final TransactionTemplate transaction;
    private final AccessTokenSigner accessTokenSigner;
    private final PositiveIdGenerator idGenerator;
    private final SecureOpaqueRefreshTokenGenerator refreshTokenGenerator;
    private final HmacSha256RefreshTokenHasher refreshTokenHasher;
    private final SessionProjectionOutboxAppender projectionAppender;
    private final LoginAuditOutboxAppender auditAppender;
    private final Clock clock;
    private final SessionIssuanceProperties properties;

    public TransactionalJdbcLoginSessionIssuer(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
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
        this.auditAppender = Objects.requireNonNull(
                auditAppender, "auditAppender must not be null");
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
    public IssuedLoginSession issue(ResolvedLoginIdentity identity, String requestId) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (!identity.active()) {
            throw new IllegalArgumentException("inactive identity cannot receive a session");
        }
        requireRequestId(requestId);
        SensitiveRefreshToken[] tokenToDestroyOnFailure = new SensitiveRefreshToken[1];
        try {
            IssuedLoginSession result = transaction.execute(status ->
                    issueInsideTransaction(identity, requestId, tokenToDestroyOnFailure));
            if (result == null) {
                throw new SessionIssuanceException("session transaction returned no result");
            }
            return result;
        } catch (RuntimeException exception) {
            if (tokenToDestroyOnFailure[0] != null) {
                tokenToDestroyOnFailure[0].destroy();
            }
            if (exception instanceof SessionIssuanceException issuanceException) {
                throw issuanceException;
            }
            throw new SessionIssuanceException("login session issuance failed", exception);
        }
    }

    private IssuedLoginSession issueInsideTransaction(
            ResolvedLoginIdentity identity,
            String requestId,
            SensitiveRefreshToken[] tokenHolder) {
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        long tokenVersion = lockTokenVersion(identity);
        enforceConcurrentSessionLimit(identity, now);

        long sessionRowId = idGenerator.nextId();
        long sessionId = idGenerator.nextId();
        long refreshTokenId = idGenerator.nextId();
        long tokenFamilyId = idGenerator.nextId();
        long projectionEventId = idGenerator.nextId();
        long auditEventId = idGenerator.nextId();
        Instant idleExpiry = now.plus(properties.getIdleTtl());
        Instant absoluteExpiry = now.plus(properties.getAbsoluteTtl());
        Instant refreshExpiry = now.plus(properties.getRefreshTokenTtl());

        SensitiveRefreshToken refreshToken = refreshTokenGenerator.generate();
        tokenHolder[0] = refreshToken;
        byte[] refreshHash = refreshTokenHasher.hash(refreshToken);
        try {
            insertSession(
                    identity, sessionRowId, sessionId, now, idleExpiry, absoluteExpiry);
            insertRefreshToken(
                    identity, refreshTokenId, sessionId, tokenFamilyId,
                    refreshHash, now, refreshExpiry);
            insertLoginDelivery(identity, sessionId, requestId, now);
        } finally {
            Arrays.fill(refreshHash, (byte) 0);
        }

        SessionSecurityProjection projection = new SessionSecurityProjection(
                identity.tenantId(),
                identity.userId(),
                sessionId,
                tokenVersion,
                1,
                SessionProjectionStatus.ACTIVE,
                idleExpiry,
                absoluteExpiry);
        projectionAppender.append(projectionEventId, projection, now);
        auditAppender.appendLoginSucceeded(
                auditEventId,
                identity.tenantId(),
                identity.userId(),
                sessionId,
                requestId,
                now);

        SignedAccessToken accessToken = accessTokenSigner.sign(
                new AccessTokenSigningRequest(
                        identity.tenantId(), identity.userId(), sessionId,
                        tokenVersion, 1));
        validateSignedAccessToken(accessToken, now, absoluteExpiry);
        return new IssuedLoginSession(
                accessToken.compact(),
                accessToken.expiresInSeconds(),
                refreshToken,
                properties.getRefreshTokenTtl().toSeconds(),
                sessionId,
                identity.userId(),
                identity.tenantId());
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
            throw new SessionIssuanceException(
                    "signed access-token time is inconsistent with the session transaction");
        }
    }

    private long lockTokenVersion(ResolvedLoginIdentity identity) {
        List<Long> versions = mapper.lockTokenVersion(identity.tenantId(), identity.userId());
        if (versions.size() != 1 || versions.get(0) == null || versions.get(0) <= 0) {
            throw new SessionIssuanceException(
                    "authoritative user security state is missing or invalid");
        }
        return versions.get(0);
    }

    private void enforceConcurrentSessionLimit(
            ResolvedLoginIdentity identity,
            Instant now) {
        Long active = mapper.countActiveSessions(identity.tenantId(), identity.userId(), now);
        if (active == null) {
            throw new SessionIssuanceException("active session count is unavailable");
        }
        if (active >= properties.getMaximumConcurrentSessions()) {
            throw new SessionLimitExceededException();
        }
    }

    private void insertSession(
            ResolvedLoginIdentity identity,
            long rowId,
            long sessionId,
            Instant now,
            Instant idleExpiry,
            Instant absoluteExpiry) {
        int updated = mapper.insertLoginSession(
                rowId,
                identity.tenantId(),
                sessionId,
                identity.userId(),
                now,
                idleExpiry,
                absoluteExpiry);
        requireOneRow(updated, "login session insert");
    }

    private void insertRefreshToken(
            ResolvedLoginIdentity identity,
            long tokenId,
            long sessionId,
            long familyId,
            byte[] tokenHash,
            Instant now,
            Instant expiry) {
        int updated = mapper.insertRefreshToken(
                tokenId,
                identity.tenantId(),
                identity.userId(),
                sessionId,
                familyId,
                tokenHash,
                now,
                expiry);
        requireOneRow(updated, "refresh token insert");
    }

    private void insertLoginDelivery(
            ResolvedLoginIdentity identity,
            long sessionId,
            String requestId,
            Instant now) {
        int updated = mapper.insertLoginDelivery(
                identity.tenantId(),
                identity.userId(),
                sessionId,
                requestId,
                now.plus(properties.getLoginDeliveryConfirmationTtl()),
                now);
        requireOneRow(updated, "login delivery insert");
    }

    private static void requireOneRow(int updated, String operation) {
        if (updated != 1) {
            throw new SessionIssuanceException(operation + " did not affect exactly one row");
        }
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException(
                    "requestId must be non-blank and at most 128 characters");
        }
    }
}
