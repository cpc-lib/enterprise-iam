package com.enterprise.iam.testsupport.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MySqlIntegrationDatabaseTest {

    @Test
    void serviceSpecificSettingsTakePrecedenceOverTemplateAndGenericCredentials() {
        Map<String, String> environment = Map.of(
                "IAM_TEST_AUTH_MYSQL_JDBC_URL", "jdbc:mysql://auth/iam_auth",
                "IAM_TEST_AUTH_MYSQL_USERNAME", "auth_user",
                "IAM_TEST_AUTH_MYSQL_PASSWORD", "auth_password",
                "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE", "jdbc:mysql://shared/{database}",
                "IAM_TEST_MYSQL_USERNAME", "generic_user",
                "IAM_TEST_MYSQL_PASSWORD", "generic_password");

        MySqlIntegrationDatabase.ExternalConnection connection =
                resolve("auth", "iam_auth", environment);

        assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://auth/iam_auth");
        assertThat(connection.username()).isEqualTo("auth_user");
        assertThat(connection.password()).isEqualTo("auth_password");
    }

    @Test
    void templateCreatesAnIsolatedDatabaseUrlAndUsesGenericCredentials() {
        Map<String, String> environment = Map.of(
                "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE", "jdbc:mysql://shared/{database}?useSSL=true",
                "IAM_TEST_MYSQL_USERNAME", "ci_user",
                "IAM_TEST_MYSQL_PASSWORD", "ci_password");

        MySqlIntegrationDatabase.ExternalConnection connection =
                resolve("identity", "iam_identity", environment);

        assertThat(connection.jdbcUrl())
                .isEqualTo("jdbc:mysql://shared/iam_identity?useSSL=true");
        assertThat(connection.username()).isEqualTo("ci_user");
        assertThat(connection.password()).isEqualTo("ci_password");
    }

    @Test
    void legacyUrlAppliesOnlyToAuth() {
        Map<String, String> environment = Map.of(
                "IAM_TEST_MYSQL_JDBC_URL", "jdbc:mysql://legacy/iam_auth");

        assertThat(resolve("auth", "iam_auth", environment).jdbcUrl())
                .isEqualTo("jdbc:mysql://legacy/iam_auth");
        assertThat(resolve("authorization", "iam_authorization", environment)).isNull();
    }

    @Test
    void templateMustContainExactlyOneDatabasePlaceholder() {
        Map<String, String> missing = Map.of(
                "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE", "jdbc:mysql://shared/fixed");
        Map<String, String> duplicate = Map.of(
                "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE",
                "jdbc:mysql://shared/{database}?schema={database}");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolve("auth", "iam_auth", missing))
                .withMessageContaining("exactly one {database}");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolve("auth", "iam_auth", duplicate))
                .withMessageContaining("exactly one {database}");
    }

    @Test
    void unsafeServiceAndDatabaseNamesAreRejectedBeforeEnvironmentLookup() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolve("AUTH!", "iam_auth", Map.of()))
                .withMessage("serviceKey is invalid");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolve("auth", "iam-auth", Map.of()))
                .withMessage("databaseName is invalid");
    }

    private static MySqlIntegrationDatabase.ExternalConnection resolve(
            String serviceKey,
            String databaseName,
            Map<String, String> environment) {
        return MySqlIntegrationDatabase.resolveExternalConnection(
                serviceKey,
                databaseName,
                environment::get);
    }
}
