package com.enterprise.iam.auth.infrastructure.security;

import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Generates rt1.key-id.random using 256 random bits and base64url without padding. */
public final class SecureOpaqueRefreshTokenGenerator {

    public static final int RANDOM_BYTES = 32;

    private final SecureRandom secureRandom;
    private final String prefix;

    public SecureOpaqueRefreshTokenGenerator(
            SecureRandom secureRandom,
            RefreshTokenHashKey hashKey) {
        this.secureRandom = Objects.requireNonNull(
                secureRandom, "secureRandom must not be null");
        this.prefix = "rt1." + Objects.requireNonNull(
                hashKey, "hashKey must not be null").keyId() + ".";
    }

    public SensitiveRefreshToken generate() {
        byte[] random = new byte[RANDOM_BYTES];
        byte[] encoded = null;
        char[] token = null;
        try {
            secureRandom.nextBytes(random);
            encoded = Base64.getUrlEncoder().withoutPadding().encode(random);
            token = new char[prefix.length() + encoded.length];
            prefix.getChars(0, prefix.length(), token, 0);
            for (int index = 0; index < encoded.length; index++) {
                token[prefix.length() + index] = (char) (encoded[index] & 0xff);
            }
            return new SensitiveRefreshToken(token);
        } finally {
            Arrays.fill(random, (byte) 0);
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
            if (token != null) {
                Arrays.fill(token, '\0');
            }
        }
    }
}
