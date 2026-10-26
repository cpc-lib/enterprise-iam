package com.enterprise.iam.gateway.interfaces.http;

import com.enterprise.iam.gateway.config.DelegationPublicJwk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Publishes the delegation-token verification key as a standard JWKS document.
 * Deliberately unauthenticated: the payload carries public key material only,
 * and consumers (the downstream IAM services) are non-browser clients, so the
 * body is raw JWKS JSON without any envelope.
 */
@RestController
@ConditionalOnProperty(
        prefix = "iam.gateway.delegation",
        name = "enabled",
        havingValue = "true")
public class GatewayDelegationJwksController {

    public static final String JWKS_PATH = "/internal/v1/gateway/delegation-jwks.json";

    private final DelegationPublicJwk delegationPublicJwk;

    public GatewayDelegationJwksController(DelegationPublicJwk delegationPublicJwk) {
        this.delegationPublicJwk = delegationPublicJwk;
    }

    @GetMapping(path = JWKS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> delegationJwks() {
        return Map.of("keys", List.of(delegationPublicJwk.jwk()));
    }
}
