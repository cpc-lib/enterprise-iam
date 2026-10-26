package com.enterprise.iam.auth.infrastructure.security;

import com.enterprise.iam.auth.application.port.out.CryptoKeyProvider;
import com.enterprise.iam.auth.infrastructure.config.CryptoKeyProperties;
import com.enterprise.iam.common.security.crypto.PemECKeyLoader;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.util.Base64;
import java.util.Objects;

/**
 * Local JCE reference {@link CryptoKeyProvider}: loads the ES256 private key
 * from a configured PKCS#8 PEM (inline or file) and the refresh-token HMAC
 * secret from configuration. Loading is eager and fail-safe: missing or
 * invalid key material fails bean creation with an actionable
 * {@link IllegalStateException}; keys are never generated silently.
 */
public final class LocalJceCryptoKeyProvider implements CryptoKeyProvider {

    private final ECPrivateKey accessTokenPrivateKey;
    private final SecretKey refreshTokenHmacKey;

    public LocalJceCryptoKeyProvider(CryptoKeyProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.accessTokenPrivateKey = loadAccessTokenPrivateKey(properties);
        this.refreshTokenHmacKey = loadRefreshTokenHmacKey(properties);
    }

    @Override
    public ECPrivateKey accessTokenPrivateKey() {
        return accessTokenPrivateKey;
    }

    @Override
    public SecretKey refreshTokenHmacKey() {
        return refreshTokenHmacKey;
    }

    private static ECPrivateKey loadAccessTokenPrivateKey(CryptoKeyProperties properties) {
        String pem = properties.getAccessTokenPrivateKeyPem();
        String path = properties.getAccessTokenPrivateKeyPath();
        if ((pem == null || pem.isBlank()) && (path == null || path.isBlank())) {
            throw new IllegalStateException(
                    "Access-token signing key is required when session issuance is enabled: set "
                            + "iam.auth.crypto.access-token-private-key-pem (env "
                            + "IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PEM) or "
                            + "iam.auth.crypto.access-token-private-key-path (env "
                            + "IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PATH) to a PKCS#8 EC P-256 PEM");
        }
        if (pem != null && !pem.isBlank() && path != null && !path.isBlank()) {
            throw new IllegalStateException(
                    "Set only one of iam.auth.crypto.access-token-private-key-pem "
                            + "(IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PEM) and "
                            + "iam.auth.crypto.access-token-private-key-path "
                            + "(IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PATH)");
        }
        try {
            return pem != null && !pem.isBlank()
                    ? PemECKeyLoader.parseP256PrivateKey(pem)
                    : PemECKeyLoader.parseP256PrivateKey(java.nio.file.Path.of(path.trim()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "iam.auth.crypto access-token private key is invalid (expect a PKCS#8 EC P-256 PEM "
                            + "via IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PEM or "
                            + "IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PATH): " + exception.getMessage(),
                    exception);
        }
    }

    private static SecretKey loadRefreshTokenHmacKey(CryptoKeyProperties properties) {
        String secret = properties.getRefreshTokenHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "iam.auth.crypto.refresh-token-hmac-secret is required when session issuance is enabled "
                            + "(env IAM_AUTH_CRYPTO_REFRESH_TOKEN_HMAC_SECRET); provide at least 32 bytes, "
                            + "base64 or plain text");
        }
        return new SecretKeySpec(decodeHmacSecret(secret), "HmacSHA256");
    }

    private static byte[] decodeHmacSecret(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            return requireAtLeast32Bytes(raw, "plain");
        }
        return decoded.length >= 32 ? decoded : requireAtLeast32Bytes(raw, "plain");
    }

    private static byte[] requireAtLeast32Bytes(byte[] raw, String format) {
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "iam.auth.crypto.refresh-token-hmac-secret (env "
                            + "IAM_AUTH_CRYPTO_REFRESH_TOKEN_HMAC_SECRET) must contain at least 32 bytes "
                            + "of key material (base64 or " + format + " text)");
        }
        return raw;
    }
}
