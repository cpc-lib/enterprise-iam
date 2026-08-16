package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionalJdbcLoginSessionIssuerIT {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

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
    void commitsSessionRefreshHashAndProjectionEventBeforeReturningTokens() {
        TransactionalJdbcLoginSessionIssuer issuer = issuer(successfulSigner(), 100);

        IssuedLoginSession issued = issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-0001");

        assertThat(issued.accessToken()).isEqualTo("header.payload.signature");
        assertThat(issued.expiresIn()).isEqualTo(300);
        assertThat(issued.refreshExpiresIn()).isEqualTo(1_209_600);
        assertThat(issued.sessionId()).isEqualTo(101);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_session", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_refresh_token", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT token_version FROM iam_user_security_state "
                        + "WHERE tenant_id=10 AND user_id=20", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT event_status FROM sys_outbox_event", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sessionId')) "
                        + "FROM sys_outbox_event", String.class)).isEqualTo("101");

        char[] raw = issued.refreshToken().copyValue();
        try {
            byte[] storedHash = jdbc.queryForObject(
                    "SELECT token_hash FROM iam_refresh_token", byte[].class);
            assertThat(storedHash).hasSize(32);
            assertThat(new String(raw)).startsWith("rt1.test-key.");
            assertThat(issued.toString()).doesNotContain(new String(raw));
        } finally {
            Arrays.fill(raw, '\0');
            issued.refreshToken().destroy();
        }
    }

    @Test
    void signerFailureRollsBackEveryDurableRow() {
        AccessTokenSigner failing = request -> {
            throw new IllegalStateException("KMS unavailable");
        };
        TransactionalJdbcLoginSessionIssuer issuer = issuer(failing, 200);

        assertThatThrownBy(() -> issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-0002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KMS unavailable");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_session", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_refresh_token", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isZero();
    }

    @Test
    void missingSecurityStateFailsClosedBeforeAnyCredentialIsCreated() {
        jdbc.update("DELETE FROM iam_user_security_state");
        TransactionalJdbcLoginSessionIssuer issuer = issuer(successfulSigner(), 300);

        assertThatThrownBy(() -> issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-0003"))
                .isInstanceOf(SessionIssuanceException.class)
                .hasMessageContaining("security state");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_session", Long.class)).isZero();
    }

    @Test
    void concurrentSessionLimitRejectsBeforeTokenOrOutboxCreation() {
        for (int index = 0; index < 10; index++) {
            long id = 1_000 + index;
            jdbc.update("""
                    INSERT INTO iam_login_session (
                        id, tenant_id, session_id, user_id, status,
                        last_access_at, idle_expire_at, absolute_expire_at,
                        last_strong_auth_at, session_version, created_at, updated_at)
                    VALUES (?, 10, ?, 20, 'ACTIVE', ?, ?, ?, ?, 1, ?, ?)
                    """,
                    id,
                    id,
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW.plusSeconds(3_600)),
                    java.sql.Timestamp.from(NOW.plusSeconds(7_200)),
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW));
        }
        TransactionalJdbcLoginSessionIssuer issuer = issuer(successfulSigner(), 400);

        assertThatThrownBy(() -> issuer.issue(
                new ResolvedLoginIdentity(10, 20, true), "request-0004"))
                .isInstanceOf(SessionLimitExceededException.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_session", Long.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_refresh_token", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isZero();
    }

    @Test
    void simultaneousIssuanceSerializesLimitAndCommitsExactlyOneSession() throws Exception {
        SessionIssuanceProperties singleSession = properties(1);
        CountDownLatch firstSignerEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSigner = new CountDownLatch(1);
        AccessTokenSigner blockingSigner = request -> {
            firstSignerEntered.countDown();
            try {
                if (!releaseFirstSigner.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release signer");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("signer interrupted", exception);
            }
            return new SignedAccessToken(
                    "header.payload.signature", NOW, NOW.plusSeconds(300));
        };
        TransactionalJdbcLoginSessionIssuer firstIssuer = issuer(
                blockingSigner, 10_000, singleSession);
        TransactionalJdbcLoginSessionIssuer competingIssuer = issuer(
                successfulSigner(), 20_000, singleSession);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<IssuedLoginSession> first = executor.submit(() -> firstIssuer.issue(
                    new ResolvedLoginIdentity(10, 20, true), "request-concurrent-1"));
            assertThat(firstSignerEntered.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch competingCallStarted = new CountDownLatch(1);
            Future<IssuedLoginSession> competing = executor.submit(() -> {
                competingCallStarted.countDown();
                return competingIssuer.issue(
                        new ResolvedLoginIdentity(10, 20, true), "request-concurrent-2");
            });
            assertThat(competingCallStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releaseFirstSigner.countDown();

            IssuedLoginSession issued = first.get(5, TimeUnit.SECONDS);
            try {
                assertThatThrownBy(() -> competing.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(SessionLimitExceededException.class);
            } finally {
                issued.refreshToken().destroy();
            }

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM iam_login_session", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM iam_refresh_token", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(1);
        } finally {
            releaseFirstSigner.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private TransactionalJdbcLoginSessionIssuer issuer(
            AccessTokenSigner signer,
            long initialId) {
        return issuer(signer, initialId, properties());
    }

    private TransactionalJdbcLoginSessionIssuer issuer(
            AccessTokenSigner signer,
            long initialId,
            SessionIssuanceProperties issuanceProperties) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AtomicLong ids = new AtomicLong(initialId);
        PositiveIdGenerator idGenerator = ids::getAndIncrement;
        RefreshTokenHashKey key = new RefreshTokenHashKey(
                "test-key", new SecretKeySpec(new byte[32], "HmacSHA256"));
        SecureRandom random = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 0x42);
            }
        };
        var codec = new SessionProjectionEventCodec(new ObjectMapper());
        var appender = new JdbcSessionProjectionOutboxAppender(
                new JdbcOutboxWriter(jdbc), codec);
        return new TransactionalJdbcLoginSessionIssuer(
                jdbc,
                transactionManager,
                signer,
                idGenerator,
                new SecureOpaqueRefreshTokenGenerator(random, key),
                new HmacSha256RefreshTokenHasher(key),
                appender,
                clock,
                issuanceProperties);
    }

    private static AccessTokenSigner successfulSigner() {
        return request -> new SignedAccessToken(
                "header.payload.signature", NOW, NOW.plusSeconds(300));
    }

    private static SessionIssuanceProperties properties() {
        return properties(10);
    }

    private static SessionIssuanceProperties properties(int maximumConcurrentSessions) {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(1);
        properties.setMaximumConcurrentSessions(maximumConcurrentSessions);
        return properties;
    }
}
