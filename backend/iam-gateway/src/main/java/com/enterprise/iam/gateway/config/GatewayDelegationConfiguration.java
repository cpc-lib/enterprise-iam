package com.enterprise.iam.gateway.config;

import com.enterprise.iam.common.security.delegation.Es256DelegationTokenSigner;
import com.enterprise.iam.gateway.delegation.ConfiguredDownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.DownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.Es256GatewayDelegationTokenIssuer;
import com.enterprise.iam.gateway.delegation.GatewayDelegationFilter;
import com.enterprise.iam.gateway.delegation.GatewayDelegationTokenIssuer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "iam.gateway.delegation",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(GatewayDelegationProperties.class)
public class GatewayDelegationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DownstreamRouteAudienceRegistry downstreamRouteAudienceRegistry(
            GatewayDelegationProperties properties) {
        return new ConfiguredDownstreamRouteAudienceRegistry(
                properties.getRouteAudiences(), properties.getPublicRoutes());
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayDelegationTokenIssuer gatewayDelegationTokenIssuer(
            Es256DelegationTokenSigner signer) {
        return new Es256GatewayDelegationTokenIssuer(signer);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayDelegationFilter gatewayDelegationFilter(
            DownstreamRouteAudienceRegistry audienceRegistry,
            GatewayDelegationTokenIssuer tokenIssuer) {
        return new GatewayDelegationFilter(audienceRegistry, tokenIssuer);
    }
}
