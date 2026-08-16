package com.enterprise.iam.auth.infrastructure.security;

import com.enterprise.iam.auth.application.port.out.PasswordVerifier;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Objects;

/** Frozen Argon2id parameters from docs/security/SECURITY-PARAMETERS.yaml. */
public final class Argon2idPasswordVerifier implements PasswordVerifier {

    public static final int SALT_LENGTH_BYTES = 16;
    public static final int HASH_LENGTH_BYTES = 32;
    public static final int PARALLELISM = 1;
    public static final int MEMORY_KIB = 19_456;
    public static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder;
    private final String dummyHash;

    public Argon2idPasswordVerifier() {
        this(new SecureRandom());
    }

    Argon2idPasswordVerifier(SecureRandom secureRandom) {
        Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.encoder = new Argon2PasswordEncoder(
                SALT_LENGTH_BYTES,
                HASH_LENGTH_BYTES,
                PARALLELISM,
                MEMORY_KIB,
                ITERATIONS);
        byte[] dummySecret = new byte[32];
        secureRandom.nextBytes(dummySecret);
        try {
            this.dummyHash = encoder.encode(Base64.getEncoder().encodeToString(dummySecret));
        } finally {
            java.util.Arrays.fill(dummySecret, (byte) 0);
        }
    }

    @Override
    public boolean verify(char[] rawPassword, String passwordPhc) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        if (passwordPhc == null || !passwordPhc.startsWith("$argon2id$")) {
            verifyAgainstDummy(rawPassword);
            return false;
        }
        try {
            return encoder.matches(normalize(rawPassword), passwordPhc);
        } catch (IllegalArgumentException exception) {
            verifyAgainstDummy(rawPassword);
            return false;
        }
    }

    @Override
    public boolean verifyAgainstDummy(char[] rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        return encoder.matches(normalize(rawPassword), dummyHash);
    }

    /** Used only when provisioning or rotating a credential, never returned by login. */
    public String encode(char[] rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        return encoder.encode(normalize(rawPassword));
    }

    private static String normalize(char[] rawPassword) {
        return Normalizer.normalize(CharBuffer.wrap(rawPassword), Normalizer.Form.NFC);
    }
}
