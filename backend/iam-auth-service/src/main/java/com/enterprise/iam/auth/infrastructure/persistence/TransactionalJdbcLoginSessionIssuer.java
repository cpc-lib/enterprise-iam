package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.SecureOpaqueRefreshTokenGenerator;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.AccessTokenSigningRequest;
import com.enterprise.iam.common.security.access.SignedAccessToken;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Owns the commit-before-return transaction for a new login credential pair. */
public final class TransactionalJdbcLoginSessionIssuer implements LoginSessionIssuer {

    static final String LOCK_SECURITY_STATE_SQL = """
            SELECT token_version
            FROM iam_user_security_state
            WHERE tenant_id = ? AND user_id = ?
            FOR UPDATE
            """;

    static final String COUNT_ACTIVE_SESSIONS_SQL = """
            SELECT COUNT(*)
            FROM iam_login_session
            WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE'
              AND idle_expire_at > ? AND absolute_expire_at > ?
            """;

    static final String INSERT_SESSION_SQL = """
            INSERT INTO iam_login_session (
                id, tenant_id, session_id, user_id, status,
                last_access_at, idle_expire_at, absolute_expire_at,
                last_strong_auth_at, session_version, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, 1, ?, ?)
            """;

    static final String INSERT_REFRESH_TOKEN_SQL = """
            INSERT INTO iam_refresh_token (
                id, tenant_id, user_id, session_id, token_family_id,
                token_hash, status, issued_at, expire_at, version,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;
    private final AccessTokenSigner accessTokenSigner;
    private final PositiveIdGenerator idGenerator;
    private final SecureOpaqueRefreshTokenGenerator refreshTokenGenerator;
    private final HmacSha256RefreshTokenHasher refreshTokenHasher;
    private final SessionProjectionOutboxAppender projectionAppender;
    private final Clock clock;
    private final SessionIssuanceProperties properties;

    public TransactionalJdbcLoginSessionIssuer(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            Clock clock,
            SessionIssuanceProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
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
                    issueInsideTransaction(identity, tokenToDestroyOnFailure));
            if (result == null) {
                throw new SessionIssuanceException("session transaction returned no result");
            }
            return result;
        } catch (RuntimeException exception) {
            if (tokenToDestroyOnFailure[0] != null) {
                tokenToDestroyOnFailure[0].destroy();
            }
            throw exception;
        }
    }

    private IssuedLoginSession issueInsideTransaction(
            ResolvedLoginIdentity identity,
            SensitiveRefreshToken[] tokenHolder) {
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        long tokenVersion = lockTokenVersion(identity);
        enforceConcurrentSessionLimit(identity, now);

        long sessionRowId = idGenerator.nextId();
        long sessionId = idGenerator.nextId();
        long refreshTokenId = idGenerator.nextId();
        long tokenFamilyId = idGenerator.nextId();
        long projectionEventId = idGenerator.nextId();
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
        List<Long> versions = jdbcTemplate.query(
                LOCK_SECURITY_STATE_SQL,
                (resultSet, rowNumber) -> resultSet.getLong("token_version"),
                identity.tenantId(),
                identity.userId());
        if (versions.size() != 1 || versions.get(0) == null || versions.get(0) <= 0) {
            throw new SessionIssuanceException(
                    "authoritative user security state is missing or invalid");
        }
        return versions.get(0);
    }

    private void enforceConcurrentSessionLimit(
            ResolvedLoginIdentity identity,
            Instant now) {
        Long active = jdbcTemplate.queryForObject(
                COUNT_ACTIVE_SESSIONS_SQL,
                Long.class,
                identity.tenantId(),
                identity.userId(),
                Timestamp.from(now),
                Timestamp.from(now));
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
        Timestamp timestamp = Timestamp.from(now);
        int updated = jdbcTemplate.update(
                INSERT_SESSION_SQL,
                rowId,
                identity.tenantId(),
                sessionId,
                identity.userId(),
                timestamp,
                Timestamp.from(idleExpiry),
                Timestamp.from(absoluteExpiry),
                timestamp,
                timestamp,
                timestamp);
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
        Timestamp timestamp = Timestamp.from(now);
        int updated = jdbcTemplate.update(
                INSERT_REFRESH_TOKEN_SQL,
                tokenId,
                identity.tenantId(),
                identity.userId(),
                sessionId,
                familyId,
                tokenHash,
                timestamp,
                Timestamp.from(expiry),
                timestamp,
                timestamp);
        requireOneRow(updated, "refresh token insert");
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
