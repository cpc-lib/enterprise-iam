package com.enterprise.iam.common.security.delegation;

public final class DelegationTokenSigningException extends RuntimeException {

    public DelegationTokenSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
