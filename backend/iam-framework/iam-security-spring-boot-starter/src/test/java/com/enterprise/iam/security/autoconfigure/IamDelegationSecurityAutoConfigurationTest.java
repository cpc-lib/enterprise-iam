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
    void registersFailClosedFilterForExactServiceAudience() {
        contextRunner
                .withBean(DelegationPublicKeyResolver.class, () -> keyId -> Optional.empty())
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service",
                        "iam.security.delegation.protected-paths[0]=/internal/v1/**")
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
    void rejectsProtectedPathOutsideInternalNamespace() {
        contextRunner
                .withBean(DelegationPublicKeyResolver.class, () -> keyId -> Optional.empty())
                .withPropertyValues(
                        "iam.security.delegation.enabled=true",
                        "iam.security.delegation.issuer=iam-gateway",
                        "iam.security.delegation.audience=iam-authorization-service",
                        "iam.security.delegation.protected-paths[0]=/**")
                .run(context -> assertThat(context).hasFailed());
    }
}
