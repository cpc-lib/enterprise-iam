package com.enterprise.iam.auth.application.port.out;

import javax.crypto.SecretKey;
import java.security.interfaces.ECPrivateKey;

/**
 * Outbound port supplying session-issuance key material. The local JCE
 * reference adapter loads the ES256 private key and refresh-token HMAC secret
 * from configured PEM/secret values. Adapters backed by a KMS/HSM with
 * exportable key material implement this port; non-exportable KMS/HSM
 * integrations instead override the {@code AccessTokenSigner},
 * {@code AccessTokenPublicJwk} and {@code RefreshTokenHashKey} beans directly
 * (all are registered with {@code @ConditionalOnMissingBean}), so raw key
 * export is never required by the wiring.
 */
public interface CryptoKeyProvider {

    /** ES256 (P-256) private key used to sign access tokens. */
    ECPrivateKey accessTokenPrivateKey();

    /** HmacSHA256 key used to hash refresh tokens; may be non-exportable. */
    SecretKey refreshTokenHmacKey();
}
