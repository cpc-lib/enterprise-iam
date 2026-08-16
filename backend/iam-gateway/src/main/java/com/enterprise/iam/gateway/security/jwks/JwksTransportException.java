package com.enterprise.iam.gateway.security.jwks;

/** Fail-closed JWKS DNS, HTTP, TLS or response-policy failure. */
public final class JwksTransportException extends RuntimeException {

    public JwksTransportException(String message) {
        super(message);
    }

    public JwksTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
