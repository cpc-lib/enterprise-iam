package com.enterprise.iam.auth.domain.exception;

/** Publicly indistinguishable current-session or current-credential rejection. */
public final class PasswordChangeRejectedException extends RuntimeException {

    public PasswordChangeRejectedException() {
        super("password change authentication rejected");
    }
}
