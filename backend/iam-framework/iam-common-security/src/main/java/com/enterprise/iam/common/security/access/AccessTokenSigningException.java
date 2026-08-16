package com.enterprise.iam.common.security.access;

public final class AccessTokenSigningException extends RuntimeException {

    public AccessTokenSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
