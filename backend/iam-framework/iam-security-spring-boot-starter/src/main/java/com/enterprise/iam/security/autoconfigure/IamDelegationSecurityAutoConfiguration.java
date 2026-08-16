package com.enterprise.iam.security.autoconfigure;

import com.enterprise.iam.common.security.delegation.DelegationPublicKeyResolver;
import com.enterprise.iam.common.security.delegation.DelegationJwkSetLoader;
import com.enterprise.iam.common.security.delegation.DelegationTokenDecoder;
import com.enterprise.iam.common.security.delegation.DelegationTokenPolicy;
import com.enterprise.iam.common.security.delegation.Es256DelegationTokenDecoder;
import com.enterprise.iam.common.security.delegation.RefreshingJwkSetDelegationPublicKeyResolver;
import com.enterprise.iam.security.delegation.PathPatternDelegationRequestMatcher;
import com.enterprise.iam.security.delegation.RequestTraceIdResolver;
import com.enterprise.iam.security.delegation.TrustedDelegationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Clock;
import java.util.Set;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "iam.security.delegation",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(IamDelegationSecurityProperties.class)
public class IamDelegationSecurityAutoConfiguration {

    @Bean("iamDelegationClock")
    @ConditionalOnMissingBean(name = "iamDelegationClock")
    Clock iamDelegationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean(DelegationJwkSetLoader.class)
    @ConditionalOnMissingBean(DelegationPublicKeyResolver.class)
    DelegationPublicKeyResolver iamDelegationPublicKeyResolver(
            DelegationJwkSetLoader loader,
            @Qualifier("iamDelegationClock") Clock clock,
            IamDelegationSecurityProperties properties) {
        properties.validateEnabledConfiguration();
        return new RefreshingJwkSetDelegationPublicKeyResolver(
                loader,
                clock,
                properties.getJwksCacheTtl(),
                properties.getUnknownKeyTtl(),
                properties.getUnknownKeyRefreshMinimumInterval());
    }

    @Bean
    @ConditionalOnMissingBean
    DelegationTokenPolicy iamDelegationTokenPolicy(
            IamDelegationSecurityProperties properties,
            @Qualifier("iamDelegationClock") Clock iamDelegationClock) {
        properties.validateEnabledConfiguration();
        return new DelegationTokenPolicy(
                iamDelegationClock,
                Set.of("ES256"),
                properties.getIssuer(),
                properties.getAudience(),
                properties.getMaximumTtl(),
                properties.getClockSkew());
    }

    @Bean
    @ConditionalOnMissingBean
    DelegationTokenDecoder iamDelegationTokenDecoder(
            DelegationPublicKeyResolver keyResolver,
            DelegationTokenPolicy policy) {
        return new Es256DelegationTokenDecoder(keyResolver, policy);
    }

    @Bean("iamDelegationProtectedRequestMatcher")
    @ConditionalOnMissingBean(name = "iamDelegationProtectedRequestMatcher")
    RequestMatcher iamDelegationProtectedRequestMatcher(
            IamDelegationSecurityProperties properties) {
        properties.validateEnabledConfiguration();
        return new PathPatternDelegationRequestMatcher(properties.getProtectedPaths());
    }

    @Bean
    @ConditionalOnMissingBean
    RequestTraceIdResolver iamRequestTraceIdResolver() {
        return request -> request.getHeader("X-Request-Id");
    }

    @Bean("iamTrustedDelegationFilterRegistration")
    @ConditionalOnMissingBean(name = "iamTrustedDelegationFilterRegistration")
    FilterRegistrationBean<TrustedDelegationFilter> iamTrustedDelegationFilterRegistration(
            DelegationTokenDecoder decoder,
            @Qualifier("iamDelegationProtectedRequestMatcher")
            RequestMatcher iamDelegationProtectedRequestMatcher,
            RequestTraceIdResolver traceIdResolver,
            IamDelegationSecurityProperties properties) {
        TrustedDelegationFilter filter = new TrustedDelegationFilter(
                decoder, iamDelegationProtectedRequestMatcher, traceIdResolver);
        FilterRegistrationBean<TrustedDelegationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setName("iamTrustedDelegationFilter");
        registration.setOrder(properties.getFilterOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
