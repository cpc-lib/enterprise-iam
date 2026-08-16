package com.enterprise.iam.auth.infrastructure.security;

public final class RefreshTokenHashingException extends RuntimeException {

    RefreshTokenHashingException(Throwable cause) {
        super("refresh token keyed hashing failed", cause);
    }
}
