package com.enterprise.iam.gateway.config;

import com.enterprise.iam.common.security.crypto.PemECKeyLoader;
import com.enterprise.iam.common.security.delegation.Es256DelegationTokenSigner;
import com.enterprise.iam.gateway.delegation.ConfiguredDownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.DownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.Es256GatewayDelegationTokenIssuer;
import com.enterprise.iam.gateway.delegation.GatewayDelegationFilter;
import com.enterprise.iam.gateway.delegation.GatewayDelegationTokenIssuer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Fail-safe production assembly of gateway delegation signing: missing or
 * invalid key material fails startup with an actionable
 * {@link IllegalStateException}; keys are never generated silently.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "iam.gateway.delegation",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(GatewayDelegationProperties.class)
public class GatewayDelegationConfiguration {

    @Bean("iamGatewayDelegationClock")
    @ConditionalOnMissingBean(name = "iamGatewayDelegationClock")
    Clock iamGatewayDelegationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(DownstreamRouteAudienceRegistry.class)
    DownstreamRouteAudienceRegistry downstreamRouteAudienceRegistry(
            GatewayDelegationProperties properties) {
        return new ConfiguredDownstreamRouteAudienceRegistry(
                properties.getRouteAudiences(), properties.getPublicRoutes());
    }

    @Bean
    @ConditionalOnMissingBean(Es256DelegationTokenSigner.class)
    Es256DelegationTokenSigner es256DelegationTokenSigner(
            GatewayDelegationProperties properties,
            @Qualifier("iamGatewayDelegationClock") Clock clock) {
        return new Es256DelegationTokenSigner(
                requireDelegationPrivateKey(properties),
                requireDelegationKeyId(properties),
                properties.getDelegationIssuer(),
                clock,
                requireDelegationTokenTtl(properties),
                () -> UUID.randomUUID().toString());
    }

    @Bean
    @ConditionalOnMissingBean(DelegationPublicJwk.class)
    DelegationPublicJwk delegationPublicJwk(GatewayDelegationProperties properties) {
        ECPrivateKey privateKey = requireDelegationPrivateKey(properties);
        return new DelegationPublicJwk(PemECKeyLoader.exportPublicJwk(
                PemECKeyLoader.derivePublicKey(privateKey),
                requireDelegationKeyId(properties)));
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

    private static ECPrivateKey requireDelegationPrivateKey(GatewayDelegationProperties properties) {
        String pem = properties.getPrivateKeyPem();
        String path = properties.getPrivateKeyPath();
        if ((pem == null || pem.isBlank()) && (path == null || path.isBlank())) {
            throw new IllegalStateException(
                    "Delegation signing key is required when gateway delegation is enabled: set "
                            + "iam.gateway.delegation.private-key-pem (env "
                            + "IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PEM) or "
                            + "iam.gateway.delegation.private-key-path (env "
                            + "IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PATH) to a PKCS#8 EC P-256 PEM");
        }
        if (pem != null && !pem.isBlank() && path != null && !path.isBlank()) {
            throw new IllegalStateException(
                    "Set only one of iam.gateway.delegation.private-key-pem "
                            + "(IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PEM) and "
                            + "iam.gateway.delegation.private-key-path "
                            + "(IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PATH)");
        }
        try {
            return pem != null && !pem.isBlank()
                    ? PemECKeyLoader.parseP256PrivateKey(pem)
                    : PemECKeyLoader.parseP256PrivateKey(Path.of(path.trim()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "iam.gateway.delegation private key is invalid (expect a PKCS#8 EC P-256 PEM via "
                            + "IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PEM or "
                            + "IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PATH): " + exception.getMessage(),
                    exception);
        }
    }

    private static String requireDelegationKeyId(GatewayDelegationProperties properties) {
        String keyId = properties.getDelegationKeyId();
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException(
                    "iam.gateway.delegation.delegation-key-id is required when gateway delegation is enabled "
                            + "(env IAM_GATEWAY_DELEGATION_KEY_ID)");
        }
        return keyId.trim();
    }

    private static Duration requireDelegationTokenTtl(GatewayDelegationProperties properties) {
        Duration ttl = properties.getDelegationTokenTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()
                || ttl.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException(
                    "iam.gateway.delegation.delegation-token-ttl must be within 1-30 seconds");
        }
        return ttl;
    }
}
