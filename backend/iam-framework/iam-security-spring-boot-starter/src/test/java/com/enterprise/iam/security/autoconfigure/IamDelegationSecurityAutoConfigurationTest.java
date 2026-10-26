package com.enterprise.iam.security.autoconfigure;

import com.enterprise.iam.common.security.delegation.DelegationPublicKeyResolver;
import com.enterprise.iam.common.security.delegation.DelegationJwkSetLoader;
import com.enterprise.iam.common.security.delegation.DelegationTokenDecoder;
import com.enterprise.iam.security.delegation.TrustedDelegationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IamDelegationSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            IamDelegationSecurityAutoConfiguration.class));

    @Test
    void remainsDisabledUnlessExplicitlyEnabled() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(DelegationTokenDecoder.class));
    }

    @Test
    void registersFailClosedFilterForExactServiceAudienceAndApiPath() {
        contextRunner
                .withBean(DelegationPublicKeyResolver.class, () -> keyId -> Optional.empty())
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-auth-service",
                        "iam.security.delegation.protected-paths[0]=/api/v1/auth/password/change")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DelegationTokenDecoder.class);
                    FilterRegistrationBean<?> registration = context.getBean(
                            "iamTrustedDelegationFilterRegistration",
                            FilterRegistrationBean.class);
                    assertThat(registration.getFilter())
                            .isInstanceOf(TrustedDelegationFilter.class);
                });
    }

    @Test
    void enabledConfigurationFailsClosedWithoutKeyResolver() {
        contextRunner
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void createsRefreshingResolverWhenDeploymentProvidesJwksLoader() {
        String emptyButValidShape = "{\"keys\":[]}";
        contextRunner
                .withBean(DelegationJwkSetLoader.class, () -> () -> emptyButValidShape)
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DelegationPublicKeyResolver.class);
                });
    }

    @Test
    void rejectsBroadRootProtectedPath() {
        contextRunner
                .withBean(DelegationPublicKeyResolver.class, () -> keyId -> Optional.empty())
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service",
                        "iam.security.delegation.protected-paths[0]=/**")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void autoProvidesHttpJwksLoaderAndResolverWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service",
                        "iam.security.delegation.jwks-uri=http://iam-gateway:8080/internal/v1/gateway/delegation-jwks.json",
                        "iam.security.delegation.jwks-allowed-hosts[0]=iam-gateway",
                        "iam.security.delegation.jwks-allow-http=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DelegationJwkSetLoader.class))
                            .isInstanceOf(HttpDelegationJwkSetLoader.class);
                    assertThat(context).hasSingleBean(DelegationPublicKeyResolver.class);
                });
    }

    @Test
    void autoProvidesHttpsLoaderWithoutAllowHttp() {
        contextRunner
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service",
                        "iam.security.delegation.jwks-uri=https://iam-gateway.example.net/internal/v1/gateway/delegation-jwks.json",
                        "iam.security.delegation.jwks-allowed-hosts[0]=iam-gateway.example.net")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DelegationJwkSetLoader.class))
                            .isInstanceOf(HttpDelegationJwkSetLoader.class);
                    assertThat(context).hasSingleBean(DelegationPublicKeyResolver.class);
                });
    }

    @Test
    void autoLoaderFailsClosedWithoutJwksUri() {
        contextRunner
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("jwks-uri");
                });
    }

    @Test
    void autoLoaderRejectsPlainHttpUnlessExplicitlyAllowed() {
        contextRunner
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service",
                        "iam.security.delegation.jwks-uri=http://iam-gateway:8080/internal/v1/gateway/delegation-jwks.json",
                        "iam.security.delegation.jwks-allowed-hosts[0]=iam-gateway")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("jwks-allow-http");
                });
    }
}
