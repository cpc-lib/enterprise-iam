package com.enterprise.iam.auth.domain.exception;

/** The proposed password violates the frozen password policy. */
public final class PasswordPolicyViolationException extends RuntimeException {

    public PasswordPolicyViolationException() {
        super("new password violates password policy");
    }
}
