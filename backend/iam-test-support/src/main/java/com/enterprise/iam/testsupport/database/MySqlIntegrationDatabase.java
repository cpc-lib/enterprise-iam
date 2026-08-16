package com.enterprise.iam.testsupport.database;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Provides an isolated MySQL database for integration tests. CI may supply a
 * service-specific JDBC URL or a URL template; otherwise a pinned container is
 * started. Secrets are read only from the environment and are never rendered.
 */
public final class MySqlIntegrationDatabase implements AutoCloseable {

    private static final String URL_TEMPLATE_ENV = "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE";
    private static final String LEGACY_AUTH_URL_ENV = "IAM_TEST_MYSQL_JDBC_URL";
    private static final String GENERIC_USERNAME_ENV = "IAM_TEST_MYSQL_USERNAME";
    private static final String GENERIC_PASSWORD_ENV = "IAM_TEST_MYSQL_PASSWORD";
    private static final String DATABASE_PLACEHOLDER = "{database}";
    private static final Pattern SAFE_KEY = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final Pattern SAFE_DATABASE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final MySQLContainer<?> container;

    private MySqlIntegrationDatabase(
            String jdbcUrl,
            String username,
            String password,
            MySQLContainer<?> container) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.container = container;
    }

    public static MySqlIntegrationDatabase start(String serviceKey, String databaseName) {
        ExternalConnection external = resolveExternalConnection(
                serviceKey,
                databaseName,
                System::getenv);
        if (external != null) {
            return new MySqlIntegrationDatabase(
                    external.jdbcUrl(),
                    external.username(),
                    external.password(),
                    null);
        }

        String normalizedService = requireSafe(serviceKey, SAFE_KEY, "serviceKey");
        String normalizedDatabase = requireSafe(databaseName, SAFE_DATABASE, "databaseName");

        MySQLContainer<?> mysql = new MySQLContainer<>(
                DockerImageName.parse("mysql:8.4.9"))
                .withDatabaseName(normalizedDatabase)
                .withEnv("TZ", "UTC");
        mysql.start();
        return new MySqlIntegrationDatabase(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword(),
                mysql);
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public void close() {
        if (container != null) {
            container.stop();
        }
    }

    static ExternalConnection resolveExternalConnection(
            String serviceKey,
            String databaseName,
            Function<String, String> environment) {
        String normalizedService = requireSafe(serviceKey, SAFE_KEY, "serviceKey");
        String normalizedDatabase = requireSafe(databaseName, SAFE_DATABASE, "databaseName");
        Objects.requireNonNull(environment, "environment must not be null");
        String servicePrefix = "IAM_TEST_"
                + normalizedService.toUpperCase(Locale.ROOT) + "_MYSQL_";

        String externalJdbcUrl = trimToNull(environment.apply(servicePrefix + "JDBC_URL"));
        if (externalJdbcUrl == null) {
            externalJdbcUrl = urlFromTemplate(normalizedDatabase, environment);
        }
        if (externalJdbcUrl == null && "auth".equals(normalizedService)) {
            externalJdbcUrl = trimToNull(environment.apply(LEGACY_AUTH_URL_ENV));
        }
        if (externalJdbcUrl == null) {
            return null;
        }
        return new ExternalConnection(
                externalJdbcUrl,
                firstNonBlank(
                        environment.apply(servicePrefix + "USERNAME"),
                        environment.apply(GENERIC_USERNAME_ENV),
                        "root"),
                firstNonBlank(
                        environment.apply(servicePrefix + "PASSWORD"),
                        environment.apply(GENERIC_PASSWORD_ENV),
                        ""));
    }

    private static String urlFromTemplate(
            String databaseName,
            Function<String, String> environment) {
        String template = trimToNull(environment.apply(URL_TEMPLATE_ENV));
        if (template == null) {
            return null;
        }
        int placeholder = template.indexOf(DATABASE_PLACEHOLDER);
        if (placeholder < 0 || placeholder != template.lastIndexOf(DATABASE_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    URL_TEMPLATE_ENV + " must contain exactly one " + DATABASE_PLACEHOLDER);
        }
        return template.replace(DATABASE_PLACEHOLDER, databaseName);
    }

    private static String requireSafe(String value, Pattern pattern, String name) {
        String normalized = Objects.requireNonNull(value, name + " must not be null").trim();
        if (!pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        String value = trimToNull(first);
        if (value == null) {
            value = trimToNull(second);
        }
        return value == null ? fallback : value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    record ExternalConnection(String jdbcUrl, String username, String password) {

        ExternalConnection {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(password, "password must not be null");
        }
    }
}
