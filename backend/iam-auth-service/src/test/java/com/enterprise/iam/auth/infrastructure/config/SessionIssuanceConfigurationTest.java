package com.enterprise.iam.auth.infrastructure.config;

import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.SignedAccessToken;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionIssuanceConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SessionIssuanceConfiguration.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class,
                    () -> mock(PlatformTransactionManager.class))
            .withBean(SessionProjectionOutboxAppender.class,
                    () -> (eventId, projection, occurredAt) -> { })
            .withBean(RefreshTokenHashKey.class,
                    SessionIssuanceConfigurationTest::hashKey);

    @Test
    void remainsDisabledByDefault() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(LoginSessionIssuer.class));
    }

    @Test
    void enabledConfigurationFailsClosedWithoutSigningCapability() {
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.session-issuance.node-id=7")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("AccessTokenSigner");
                });
    }

    @Test
    void enabledConfigurationRequiresExplicitNodeId() {
        runner.withPropertyValues("iam.auth.session-issuance.enabled=true")
                .withBean(AccessTokenSigner.class,
                        SessionIssuanceConfigurationTest::signer)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("node-id must be explicitly set");
                });
    }

    @Test
    void createsIssuerOnlyWithAllSecurityCapabilities() {
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.session-issuance.node-id=7")
                .withBean(AccessTokenSigner.class,
                        SessionIssuanceConfigurationTest::signer)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LoginSessionIssuer.class);
                });
    }

    private static RefreshTokenHashKey hashKey() {
        return new RefreshTokenHashKey(
                "test-key",
                new SecretKeySpec(new byte[32], "HmacSHA256"));
    }

    private static AccessTokenSigner signer() {
        return request -> new SignedAccessToken(
                "header.payload.signature",
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:05:00Z"));
    }
}
