package com.enterprise.iam.gateway.config;

import com.enterprise.iam.common.security.access.AccessTokenDecoder;
import com.enterprise.iam.common.security.access.AccessTokenJwkSetLoader;
import com.enterprise.iam.common.security.access.AccessTokenPublicKeyResolver;
import com.enterprise.iam.gateway.delegation.ConfiguredDownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.DownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.GatewayDelegationFilter;
import com.enterprise.iam.gateway.security.ExternalIdentityHeaderSanitizingFilter;
import com.enterprise.iam.gateway.security.GatewayAccessAuthenticationFilter;
import com.enterprise.iam.gateway.security.ReactiveSessionSnapshotReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class GatewayAccessAuthenticationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayAccessAuthenticationConfiguration.class)
            .withBean(ExternalIdentityHeaderSanitizingFilter.class,
                    ExternalIdentityHeaderSanitizingFilter::new)
            .withBean(DownstreamRouteAudienceRegistry.class,
                    GatewayAccessAuthenticationConfigurationTest::routeRegistry)
            .withBean(GatewayDelegationFilter.class,
                    () -> new GatewayDelegationFilter(routeRegistry(),
                            (audience, principal) -> "delegation"));

    @Test
    void remainsDisabledUnlessExplicitlyEnabled() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(AccessTokenDecoder.class));
    }

    @Test
    void enabledConfigurationFailsWithoutKeyAndSessionSources() {
        contextRunner
                .withPropertyValues("iam.gateway.access-authentication.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void createsStrictDecoderSessionVerifierAndOrderedFilter() {
        contextRunner
                .withBean(AccessTokenPublicKeyResolver.class,
                        () -> keyId -> Optional.empty())
                .withBean(ReactiveSessionSnapshotReader.class,
                        () -> (tenantId, subjectId, sessionId) -> Mono.empty())
                .withPropertyValues("iam.gateway.access-authentication.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AccessTokenDecoder.class);
                    assertThat(context).hasSingleBean(GatewayAccessAuthenticationFilter.class);
                });
    }

    @Test
    void createsHardenedJwksLoaderAndRedisReaderFromProductionAdapters() {
        contextRunner
                .withBean(ReactiveStringRedisTemplate.class,
                        () -> mock(ReactiveStringRedisTemplate.class, RETURNS_DEEP_STUBS))
                .withPropertyValues(
                        "iam.gateway.access-authentication.enabled=true",
                        "iam.gateway.access-authentication.jwks-uri=https://auth.example.net/.well-known/jwks.json",
                        "iam.gateway.access-authentication.jwks-allowed-hosts[0]=auth.example.net")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AccessTokenJwkSetLoader.class);
                    assertThat(context).hasSingleBean(ReactiveSessionSnapshotReader.class);
                    assertThat(context).hasSingleBean(GatewayAccessAuthenticationFilter.class);
                });
    }

    @Test
    void rejectsUnsafeDefaultJwksEndpointAtStartup() {
        contextRunner
                .withBean(ReactiveStringRedisTemplate.class,
                        () -> mock(ReactiveStringRedisTemplate.class, RETURNS_DEEP_STUBS))
                .withPropertyValues(
                        "iam.gateway.access-authentication.enabled=true",
                        "iam.gateway.access-authentication.jwks-uri=http://auth.example.net/jwks.json",
                        "iam.gateway.access-authentication.jwks-allowed-hosts[0]=auth.example.net")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnsafeTtlConfiguration() {
        contextRunner
                .withBean(AccessTokenPublicKeyResolver.class,
                        () -> keyId -> Optional.empty())
                .withBean(ReactiveSessionSnapshotReader.class,
                        () -> (tenantId, subjectId, sessionId) -> Mono.empty())
                .withPropertyValues(
                        "iam.gateway.access-authentication.enabled=true",
                        "iam.gateway.access-authentication.maximum-ttl=6m")
                .run(context -> assertThat(context).hasFailed());
    }

    private static DownstreamRouteAudienceRegistry routeRegistry() {
        return new ConfiguredDownstreamRouteAudienceRegistry(
                Map.of("authorization-route", "iam-authorization-service"),
                Set.of("public-route"));
    }
}
