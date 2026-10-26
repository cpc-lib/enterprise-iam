package com.enterprise.iam.common.transaction;

/**
 * Thrown when Seata cross-boundary coordination fails during security-critical operations,
 * guaranteeing that the command fails closed and rolls back.
 */
public class SeataRevocationCoordinationException extends RuntimeException {

    public SeataRevocationCoordinationException(String message) {
        super(message);
    }

    public SeataRevocationCoordinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
