package com.enterprise.iam.gateway.config;

import com.enterprise.iam.common.security.access.AccessTokenDecoder;
import com.enterprise.iam.common.security.access.AccessTokenJwkSetLoader;
import com.enterprise.iam.common.security.access.AccessTokenPolicy;
import com.enterprise.iam.common.security.access.AccessTokenPublicKeyResolver;
import com.enterprise.iam.common.security.access.Es256AccessTokenDecoder;
import com.enterprise.iam.common.security.access.RefreshingJwkSetAccessTokenPublicKeyResolver;
import com.enterprise.iam.gateway.delegation.DownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.GatewayDelegationFilter;
import com.enterprise.iam.gateway.security.DefaultGatewayRequestIdResolver;
import com.enterprise.iam.gateway.security.ExternalIdentityHeaderSanitizingFilter;
import com.enterprise.iam.gateway.security.AuthoritativeReactiveSessionStateVerifier;
import com.enterprise.iam.gateway.security.GatewayAccessAuthenticationFilter;
import com.enterprise.iam.gateway.security.GatewayRequestIdResolver;
import com.enterprise.iam.gateway.security.ReactiveSessionStateVerifier;
import com.enterprise.iam.gateway.security.ReactiveSessionSnapshotReader;
import com.enterprise.iam.gateway.security.RedisReactiveSessionSnapshotReader;
import com.enterprise.iam.gateway.security.jwks.AllowlistedHttpsAccessTokenJwkSetLoader;
import com.enterprise.iam.gateway.security.jwks.JavaHttpClientJwksTransport;
import com.enterprise.iam.gateway.security.jwks.JwksDnsResolver;
import com.enterprise.iam.gateway.security.jwks.JwksHttpTransport;
import com.enterprise.iam.gateway.security.jwks.SystemJwksDnsResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "iam.gateway.access-authentication",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(GatewayAccessAuthenticationProperties.class)
public class GatewayAccessAuthenticationConfiguration {

    @Bean("iamGatewayAccessTokenClock")
    @ConditionalOnMissingBean(name = "iamGatewayAccessTokenClock")
    Clock iamGatewayAccessTokenClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    JwksDnsResolver jwksDnsResolver() {
        return new SystemJwksDnsResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    JwksHttpTransport jwksHttpTransport(
            GatewayAccessAuthenticationProperties properties) {
        properties.validateEnabledConfiguration();
        return new JavaHttpClientJwksTransport(properties.getJwksConnectTimeout());
    }

    @Bean
    @ConditionalOnMissingBean({
            AccessTokenPublicKeyResolver.class,
            AccessTokenJwkSetLoader.class})
    AccessTokenJwkSetLoader accessTokenJwkSetLoader(
            JwksDnsResolver dnsResolver,
            JwksHttpTransport transport,
            GatewayAccessAuthenticationProperties properties) {
        properties.validateJwksTransportConfiguration();
        return new AllowlistedHttpsAccessTokenJwkSetLoader(
                properties.getJwksUri(),
                properties.getJwksAllowedHosts(),
                dnsResolver,
                transport,
                properties.getJwksRequestTimeout());
    }

    @Bean
    @ConditionalOnBean(AccessTokenJwkSetLoader.class)
    @ConditionalOnMissingBean(AccessTokenPublicKeyResolver.class)
    AccessTokenPublicKeyResolver accessTokenPublicKeyResolver(
            AccessTokenJwkSetLoader loader,
            @Qualifier("iamGatewayAccessTokenClock") Clock clock,
            GatewayAccessAuthenticationProperties properties) {
        properties.validateEnabledConfiguration();
        return new RefreshingJwkSetAccessTokenPublicKeyResolver(
                loader,
                clock,
                properties.getJwksCacheTtl(),
                properties.getUnknownKeyTtl(),
                properties.getUnknownKeyRefreshMinimumInterval());
    }

    @Bean
    @ConditionalOnMissingBean
    AccessTokenPolicy accessTokenPolicy(
            @Qualifier("iamGatewayAccessTokenClock") Clock clock,
            GatewayAccessAuthenticationProperties properties) {
        properties.validateEnabledConfiguration();
        return new AccessTokenPolicy(
                clock,
                properties.getIssuer(),
                properties.getAudience(),
                properties.getMaximumTtl(),
                properties.getClockSkew());
    }

    @Bean
    @ConditionalOnMissingBean
    AccessTokenDecoder accessTokenDecoder(
            AccessTokenPublicKeyResolver keyResolver,
            AccessTokenPolicy policy) {
        return new Es256AccessTokenDecoder(keyResolver, policy);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayRequestIdResolver gatewayRequestIdResolver() {
        return new DefaultGatewayRequestIdResolver();
    }

    @Bean
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(ReactiveSessionSnapshotReader.class)
    ReactiveSessionSnapshotReader reactiveSessionSnapshotReader(
            ReactiveStringRedisTemplate redisTemplate) {
        return new RedisReactiveSessionSnapshotReader(redisTemplate);
    }

    @Bean
    @ConditionalOnBean(ReactiveSessionSnapshotReader.class)
    @ConditionalOnMissingBean(ReactiveSessionStateVerifier.class)
    ReactiveSessionStateVerifier reactiveSessionStateVerifier(
            ReactiveSessionSnapshotReader reader,
            @Qualifier("iamGatewayAccessTokenClock") Clock clock) {
        return new AuthoritativeReactiveSessionStateVerifier(reader, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayAccessAuthenticationFilter gatewayAccessAuthenticationFilter(
            DownstreamRouteAudienceRegistry routeRegistry,
            AccessTokenDecoder tokenDecoder,
            ReactiveSessionStateVerifier sessionVerifier,
            GatewayRequestIdResolver requestIdResolver,
            ExternalIdentityHeaderSanitizingFilter sanitizingFilter,
            GatewayDelegationFilter delegationFilter) {
        if (!(sanitizingFilter.getOrder() < GatewayAccessAuthenticationFilter.ORDER
                && GatewayAccessAuthenticationFilter.ORDER < delegationFilter.getOrder())) {
            throw new IllegalStateException(
                    "Gateway security filters must run sanitizer -> authentication -> delegation");
        }
        return new GatewayAccessAuthenticationFilter(
                routeRegistry, tokenDecoder, sessionVerifier, requestIdResolver);
    }
}
