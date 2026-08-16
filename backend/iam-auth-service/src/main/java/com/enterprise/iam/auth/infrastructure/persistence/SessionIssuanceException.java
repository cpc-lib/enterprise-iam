package com.enterprise.iam.auth.infrastructure.persistence;

public class SessionIssuanceException extends RuntimeException {

    public SessionIssuanceException(String message) {
        super(message);
    }

    public SessionIssuanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
