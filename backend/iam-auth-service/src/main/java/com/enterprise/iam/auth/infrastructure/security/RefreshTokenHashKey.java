package com.enterprise.iam.auth.infrastructure.security;

import javax.crypto.SecretKey;
import java.util.Objects;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Current keyed-hash capability. The SecretKey may be non-exportable. */
public record RefreshTokenHashKey(String keyId, SecretKey secretKey) {

    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    public RefreshTokenHashKey {
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("refresh-token hash keyId is invalid");
        }
        Objects.requireNonNull(secretKey, "secretKey must not be null");
        if (!"HmacSHA256".equalsIgnoreCase(secretKey.getAlgorithm())) {
            throw new IllegalArgumentException("refresh-token hash key must use HmacSHA256");
        }
        byte[] encoded = secretKey.getEncoded();
        if (encoded != null) {
            try {
                if (encoded.length < 32) {
                    throw new IllegalArgumentException(
                            "exportable refresh-token hash key must contain at least 256 bits");
                }
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }
}
