package com.enterprise.iam.auth.infrastructure.config;

import java.util.Map;

/** Public JWK of the access-token signing key, published by the JWKS endpoint. */
public record AccessTokenPublicJwk(Map<String, Object> jwk) {

    public AccessTokenPublicJwk {
        if (jwk == null || jwk.isEmpty()) {
            throw new IllegalArgumentException("access-token public JWK must not be empty");
        }
        jwk = Map.copyOf(jwk);
    }
}
