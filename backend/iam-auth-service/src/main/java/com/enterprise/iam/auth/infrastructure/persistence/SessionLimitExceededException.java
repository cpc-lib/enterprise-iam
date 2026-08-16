package com.enterprise.iam.auth.infrastructure.persistence;

public final class SessionLimitExceededException extends SessionIssuanceException {

    SessionLimitExceededException() {
        super("maximum concurrent login sessions reached");
    }
}
