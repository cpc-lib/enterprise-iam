package com.enterprise.iam.gateway.config;

import java.util.Map;

/** Public JWK of the delegation-token signing key, published by the gateway JWKS endpoint. */
public record DelegationPublicJwk(Map<String, Object> jwk) {

    public DelegationPublicJwk {
        if (jwk == null || jwk.isEmpty()) {
            throw new IllegalArgumentException("delegation public JWK must not be empty");
        }
        jwk = Map.copyOf(jwk);
    }
}
