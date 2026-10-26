package com.enterprise.iam.authorization.application.service;

public final class DomainInvariantException extends RuntimeException {

    public DomainInvariantException(String message) {
        super(message);
    }
}
