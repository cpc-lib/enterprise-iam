package com.enterprise.iam.auth.infrastructure.security;

import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenSecurityTest {

    private static final byte[] KEY_BYTES = new byte[32];

    @Test
    void emitsVersionedKeyIdAndFull256BitRandomBody() {
        RefreshTokenHashKey key = key();
        SecureRandom deterministic = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 0x5a);
            }
        };
        SecureOpaqueRefreshTokenGenerator generator =
                new SecureOpaqueRefreshTokenGenerator(deterministic, key);

        try (SensitiveRefreshToken token = generator.generate()) {
            char[] value = token.copyValue();
            assertThat(new String(value)).startsWith("rt1.refresh-key-1.");
            assertThat(value.length).isEqualTo("rt1.refresh-key-1.".length() + 43);
            Arrays.fill(value, '\0');
        }
    }

    @Test
    void hmacIsDeterministicKeyedAndExactly32Bytes() {
        HmacSha256RefreshTokenHasher hasher = new HmacSha256RefreshTokenHasher(key());
        try (SensitiveRefreshToken token = new SensitiveRefreshToken(
                "rt1.refresh-key-1.0123456789012345678901234567890123456789012"
                        .toCharArray())) {
            byte[] first = hasher.hash(token);
            byte[] second = hasher.hash(token);
            assertThat(first).hasSize(32).containsExactly(second);
            assertThat(first).isNotEqualTo(Arrays.copyOf(
                    "rt1.refresh-key-1.0123456789012345678901234567890123456789012"
                            .getBytes(StandardCharsets.US_ASCII),
                    32));
            Arrays.fill(first, (byte) 0);
            Arrays.fill(second, (byte) 0);
        }
    }

    @Test
    void rejectsWrongKeyAlgorithmAndInvalidKeyId() {
        assertThatThrownBy(() -> new RefreshTokenHashKey(
                "key", new SecretKeySpec(KEY_BYTES, "AES")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenHashKey(
                "bad key", new SecretKeySpec(KEY_BYTES, "HmacSHA256")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenHashKey(
                "short-key", new SecretKeySpec(new byte[16], "HmacSHA256")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RefreshTokenHashKey key() {
        byte[] bytes = KEY_BYTES.clone();
        Arrays.fill(bytes, (byte) 0x33);
        return new RefreshTokenHashKey(
                "refresh-key-1", new SecretKeySpec(bytes, "HmacSHA256"));
    }
}
