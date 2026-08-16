package com.enterprise.iam.auth.infrastructure.redis;

/** Redis projection corruption or an indeterminate publication result. */
public final class SessionProjectionPublicationException extends RuntimeException {

    public SessionProjectionPublicationException(String message) {
        super(message);
    }
}
