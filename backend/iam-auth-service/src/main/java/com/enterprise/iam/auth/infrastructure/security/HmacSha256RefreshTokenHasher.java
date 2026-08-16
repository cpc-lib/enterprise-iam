package com.enterprise.iam.auth.infrastructure.security;

import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;

import javax.crypto.Mac;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;

/** HMACs the ASCII opaque token; raw token bytes are cleared immediately. */
public final class HmacSha256RefreshTokenHasher {

    public static final int HASH_BYTES = 32;

    private final RefreshTokenHashKey key;

    public HmacSha256RefreshTokenHasher(RefreshTokenHashKey key) {
        this.key = Objects.requireNonNull(key, "key must not be null");
    }

    public byte[] hash(SensitiveRefreshToken token) {
        Objects.requireNonNull(token, "token must not be null");
        char[] characters = token.copyValue();
        byte[] bytes = null;
        try {
            bytes = new byte[characters.length];
            for (int index = 0; index < characters.length; index++) {
                char value = characters[index];
                if (value > 0x7f) {
                    throw new IllegalArgumentException("refresh token must be ASCII");
                }
                bytes[index] = (byte) value;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key.secretKey());
            byte[] result = mac.doFinal(bytes);
            if (result.length != HASH_BYTES) {
                Arrays.fill(result, (byte) 0);
                throw new IllegalStateException("refresh token hash length is invalid");
            }
            return result;
        } catch (GeneralSecurityException exception) {
            throw new RefreshTokenHashingException(exception);
        } finally {
            Arrays.fill(characters, '\0');
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }
}
