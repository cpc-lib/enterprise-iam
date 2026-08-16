package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.command.RefreshRotationCommand;
import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.RefreshRotationResult;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.service.RefreshRotationPolicy;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.outbox.JdbcSessionProjectionOutboxAppender;
import com.enterprise.iam.auth.infrastructure.outbox.SessionProjectionEventCodec;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import com.enterprise.iam.auth.infrastructure.security.SecureOpaqueRefreshTokenGenerator;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.SignedAccessToken;
import com.enterprise.iam.outbox.JdbcOutboxWriter;
import com.enterprise.iam.testsupport.database.MySqlIntegrationDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalJdbcRefreshTokenRotatorIT {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final RefreshTokenHashKey KEY = new RefreshTokenHashKey(
            "test-key", new SecretKeySpec(new byte[32], "HmacSHA256"));
    private static final Duration ISSUER_IDLE = Duration.ofHours(1);
    private static final Duration ROTATOR_IDLE = Duration.ofHours(2);
    private static final Duration ABSOLUTE = Duration.ofDays(2);
    private static final Duration REFRESH = Duration.ofHours(6);

    private static MySqlIntegrationDatabase mysql;

    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;

    @BeforeAll
    static void startDatabase() {
        mysql = MySqlIntegrationDatabase.start("auth", "iam_auth");
    }

    @AfterAll
    static void stopDatabase() {
        if (mysql != null) {
            mysql.close();
        }
    }

    @BeforeEach
    void prepareDatabase() {
        DataSource dataSource = new DriverManagerDataSource(
                mysql.jdbcUrl(), mysql.username(), mysql.password());
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
        jdbc.update("DELETE FROM sys_outbox_event");
        jdbc.update("DELETE FROM iam_refresh_token");
        jdbc.update("DELETE FROM iam_login_session");
        jdbc.update("DELETE FROM iam_user_security_state");
        jdbc.update("""
                INSERT INTO iam_user_security_state (
                    id, tenant_id, user_id, token_version, password_version,
                    login_failure_count, version, created_at, updated_at)
                VALUES (1, 10, 20, 4, 1, 0, 0, ?, ?)
                """, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    @Test
    void rotatingActiveTokenIssuesSuccessorSlidesSessionAndPublishesProjection() {
        TransactionalJdbcLoginSessionIssuer issuer = issuer();
        TransactionalJdbcRefreshTokenRotator rotator = rotator(100_000);
        IssuedLoginSession issued = issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-rot-seed-1");
        char[] raw = issued.refreshToken().copyValue();
        RefreshRotationResult result = null;

        try {
            result = rotator.rotate(
                    new RefreshRotationCommand(raw, "request-rot-0001"));

            assertThat(result.rotated()).isTrue();
            assertThat(result.issuedSession().orElseThrow().sessionId())
                    .isEqualTo(issued.sessionId());
            assertThat(result.issuedSession().orElseThrow().refreshExpiresIn())
                    .isEqualTo(REFRESH.toSeconds());

            assertThat(jdbc.queryForObject("""
                    SELECT status FROM iam_refresh_token
                    WHERE token_hash = ?
                    """, String.class, hash(raw))).isEqualTo("ROTATED");
            assertThat(jdbc.queryForObject("""
                    SELECT replaced_by_token_id FROM iam_refresh_token
                    WHERE token_hash = ?
                    """, Long.class, hash(raw))).isNotNull();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM iam_refresh_token
                    WHERE parent_token_id IS NOT NULL AND status = 'ACTIVE'
                    """, Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT idle_expire_at FROM iam_login_session WHERE session_id = ?
                    """, java.sql.Timestamp.class, issued.sessionId()).toInstant())
                    .isEqualTo(NOW.plus(ROTATOR_IDLE));
            assertThat(jdbc.queryForObject("""
                    SELECT session_version FROM iam_login_session WHERE session_id = ?
                    """, Long.class, issued.sessionId())).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sessionVersion'))
                    FROM sys_outbox_event ORDER BY id DESC LIMIT 1
                    """, String.class)).isEqualTo("2");
        } finally {
            Arrays.fill(raw, '\0');
            issued.refreshToken().destroy();
            if (result != null) {
                result.issuedSession().ifPresent(session -> session.refreshToken().destroy());
            }
        }
    }

    @Test
    void replayingRotatedTokenRevokesWholeFamilyAndSession() {
        TransactionalJdbcLoginSessionIssuer issuer = issuer();
        TransactionalJdbcRefreshTokenRotator rotator = rotator(200_000);
        IssuedLoginSession issued = issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-rot-seed-2");
        char[] raw = issued.refreshToken().copyValue();
        RefreshRotationResult first;
        try {
            first = rotator.rotate(new RefreshRotationCommand(raw, "request-rot-0002"));
        } finally {
            issued.refreshToken().destroy();
        }

        assertThat(first.rotated()).isTrue();

        RefreshRotationResult replay = rotator.rotate(
                new RefreshRotationCommand(raw, "request-rot-0003"));
        try {
            Arrays.fill(raw, '\0');

            assertThat(replay.rotated()).isFalse();
            assertThat(replay.publicCode()).isEqualTo("IAM_AUTHENTICATION_FAILED");

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM iam_refresh_token WHERE status = 'ACTIVE'
                    """, Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM iam_refresh_token
                    WHERE status IN ('REUSED', 'REVOKED')
                      AND revoke_reason = 'REUSE_DETECTED'
                    """, Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM iam_login_session WHERE session_id = ?
                    """, String.class, issued.sessionId())).isEqualTo("REVOKED");
            assertThat(jdbc.queryForObject("""
                    SELECT revoke_reason FROM iam_login_session WHERE session_id = ?
                    """, String.class, issued.sessionId())).isEqualTo("REFRESH_TOKEN_REUSE");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("""
                    SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.status'))
                    FROM sys_outbox_event ORDER BY id DESC LIMIT 1
                    """, String.class)).isEqualTo("REVOKED");

            char[] successor = first.issuedSession().orElseThrow()
                    .refreshToken().copyValue();
            RefreshRotationResult successorReplay = rotator.rotate(
                    new RefreshRotationCommand(successor, "request-rot-0004"));
            try {
                assertThat(successorReplay.rotated()).isFalse();
            } finally {
                Arrays.fill(successor, '\0');
            }
        } finally {
            first.issuedSession().ifPresent(session -> session.refreshToken().destroy());
        }
    }

    @Test
    void unknownTokenIsRejectedWithoutAnySideEffect() {
        TransactionalJdbcLoginSessionIssuer issuer = issuer();
        TransactionalJdbcRefreshTokenRotator rotator = rotator(300_000);
        IssuedLoginSession issued = issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-rot-seed-3");
        issued.refreshToken().destroy();
        String unknown = "rt1.test-key." + "z".repeat(43);

        RefreshRotationResult result = rotator.rotate(
                new RefreshRotationCommand(unknown.toCharArray(), "request-rot-0005"));

        assertThat(result.rotated()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_session", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_refresh_token", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(1);
    }

    @Test
    void expiredActiveTokenIsRejectedWithoutFamilyRevocation() {
        seedSessionAndToken(
                "ACTIVE", NOW.minusSeconds(3_600),
                "ACTIVE", NOW.plusSeconds(1_800), NOW.plusSeconds(86_400));
        TransactionalJdbcRefreshTokenRotator rotator = rotator(400_000);

        RefreshRotationResult result = rotator.rotate(new RefreshRotationCommand(
                "rt1.test-key.seeded-token-value-00000000000000000000".toCharArray(),
                "request-rot-0006"));

        assertThat(result.rotated()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isZero();
    }

    @Test
    void liveTokenOnRevokedSessionRevokesFamily() {
        seedSessionAndToken(
                "ACTIVE", NOW.plusSeconds(3_600),
                "REVOKED", NOW.plusSeconds(1_800), NOW.plusSeconds(86_400));
        TransactionalJdbcRefreshTokenRotator rotator = rotator(500_000);

        RefreshRotationResult result = rotator.rotate(new RefreshRotationCommand(
                "rt1.test-key.seeded-token-value-00000000000000000000".toCharArray(),
                "request-rot-0007"));

        assertThat(result.rotated()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token", String.class)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                "SELECT session_version FROM iam_login_session", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.status'))
                FROM sys_outbox_event
                """, String.class)).isEqualTo("REVOKED");
    }

    @Test
    void concurrentDuplicateRotationRotatesOnceAndRevokesWholeFamily() throws Exception {
        TransactionalJdbcLoginSessionIssuer issuer = issuer();
        TransactionalJdbcRefreshTokenRotator rotator = rotator(600_000);
        IssuedLoginSession issued = issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-rot-seed-4");
        char[] raw = issued.refreshToken().copyValue();
        issued.refreshToken().destroy();

        CountDownLatch bothStarted = new CountDownLatch(2);
        Callable<RefreshRotationResult> attempt = () -> {
            bothStarted.countDown();
            bothStarted.await(5, TimeUnit.SECONDS);
            return rotator.rotate(new RefreshRotationCommand(
                    Arrays.copyOf(raw, raw.length), "request-rot-concurrent"));
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<RefreshRotationResult>> futures;
        try {
            futures = List.of(
                    executor.submit(attempt),
                    executor.submit(attempt));
            long rotatedCount = 0;
            for (Future<RefreshRotationResult> future : futures) {
                RefreshRotationResult result = future.get(10, TimeUnit.SECONDS);
                if (result.rotated()) {
                    rotatedCount++;
                } else {
                    assertThat(result.publicCode()).isEqualTo("IAM_AUTHENTICATION_FAILED");
                }
            }
            assertThat(rotatedCount).isEqualTo(1);
        } finally {
            Arrays.fill(raw, '\0');
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM iam_refresh_token WHERE status = 'ACTIVE'
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session", String.class)).isEqualTo("REVOKED");
    }

    private void seedSessionAndToken(
            String tokenStatus, Instant tokenExpireAt,
            String sessionStatus, Instant idleExpireAt, Instant absoluteExpireAt) {
        jdbc.update("""
                INSERT INTO iam_login_session (
                    id, tenant_id, session_id, user_id, status,
                    last_access_at, idle_expire_at, absolute_expire_at,
                    last_strong_auth_at, session_version, created_at, updated_at)
                VALUES (5000, 10, 5001, 20, ?, ?, ?, ?, ?, 1, ?, ?)
                """,
                sessionStatus,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(idleExpireAt),
                java.sql.Timestamp.from(absoluteExpireAt),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));
        byte[] hash = new HmacSha256RefreshTokenHasher(KEY).hash(new SensitiveRefreshToken(
                "rt1.test-key.seeded-token-value-00000000000000000000".toCharArray()));
        jdbc.update("""
                INSERT INTO iam_refresh_token (
                    id, tenant_id, user_id, session_id, token_family_id,
                    token_hash, status, issued_at, expire_at, version,
                    created_at, updated_at)
                VALUES (6000, 10, 20, 5001, 6001, ?, ?, ?, ?, 0, ?, ?)
                """,
                hash,
                tokenStatus,
                java.sql.Timestamp.from(NOW.minusSeconds(60)),
                java.sql.Timestamp.from(tokenExpireAt),
                java.sql.Timestamp.from(NOW.minusSeconds(60)),
                java.sql.Timestamp.from(NOW.minusSeconds(60)));
    }

    private byte[] hash(char[] rawToken) {
        return new HmacSha256RefreshTokenHasher(KEY).hash(new SensitiveRefreshToken(rawToken));
    }

    private TransactionalJdbcLoginSessionIssuer issuer() {
        return new TransactionalJdbcLoginSessionIssuer(
                jdbc,
                transactionManager,
                successfulSigner(),
                sequentialIds(1_000),
                new SecureOpaqueRefreshTokenGenerator(distinctRandom(), KEY),
                new HmacSha256RefreshTokenHasher(KEY),
                appender(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties(ISSUER_IDLE));
    }

    private TransactionalJdbcRefreshTokenRotator rotator(long initialId) {
        return new TransactionalJdbcRefreshTokenRotator(
                jdbc,
                transactionManager,
                successfulSigner(),
                sequentialIds(initialId),
                new SecureOpaqueRefreshTokenGenerator(distinctRandom(), KEY),
                new HmacSha256RefreshTokenHasher(KEY),
                appender(),
                new RefreshRotationPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties(ROTATOR_IDLE));
    }

    private JdbcSessionProjectionOutboxAppender appender() {
        return new JdbcSessionProjectionOutboxAppender(
                new JdbcOutboxWriter(jdbc), new SessionProjectionEventCodec(new ObjectMapper()));
    }

    private static PositiveIdGenerator sequentialIds(long initialId) {
        AtomicLong ids = new AtomicLong(initialId);
        return ids::getAndIncrement;
    }

    /** Distinct bytes per generation so successor hashes never collide. */
    private static SecureRandom distinctRandom() {
        AtomicInteger calls = new AtomicInteger();
        return new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) (0x40 + calls.getAndIncrement() % 0x30));
            }
        };
    }

    private static AccessTokenSigner successfulSigner() {
        return request -> new SignedAccessToken(
                "header.payload.signature", NOW, NOW.plusSeconds(300));
    }

    private static SessionIssuanceProperties properties(Duration idleTtl) {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(1);
        properties.setMaximumConcurrentSessions(10);
        properties.setIdleTtl(idleTtl);
        properties.setAbsoluteTtl(ABSOLUTE);
        properties.setRefreshTokenTtl(REFRESH);
        return properties;
    }
}
