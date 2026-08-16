package com.enterprise.iam.auth.infrastructure.persistence;

/** Durable refresh-rotation failure; the public outcome stays uniform. */
public class RefreshRotationException extends SessionIssuanceException {

    public RefreshRotationException(String message) {
        super(message);
    }
}
