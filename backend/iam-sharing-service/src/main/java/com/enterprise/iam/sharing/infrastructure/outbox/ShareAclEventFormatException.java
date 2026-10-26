package com.enterprise.iam.sharing.infrastructure.outbox;

/** Malformed share ACL event payload; never retryable for the relay. */
public final class ShareAclEventFormatException extends RuntimeException {

    public ShareAclEventFormatException(String message) {
        super(message);
    }

    public ShareAclEventFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
