package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.command.ChangePasswordCommand;
import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.PasswordHasher;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.outbox.JdbcLoginAuditOutboxAppender;
import com.enterprise.iam.auth.infrastructure.outbox.JdbcSessionProjectionOutboxAppender;
import com.enterprise.iam.auth.infrastructure.outbox.LoginAuditEventCodec;
import com.enterprise.iam.auth.infrastructure.outbox.SessionProjectionEventCodec;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.Argon2idPasswordVerifier;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import com.enterprise.iam.auth.infrastructure.security.SecureOpaqueRefreshTokenGenerator;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.SignedAccessToken;
import com.enterprise.iam.outbox.JdbcOutboxWriter;
import com.enterprise.iam.outbox.mapper.OutboxPersistenceMapper;
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
    private AuthPersistenceMapper mapper;
    private OutboxPersistenceMapper outboxMapper;
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
        mapper = AuthPersistenceTestMapperFactory.create(dataSource);
        outboxMapper = AuthPersistenceTestMapperFactory.createOutbox(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
        jdbc.update("DELETE FROM sys_outbox_event");
        jdbc.update("DELETE FROM iam_login_delivery");
        jdbc.update("DELETE FROM iam_refresh_token");
        jdbc.update("DELETE FROM iam_login_session");
        jdbc.update("DELETE FROM iam_password_credential");
        jdbc.update("DELETE FROM iam_user_security_state");
        jdbc.update("""
                INSERT INTO iam_user_security_state (
                    id, tenant_id, user_id, token_version, password_version,
                    login_failure_count, version, created_at, updated_at)
                VALUES (1, 10, 20, 4, 1, 0, 0, ?, ?)
                """, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    @Test
    void commitsSessionRefreshDeliveryProjectionAndAuditBeforeReturningTokens() {
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
                "SELECT COUNT(*) FROM iam_login_delivery", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT delivery_status FROM iam_login_delivery", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT request_id FROM iam_login_delivery", String.class))
                .isEqualTo("request-0001");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT token_version FROM iam_user_security_state "
                        + "WHERE tenant_id=10 AND user_id=20", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event WHERE event_status='PENDING'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sessionId')) "
                        + "FROM sys_outbox_event ORDER BY event_type", String.class))
                .containsOnly("101");
        assertThat(jdbc.queryForList(
                "SELECT event_type FROM sys_outbox_event ORDER BY event_type", String.class))
                .containsExactly("auth.login.succeeded", "iam.auth.session-security-projection");

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
                .isInstanceOf(SessionIssuanceException.class)
                .hasMessage("login session issuance failed")
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_session", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_refresh_token", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_login_delivery", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isZero();
    }

    @Test
    void passwordChangeCommitsVersionsOtherSessionRevocationAndThreeOutboxFacts() {
        String currentPassword = "current-password-value";
        String newPassword = "replacement-password-value";
        String rawRefresh = "rt1.test-key." + "A".repeat(43);
        PasswordHasher passwordHasher = seedPasswordChangeState(currentPassword, rawRefresh);
        TransactionalJdbcPasswordChanger changer = passwordChanger(
                passwordHasher, projectionAppender(), 80_000);

        changer.changePassword(new ChangePasswordCommand(
                rawRefresh.toCharArray(),
                currentPassword.toCharArray(),
                newPassword.toCharArray(),
                10L,
                20L,
                101L,
                "request-password-change"));

        assertThat(jdbc.queryForObject(
                "SELECT password_version FROM iam_password_credential "
                        + "WHERE tenant_id=10 AND user_id=20", Long.class)).isEqualTo(2);
        String passwordPhc = jdbc.queryForObject(
                "SELECT password_phc FROM iam_password_credential "
                        + "WHERE tenant_id=10 AND user_id=20", String.class);
        assertThat(passwordHasher.verify(newPassword.toCharArray(), passwordPhc)).isTrue();
        assertThat(passwordHasher.verify(currentPassword.toCharArray(), passwordPhc)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT token_version FROM iam_user_security_state "
                        + "WHERE tenant_id=10 AND user_id=20", Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT password_version FROM iam_user_security_state "
                        + "WHERE tenant_id=10 AND user_id=20", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session WHERE session_id=101", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session WHERE session_id=102", String.class))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                "SELECT revoke_reason FROM iam_login_session WHERE session_id=102", String.class))
                .isEqualTo("PASSWORD_CHANGED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token WHERE session_id=101", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token WHERE session_id=102", String.class))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                "SELECT revoke_reason FROM iam_refresh_token WHERE session_id=102", String.class))
                .isEqualTo("PASSWORD_CHANGED");
        assertThat(jdbc.queryForList(
                "SELECT event_type FROM sys_outbox_event ORDER BY event_type", String.class))
                .containsExactly(
                        "auth.password.changed",
                        "iam.auth.session-security-projection",
                        "iam.auth.session-security-projection");
        assertThat(jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.reason')) "
                        + "FROM sys_outbox_event WHERE event_type='auth.password.changed'",
                String.class)).isEqualTo("PASSWORD_CHANGED");
    }

    @Test
    void passwordChangeOutboxFailureRollsBackEveryRelationalMutation() {
        String currentPassword = "current-password-value";
        String rawRefresh = "rt1.test-key." + "B".repeat(43);
        PasswordHasher passwordHasher = seedPasswordChangeState(currentPassword, rawRefresh);
        SessionProjectionOutboxAppender failingAppender = (eventId, projection, occurredAt) -> {
            throw new IllegalStateException("outbox unavailable");
        };
        TransactionalJdbcPasswordChanger changer = passwordChanger(
                passwordHasher, failingAppender, 90_000);

        assertThatThrownBy(() -> changer.changePassword(new ChangePasswordCommand(
                rawRefresh.toCharArray(),
                currentPassword.toCharArray(),
                "replacement-password-value".toCharArray(),
                10L,
                20L,
                101L,
                "request-password-rollback")))
                .isInstanceOf(PasswordChangeException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT password_version FROM iam_password_credential", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT token_version FROM iam_user_security_state", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT password_version FROM iam_user_security_state", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session WHERE session_id=102", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token WHERE session_id=102", String.class))
                .isEqualTo("ROTATED");
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
                    "SELECT COUNT(*) FROM iam_login_delivery", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(2);
        } finally {
            releaseFirstSigner.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void confirmsDeliveredLoginIdempotentlyBeforeDeadline() {
        IssuedLoginSession issued = issuer(successfulSigner(), 30_000).issue(
                new ResolvedLoginIdentity(10, 20, true), "request-confirmed");
        TransactionalJdbcLoginDeliveryConfirmer confirmer =
                new TransactionalJdbcLoginDeliveryConfirmer(
                        mapper,
                        refreshHasher(),
                        transactionManager,
                        Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC),
                        properties());

        try {
            assertThat(confirmer.confirm(issued.sessionId(), issued.refreshToken())).isTrue();
            assertThat(confirmer.confirm(issued.sessionId(), issued.refreshToken())).isTrue();
        } finally {
            issued.refreshToken().destroy();
        }

        assertThat(jdbc.queryForObject(
                "SELECT delivery_status FROM iam_login_delivery", String.class))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox_event", Long.class)).isEqualTo(2);
    }

    @Test
    void reconcilesExpiredDeliveryInOneTransactionWithTerminalEvents() {
        IssuedLoginSession issued = issuer(successfulSigner(), 40_000).issue(
                new ResolvedLoginIdentity(10, 20, true), "request-orphan");
        TransactionalJdbcOrphanLoginSessionReconciler reconciler = reconciler(
                NOW.plusSeconds(61), 50_000);

        try {
            assertThat(reconciler.reconcileExpired()).isEqualTo(1);
        } finally {
            issued.refreshToken().destroy();
        }

        assertThat(jdbc.queryForObject(
                "SELECT delivery_status FROM iam_login_delivery", String.class))
                .isEqualTo("RECONCILED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_login_session", String.class)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                "SELECT revoke_reason FROM iam_login_session", String.class))
                .isEqualTo("LOGIN_DELIVERY_UNCONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM iam_refresh_token", String.class)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForList(
                "SELECT event_type FROM sys_outbox_event ORDER BY event_type", String.class))
                .containsExactly(
                        "auth.login.orphan-reconciled",
                        "auth.login.succeeded",
                        "iam.auth.session-security-projection",
                        "iam.auth.session-security-projection");
    }

    @Test
    void confirmationAndReconciliationRaceHasExactlyOneTerminalWinner() throws Exception {
        IssuedLoginSession issued = issuer(successfulSigner(), 60_000).issue(
                new ResolvedLoginIdentity(10, 20, true), "request-race");
        TransactionalJdbcLoginDeliveryConfirmer confirmer =
                new TransactionalJdbcLoginDeliveryConfirmer(
                        mapper,
                        refreshHasher(),
                        transactionManager,
                        Clock.fixed(NOW.plusSeconds(59), ZoneOffset.UTC),
                        properties());
        TransactionalJdbcOrphanLoginSessionReconciler reconciler = reconciler(
                NOW.plusSeconds(61), 70_000);
        CountDownLatch bothStarted = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        boolean confirmed;
        int reconciled;
        try {
            Future<Boolean> confirmation = executor.submit(() -> {
                bothStarted.countDown();
                bothStarted.await(5, TimeUnit.SECONDS);
                return confirmer.confirm(issued.sessionId(), issued.refreshToken());
            });
            Future<Integer> reconciliation = executor.submit(() -> {
                bothStarted.countDown();
                bothStarted.await(5, TimeUnit.SECONDS);
                return reconciler.reconcileExpired();
            });
            confirmed = confirmation.get(10, TimeUnit.SECONDS);
            reconciled = reconciliation.get(10, TimeUnit.SECONDS);
        } finally {
            issued.refreshToken().destroy();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        String deliveryStatus = jdbc.queryForObject(
                "SELECT delivery_status FROM iam_login_delivery", String.class);
        String sessionStatus = jdbc.queryForObject(
                "SELECT status FROM iam_login_session", String.class);
        if (confirmed) {
            assertThat(reconciled).isZero();
            assertThat(deliveryStatus).isEqualTo("CONFIRMED");
            assertThat(sessionStatus).isEqualTo("ACTIVE");
        } else {
            assertThat(reconciled).isEqualTo(1);
            assertThat(deliveryStatus).isEqualTo("RECONCILED");
            assertThat(sessionStatus).isEqualTo("REVOKED");
        }
    }

    private TransactionalJdbcLoginSessionIssuer issuer(
            AccessTokenSigner signer,
            long initialId) {
        return issuer(signer, initialId, properties());
    }

    private TransactionalJdbcPasswordChanger passwordChanger(
            PasswordHasher passwordHasher,
            SessionProjectionOutboxAppender sessionProjectionAppender,
            long initialId) {
        AtomicLong ids = new AtomicLong(initialId);
        return new TransactionalJdbcPasswordChanger(
                mapper,
                transactionManager,
                passwordHasher,
                refreshHasher(),
                ids::getAndIncrement,
                sessionProjectionAppender,
                auditAppender(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties());
    }

    private PasswordHasher seedPasswordChangeState(
            String currentPassword,
            String rawRefresh) {
        PasswordHasher passwordHasher = new Argon2idPasswordVerifier();
        String passwordPhc = passwordHasher.encode(currentPassword.toCharArray());
        jdbc.update("""
                INSERT INTO iam_password_credential (
                    id, tenant_id, user_id, password_phc, password_version,
                    status, changed_at, version, created_at, updated_at)
                VALUES (1, 10, 20, ?, 1, 'ACTIVE', ?, 0, ?, ?)
                """,
                passwordPhc,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));
        for (long sessionId : new long[] {101L, 102L}) {
            jdbc.update("""
                    INSERT INTO iam_login_session (
                        id, tenant_id, session_id, user_id, status,
                        last_access_at, idle_expire_at, absolute_expire_at,
                        last_strong_auth_at, session_version, created_at, updated_at)
                    VALUES (?, 10, ?, 20, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?)
                    """,
                    sessionId,
                    sessionId,
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW.plusSeconds(3_600)),
                    java.sql.Timestamp.from(NOW.plusSeconds(7_200)),
                    java.sql.Timestamp.from(NOW),
                    sessionId == 101L ? 2L : 5L,
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW));
        }
        byte[] currentHash = hashRefresh(rawRefresh);
        byte[] otherHash = hashRefresh("rt1.test-key." + "Z".repeat(43));
        try {
            jdbc.update("""
                    INSERT INTO iam_refresh_token (
                        id, tenant_id, user_id, session_id, token_family_id,
                        token_hash, status, issued_at, expire_at, version,
                        created_at, updated_at)
                    VALUES (201, 10, 20, 101, 301, ?, 'ACTIVE', ?, ?, 0, ?, ?)
                    """,
                    currentHash,
                    java.sql.Timestamp.from(NOW.minusSeconds(60)),
                    java.sql.Timestamp.from(NOW.plusSeconds(3_600)),
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW));
            jdbc.update("""
                    INSERT INTO iam_refresh_token (
                        id, tenant_id, user_id, session_id, token_family_id,
                        token_hash, status, issued_at, expire_at, rotated_at, version,
                        created_at, updated_at)
                    VALUES (202, 10, 20, 102, 302, ?, 'ROTATED', ?, ?, ?, 1, ?, ?)
                    """,
                    otherHash,
                    java.sql.Timestamp.from(NOW.minusSeconds(120)),
                    java.sql.Timestamp.from(NOW.plusSeconds(3_600)),
                    java.sql.Timestamp.from(NOW.minusSeconds(30)),
                    java.sql.Timestamp.from(NOW),
                    java.sql.Timestamp.from(NOW));
        } finally {
            Arrays.fill(currentHash, (byte) 0);
            Arrays.fill(otherHash, (byte) 0);
        }
        return passwordHasher;
    }

    private static byte[] hashRefresh(String rawRefresh) {
        try (SensitiveRefreshToken token = new SensitiveRefreshToken(rawRefresh.toCharArray())) {
            return refreshHasher().hash(token);
        }
    }

    private TransactionalJdbcLoginSessionIssuer issuer(
            AccessTokenSigner signer,
            long initialId,
            SessionIssuanceProperties issuanceProperties) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AtomicLong ids = new AtomicLong(initialId);
        PositiveIdGenerator idGenerator = ids::getAndIncrement;
        SecureRandom random = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 0x42);
            }
        };
        return new TransactionalJdbcLoginSessionIssuer(
                mapper,
                transactionManager,
                signer,
                idGenerator,
                new SecureOpaqueRefreshTokenGenerator(random, refreshHashKey()),
                refreshHasher(),
                projectionAppender(),
                auditAppender(),
                clock,
                issuanceProperties);
    }

    private TransactionalJdbcOrphanLoginSessionReconciler reconciler(
            Instant now,
            long initialId) {
        AtomicLong ids = new AtomicLong(initialId);
        return new TransactionalJdbcOrphanLoginSessionReconciler(
                mapper,
                transactionManager,
                ids::getAndIncrement,
                projectionAppender(),
                auditAppender(),
                Clock.fixed(now, ZoneOffset.UTC),
                properties());
    }

    private JdbcSessionProjectionOutboxAppender projectionAppender() {
        return new JdbcSessionProjectionOutboxAppender(
                new JdbcOutboxWriter(outboxMapper),
                new SessionProjectionEventCodec(new ObjectMapper()));
    }

    private JdbcLoginAuditOutboxAppender auditAppender() {
        return new JdbcLoginAuditOutboxAppender(
                new JdbcOutboxWriter(outboxMapper),
                new LoginAuditEventCodec(new ObjectMapper().findAndRegisterModules()));
    }

    private static HmacSha256RefreshTokenHasher refreshHasher() {
        return new HmacSha256RefreshTokenHasher(refreshHashKey());
    }

    private static RefreshTokenHashKey refreshHashKey() {
        return new RefreshTokenHashKey(
                "test-key", new SecretKeySpec(new byte[32], "HmacSHA256"));
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
