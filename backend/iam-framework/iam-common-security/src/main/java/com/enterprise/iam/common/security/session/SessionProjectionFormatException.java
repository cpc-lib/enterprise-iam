package com.enterprise.iam.common.security.session;

/** Signals a present but malformed or unsupported Redis security projection. */
public final class SessionProjectionFormatException extends RuntimeException {

    public SessionProjectionFormatException(String message) {
        super(message);
    }

    public SessionProjectionFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
