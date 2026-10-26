package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.infrastructure.config.AccessTokenPublicJwk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Publishes the access-token verification key as a standard JWKS document.
 * Deliberately unauthenticated: the payload carries public key material only,
 * and consumers (such as the gateway) are non-IAM clients, so the body is raw
 * JWKS JSON rather than the service's {@code ApiResponse} envelope.
 */
@RestController
@ConditionalOnProperty(
        prefix = "iam.auth.session-issuance",
        name = "enabled",
        havingValue = "true")
public class AccessTokenJwksController {

    public static final String JWKS_PATH = "/internal/v1/crypto/jwks.json";

    private final AccessTokenPublicJwk accessTokenPublicJwk;

    public AccessTokenJwksController(AccessTokenPublicJwk accessTokenPublicJwk) {
        this.accessTokenPublicJwk = accessTokenPublicJwk;
    }

    @GetMapping(path = JWKS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(accessTokenPublicJwk.jwk()));
    }
}
