package com.enterprise.iam.common.security.jwt;

public class JwkSetKeyResolutionException extends RuntimeException {

    public JwkSetKeyResolutionException(String message) {
        super(message);
    }

    public JwkSetKeyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
