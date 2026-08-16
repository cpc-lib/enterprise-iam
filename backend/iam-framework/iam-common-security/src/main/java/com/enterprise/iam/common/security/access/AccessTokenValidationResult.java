package com.enterprise.iam.common.security.access;

import java.util.Objects;
import java.util.Optional;

public record AccessTokenValidationResult(
        VerifiedAccessToken token,
        AccessTokenValidationFailure failure) {

    public AccessTokenValidationResult {
        if ((token == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of token or failure is required");
        }
    }

    public static AccessTokenValidationResult valid(VerifiedAccessToken token) {
        return new AccessTokenValidationResult(Objects.requireNonNull(token), null);
    }

    public static AccessTokenValidationResult invalid(AccessTokenValidationFailure failure) {
        return new AccessTokenValidationResult(null, Objects.requireNonNull(failure));
    }

    public boolean isValid() {
        return token != null;
    }

    public Optional<VerifiedAccessToken> verifiedToken() {
        return Optional.ofNullable(token);
    }
}
