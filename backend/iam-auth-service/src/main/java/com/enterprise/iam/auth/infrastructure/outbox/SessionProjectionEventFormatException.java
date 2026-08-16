package com.enterprise.iam.auth.infrastructure.outbox;

/** Does not expose the untrusted payload in logs or relay status. */
public final class SessionProjectionEventFormatException extends RuntimeException {

    public SessionProjectionEventFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
