package com.enterprise.iam.auth.infrastructure.outbox;

final class LoginAuditEventFormatException extends RuntimeException {

    LoginAuditEventFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
