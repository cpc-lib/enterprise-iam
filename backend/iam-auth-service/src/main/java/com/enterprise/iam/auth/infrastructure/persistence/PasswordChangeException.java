package com.enterprise.iam.auth.infrastructure.persistence;

public final class PasswordChangeException extends RuntimeException {

    public PasswordChangeException(String message) {
        super(message);
    }

    public PasswordChangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
