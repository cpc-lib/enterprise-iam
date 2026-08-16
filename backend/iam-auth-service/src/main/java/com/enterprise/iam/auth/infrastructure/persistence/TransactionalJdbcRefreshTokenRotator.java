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

    static final String LOCK_SECURITY_STATE_SQL = """
            SELECT token_version
            FROM iam_user_security_state
            WHERE tenant_id = ? AND user_id = ?
            FOR UPDATE
            """;

    static final String SELECT_TOKEN_BY_HASH_SQL = """
            SELECT id, tenant_id, user_id, session_id, token_family_id, status, expire_at
            FROM iam_refresh_token
            WHERE token_hash = ?
            """;

    static final String SELECT_SESSION_FOR_UPDATE_SQL = """
            SELECT user_id, status, idle_expire_at, absolute_expire_at, session_version
            FROM iam_login_session
            WHERE tenant_id = ? AND session_id = ?
            FOR UPDATE
            """;

    static final String CAS_ROTATE_TOKEN_SQL = """
            UPDATE iam_refresh_token
            SET status = 'ROTATED', replaced_by_token_id = ?, rotated_at = ?,
                version = version + 1
            WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
            """;

    static final String INSERT_SUCCESSOR_TOKEN_SQL = """
            INSERT INTO iam_refresh_token (
                id, tenant_id, user_id, session_id, token_family_id,
                token_hash, parent_token_id, status, issued_at, expire_at, version,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, ?)
            """;

    static final String SLIDE_SESSION_SQL = """
            UPDATE iam_login_session
            SET last_access_at = ?, idle_expire_at = ?, session_version = session_version + 1
            WHERE tenant_id = ? AND session_id = ? AND status = 'ACTIVE'
            """;

    static final String MARK_PRESENTED_TOKEN_REUSED_SQL = """
            UPDATE iam_refresh_token
            SET status = 'REUSED', revoked_at = ?, revoke_reason = 'REUSE_DETECTED'
            WHERE id = ? AND tenant_id = ? AND status IN ('ACTIVE', 'ROTATED', 'REUSED')
            """;

    static final String REVOKE_FAMILY_SQL = """
            UPDATE iam_refresh_token
            SET status = 'REVOKED', revoked_at = ?, revoke_reason = 'REUSE_DETECTED',
                version = version + 1
            WHERE tenant_id = ? AND token_family_id = ? AND status IN ('ACTIVE', 'ROTATED')
            """;

    static final String REVOKE_SESSION_SQL = """
            UPDATE iam_login_session
            SET status = 'REVOKED', revoked_at = ?, revoke_reason = 'REFRESH_TOKEN_REUSE',
                session_version = session_version + 1
            WHERE tenant_id = ? AND session_id = ? AND status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;
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
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            Clock clock,
            SessionIssuanceProperties properties) {
        this(
                jdbcTemplate,
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
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            RefreshRotationPolicy policy,
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
            throw exception;
        } finally {
            command.destroy();
        }
    }

    private RefreshRotationResult rotateInsideTransaction(
            RefreshRotationCommand command,
            SensitiveRefreshToken[] generated) {
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());

        byte[] presentedHash = hashPresentedToken(command);
        final StoredRefreshToken token;
        try {
            token = findTokenByHash(presentedHash);
        } finally {
            Arrays.fill(presentedHash, (byte) 0);
        }
        if (token == null) {
            return RefreshRotationResult.rejected();
        }

        long tokenVersion = lockTokenVersion(token);
        StoredSession session = findSessionForUpdate(token);

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
            StoredRefreshToken token,
            StoredSession session,
            long tokenVersion,
            Instant now,
            SensitiveRefreshToken[] generated) {
        SensitiveRefreshToken successor = refreshTokenGenerator.generate();
        generated[0] = successor;
        byte[] successorHash = refreshTokenHasher.hash(successor);
        try {
            long successorId = idGenerator.nextId();
            int rotated = jdbcTemplate.update(
                    CAS_ROTATE_TOKEN_SQL,
                    successorId,
                    Timestamp.from(now),
                    token.id(),
                    token.tenantId());
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
            int slid = jdbcTemplate.update(
                    SLIDE_SESSION_SQL,
                    Timestamp.from(now),
                    Timestamp.from(newIdleExpiry),
                    token.tenantId(),
                    token.sessionId());
            requireOneRow(slid, "session idle slide");

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
            StoredRefreshToken token,
            StoredSession session,
            long tokenVersion,
            Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update(
                MARK_PRESENTED_TOKEN_REUSED_SQL, timestamp, token.id(), token.tenantId());
        jdbcTemplate.update(
                REVOKE_FAMILY_SQL, timestamp, token.tenantId(), token.tokenFamilyId());
        if (session != null) {
            int revoked = jdbcTemplate.update(
                    REVOKE_SESSION_SQL, timestamp, token.tenantId(), token.sessionId());
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
        return refreshTokenHasher.hash(
                new SensitiveRefreshToken(command.presentedTokenCopy()));
    }

    private StoredRefreshToken findTokenByHash(byte[] tokenHash) {
        List<StoredRefreshToken> matches = jdbcTemplate.query(
                SELECT_TOKEN_BY_HASH_SQL,
                (resultSet, rowNumber) -> new StoredRefreshToken(
                        resultSet.getLong("id"),
                        resultSet.getLong("tenant_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("token_family_id"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("expire_at").toInstant()),
                tokenHash);
        if (matches.size() > 1) {
            throw new RefreshRotationException(
                    "refresh token hash is not unique which must be impossible");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private long lockTokenVersion(StoredRefreshToken token) {
        List<Long> versions = jdbcTemplate.query(
                LOCK_SECURITY_STATE_SQL,
                (resultSet, rowNumber) -> resultSet.getLong("token_version"),
                token.tenantId(),
                token.userId());
        if (versions.size() != 1 || versions.get(0) == null || versions.get(0) <= 0) {
            throw new RefreshRotationException(
                    "authoritative user security state is missing or invalid");
        }
        return versions.get(0);
    }

    private StoredSession findSessionForUpdate(StoredRefreshToken token) {
        List<StoredSession> sessions = jdbcTemplate.query(
                SELECT_SESSION_FOR_UPDATE_SQL,
                (resultSet, rowNumber) -> new StoredSession(
                        resultSet.getLong("user_id"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("idle_expire_at").toInstant(),
                        resultSet.getTimestamp("absolute_expire_at").toInstant(),
                        resultSet.getLong("session_version")),
                token.tenantId(),
                token.sessionId());
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
            StoredRefreshToken token,
            long successorId,
            byte[] successorHash,
            Instant now,
            Instant expiry) {
        Timestamp timestamp = Timestamp.from(now);
        int updated = jdbcTemplate.update(
                INSERT_SUCCESSOR_TOKEN_SQL,
                successorId,
                token.tenantId(),
                token.userId(),
                token.sessionId(),
                token.tokenFamilyId(),
                successorHash,
                token.id(),
                timestamp,
                Timestamp.from(expiry),
                timestamp,
                timestamp);
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

    private record StoredRefreshToken(
            long id,
            long tenantId,
            long userId,
            long sessionId,
            long tokenFamilyId,
            String status,
            Instant expireAt) {
    }

    private record StoredSession(
            long userId,
            String status,
            Instant idleExpireAt,
            Instant absoluteExpireAt,
            long sessionVersion) {
    }
}
