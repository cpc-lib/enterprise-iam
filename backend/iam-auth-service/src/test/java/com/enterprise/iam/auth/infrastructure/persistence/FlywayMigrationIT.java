package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.testsupport.database.MySqlIntegrationDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT {

    private static MySqlIntegrationDatabase mysql;

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

    @Test
    void cleanMigrationThenSecondRunIsNoOpAndValidatePasses() {
        Flyway flyway = Flyway.configure()
                .dataSource(mysql.jdbcUrl(), mysql.username(), mysql.password())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .validateOnMigrate(true)
                .load();

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.success).isTrue();
        assertThat(first.migrationsExecuted).isGreaterThan(0);
        assertThat(second.success).isTrue();
        assertThat(second.migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
