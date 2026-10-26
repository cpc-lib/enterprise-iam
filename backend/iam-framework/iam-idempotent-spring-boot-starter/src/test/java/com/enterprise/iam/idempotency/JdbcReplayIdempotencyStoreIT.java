package com.enterprise.iam.idempotency;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper;
import com.enterprise.iam.testsupport.database.MySqlIntegrationDatabase;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcReplayIdempotencyStoreIT {

    private static final String SCHEMA_DDL = """
            CREATE TABLE iam_idempotency_record (
                tenant_id BIGINT UNSIGNED NOT NULL,
                idempotency_key VARCHAR(128) NOT NULL,
                request_hash CHAR(64) NOT NULL,
                status VARCHAR(16) NOT NULL,
                response_status INT NULL,
                response_content_type VARCHAR(255) NULL,
                response_body LONGBLOB NULL,
                expires_at DATETIME(3) NOT NULL,
                created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                PRIMARY KEY (tenant_id, idempotency_key),
                KEY idx_idempotency_expires (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """;

    private static MySqlIntegrationDatabase mysql;

    private JdbcTemplate jdbcTemplate;
    private JdbcReplayIdempotencyStore store;
    private Instant now;

    @BeforeAll
    static void startDatabase() {
        mysql = MySqlIntegrationDatabase.start("idempotency", "iam_idempotency");
    }

    @AfterAll
    static void stopDatabase() {
        if (mysql != null) {
            mysql.close();
        }
    }

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                mysql.jdbcUrl(), mysql.username(), mysql.password());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_idempotency_record");
        jdbcTemplate.execute(SCHEMA_DDL);
        store = new JdbcReplayIdempotencyStore(
                createMapper(dataSource), new DataSourceTransactionManager(dataSource), Duration.ofHours(1));
        now = Instant.parse("2026-09-26T00:00:00Z");
    }

    @Test
    void newClaimThenCommitThenReplaySnapshot() {
        assertThat(store.claim(1L, "key-a", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);

        CachedHttpResponse snapshot = new CachedHttpResponse(
                201, "application/json", "{\"id\":42}".getBytes(StandardCharsets.UTF_8));
        store.commit(1L, "key-a", snapshot, now);

        ReplayIdempotencyStore.ClaimOutcome outcome = store.claim(1L, "key-a", "hash-a", now.plusSeconds(30));
        assertThat(outcome).isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.Replay.class);
        CachedHttpResponse replayed =
                ((ReplayIdempotencyStore.ClaimOutcome.Replay) outcome).response();
        assertThat(replayed.status()).isEqualTo(201);
        assertThat(replayed.contentType()).isEqualTo("application/json");
        assertThat(new String(replayed.body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":42}");
    }

    @Test
    void differentBodyWhileInFlightConflicts() {
        assertThat(store.claim(1L, "key-b", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);

        assertThat(store.claim(1L, "key-b", "hash-other", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.DifferentBody.class);
    }

    @Test
    void sameBodyInFlightConflicts() {
        assertThat(store.claim(1L, "key-c", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);

        assertThat(store.claim(1L, "key-c", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.InFlight.class);
    }

    @Test
    void releaseAllowsRetryWithNewClaim() {
        assertThat(store.claim(1L, "key-d", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);

        store.release(1L, "key-d");

        assertThat(store.claim(1L, "key-d", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);
    }

    @Test
    void releaseDoesNotRemoveCommittedReplay() {
        store.claim(1L, "key-e", "hash-a", now);
        store.commit(1L, "key-e",
                new CachedHttpResponse(200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8)), now);

        store.release(1L, "key-e");

        assertThat(store.claim(1L, "key-e", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.Replay.class);
    }

    @Test
    void expiredClaimIsTakenOver() {
        assertThat(store.claim(1L, "key-f", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);

        // Same key, same hash, but the previous claim has expired: retry claims anew.
        Instant later = now.plus(Duration.ofHours(2));
        assertThat(store.claim(1L, "key-f", "hash-a", later))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);
    }

    @Test
    void claimsAreIsolatedPerTenant() {
        assertThat(store.claim(1L, "key-g", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);

        assertThat(store.claim(2L, "key-g", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.New.class);
        assertThat(store.claim(1L, "key-g", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.InFlight.class);
    }

    @Test
    void concurrentFirstClaimsYieldOneWinnerAndOneInFlightConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ReplayIdempotencyStore.ClaimOutcome> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.claim(1L, "key-concurrent", "hash-a", now);
            });
            Future<ReplayIdempotencyStore.ClaimOutcome> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.claim(1L, "key-concurrent", "hash-a", now);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Class<?>> outcomeTypes = List.of(first.get().getClass(), second.get().getClass());

            assertThat(outcomeTypes).containsExactlyInAnyOrder(
                    ReplayIdempotencyStore.ClaimOutcome.New.class,
                    ReplayIdempotencyStore.ClaimOutcome.InFlight.class);
        }
    }

    private static IdempotencyPersistenceMapper createMapper(DataSource dataSource) {
        try {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            configuration.addMapper(IdempotencyPersistenceMapper.class);
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            factoryBean.setMapperLocations(
                    new ClassPathResource("mapper/IdempotencyPersistenceMapper.xml"));
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("MyBatis session factory was not created");
            }
            return new SqlSessionTemplate(factory).getMapper(IdempotencyPersistenceMapper.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create idempotency mapper", exception);
        }
    }
}
