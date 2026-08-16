package com.enterprise.iam.common.security.access;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Signed capability plus authoritative issuance timestamps; token text is redacted. */
public record SignedAccessToken(String compact, Instant issuedAt, Instant expiresAt) {

    private static final Pattern COMPACT_JWS = Pattern.compile(
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    public SignedAccessToken {
        Objects.requireNonNull(compact, "compact must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!COMPACT_JWS.matcher(compact).matches()
                || compact.length() > Es256AccessTokenDecoder.MAX_COMPACT_TOKEN_LENGTH) {
            throw new IllegalArgumentException("compact access token is invalid");
        }
        Duration lifetime = Duration.between(issuedAt, expiresAt);
        if (lifetime.isZero() || lifetime.isNegative()
                || lifetime.compareTo(Duration.ofMinutes(5)) > 0
                || lifetime.toNanosPart() != 0) {
            throw new IllegalArgumentException("signed access-token lifetime is invalid");
        }
    }

    public long expiresInSeconds() {
        return Duration.between(issuedAt, expiresAt).toSeconds();
    }

    @Override
    public String toString() {
        return "SignedAccessToken[compact=REDACTED, issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
