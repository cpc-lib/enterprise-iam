package com.enterprise.iam.common.security.delegation;

import java.util.Objects;
import java.util.Optional;

public record DelegationValidationResult(
        TrustedRequestContext context,
        DelegationValidationFailure failure) {

    public DelegationValidationResult {
        if ((context == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of context or failure is required");
        }
    }

    public static DelegationValidationResult valid(TrustedRequestContext context) {
        return new DelegationValidationResult(Objects.requireNonNull(context), null);
    }

    public static DelegationValidationResult invalid(DelegationValidationFailure failure) {
        return new DelegationValidationResult(null, Objects.requireNonNull(failure));
    }

    public boolean isValid() {
        return context != null;
    }

    public Optional<TrustedRequestContext> trustedContext() {
        return Optional.ofNullable(context);
    }
}
