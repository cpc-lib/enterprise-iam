package com.enterprise.iam.auth.infrastructure.config;

import com.enterprise.iam.auth.application.port.out.CryptoKeyProvider;
import com.enterprise.iam.auth.infrastructure.security.LocalJceCryptoKeyProvider;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.Es256AccessTokenSigner;
import com.enterprise.iam.common.security.crypto.PemECKeyLoader;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/**
 * Production assembly of the crypto beans required by
 * {@code SessionIssuanceConfiguration}. Key material comes from a
 * {@link CryptoKeyProvider}; the default {@code iam.auth.crypto.provider=local}
 * wires the local JCE reference provider, which fails startup with an
 * actionable {@link IllegalStateException} on missing or invalid key material
 * and never generates keys silently. KMS/HSM integrations override the
 * provider bean (exportable keys) or the signer/JWK/hash-key beans directly
 * (non-exportable keys). {@code iam.auth.session-issuance.enabled} gates the
 * whole configuration so tests can override every bean.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CryptoKeyProperties.class)
@ConditionalOnProperty(
        prefix = "iam.auth.session-issuance",
        name = "enabled",
        havingValue = "true")
public class CryptoKeyConfiguration {

    @Bean("iamCryptoKeyClock")
    @ConditionalOnMissingBean(name = "iamCryptoKeyClock")
    Clock iamCryptoKeyClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(CryptoKeyProvider.class)
    @ConditionalOnProperty(
            prefix = "iam.auth.crypto",
            name = "provider",
            havingValue = "local",
            matchIfMissing = true)
    CryptoKeyProvider localJceCryptoKeyProvider(CryptoKeyProperties properties) {
        return new LocalJceCryptoKeyProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AccessTokenSigner.class)
    AccessTokenSigner accessTokenSigner(
            CryptoKeyProvider keyProvider,
            CryptoKeyProperties properties,
            @Qualifier("iamCryptoKeyClock") Clock clock) {
        return new Es256AccessTokenSigner(
                keyProvider.accessTokenPrivateKey(),
                requireAccessTokenKid(properties),
                properties.getAccessTokenIssuer(),
                properties.getAccessTokenAudience(),
                clock,
                properties.getAccessTokenTtl(),
                () -> UUID.randomUUID().toString());
    }

    @Bean
    @ConditionalOnMissingBean(AccessTokenPublicJwk.class)
    AccessTokenPublicJwk accessTokenPublicJwk(
            CryptoKeyProvider keyProvider,
            CryptoKeyProperties properties) {
        return new AccessTokenPublicJwk(PemECKeyLoader.exportPublicJwk(
                PemECKeyLoader.derivePublicKey(keyProvider.accessTokenPrivateKey()),
                requireAccessTokenKid(properties)));
    }

    @Bean
    @ConditionalOnMissingBean(RefreshTokenHashKey.class)
    RefreshTokenHashKey refreshTokenHashKey(
            CryptoKeyProvider keyProvider,
            CryptoKeyProperties properties) {
        String keyId = properties.getRefreshTokenHmacKeyId();
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException(
                    "iam.auth.crypto.refresh-token-hmac-key-id must not be blank");
        }
        return new RefreshTokenHashKey(keyId, keyProvider.refreshTokenHmacKey());
    }

    private static String requireAccessTokenKid(CryptoKeyProperties properties) {
        String kid = properties.getAccessTokenKid();
        if (kid == null || kid.isBlank()) {
            throw new IllegalStateException(
                    "iam.auth.crypto.access-token-kid is required when session issuance is enabled "
                            + "(env IAM_AUTH_CRYPTO_ACCESS_TOKEN_KID)");
        }
        return kid.trim();
    }
}
