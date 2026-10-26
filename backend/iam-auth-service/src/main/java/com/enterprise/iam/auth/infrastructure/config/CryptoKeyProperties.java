package com.enterprise.iam.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Key material for the session-issuance crypto beans. Values are injected from
 * the environment (IAM_AUTH_CRYPTO_*); the fail-safe posture is enforced by
 * {@link CryptoKeyConfiguration}, never by silently generating keys.
 */
@ConfigurationProperties("iam.auth.crypto")
public final class CryptoKeyProperties {

    /** Key-material source; only {@code local} (JCE, configured PEM/secret) is built in. */
    private String provider = "local";
    private String accessTokenPrivateKeyPem;
    private String accessTokenPrivateKeyPath;
    private String accessTokenKid;
    private String accessTokenIssuer = "iam-auth-service";
    private String accessTokenAudience = "iam-gateway";
    private Duration accessTokenTtl = Duration.ofMinutes(5);
    private String refreshTokenHmacSecret;
    private String refreshTokenHmacKeyId = "rt-1";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAccessTokenPrivateKeyPem() {
        return accessTokenPrivateKeyPem;
    }

    public void setAccessTokenPrivateKeyPem(String accessTokenPrivateKeyPem) {
        this.accessTokenPrivateKeyPem = accessTokenPrivateKeyPem;
    }

    public String getAccessTokenPrivateKeyPath() {
        return accessTokenPrivateKeyPath;
    }

    public void setAccessTokenPrivateKeyPath(String accessTokenPrivateKeyPath) {
        this.accessTokenPrivateKeyPath = accessTokenPrivateKeyPath;
    }

    public String getAccessTokenKid() {
        return accessTokenKid;
    }

    public void setAccessTokenKid(String accessTokenKid) {
        this.accessTokenKid = accessTokenKid;
    }

    public String getAccessTokenIssuer() {
        return accessTokenIssuer;
    }

    public void setAccessTokenIssuer(String accessTokenIssuer) {
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public String getAccessTokenAudience() {
        return accessTokenAudience;
    }

    public void setAccessTokenAudience(String accessTokenAudience) {
        this.accessTokenAudience = accessTokenAudience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public String getRefreshTokenHmacSecret() {
        return refreshTokenHmacSecret;
    }

    public void setRefreshTokenHmacSecret(String refreshTokenHmacSecret) {
        this.refreshTokenHmacSecret = refreshTokenHmacSecret;
    }

    public String getRefreshTokenHmacKeyId() {
        return refreshTokenHmacKeyId;
    }

    public void setRefreshTokenHmacKeyId(String refreshTokenHmacKeyId) {
        this.refreshTokenHmacKeyId = refreshTokenHmacKeyId;
    }
}
