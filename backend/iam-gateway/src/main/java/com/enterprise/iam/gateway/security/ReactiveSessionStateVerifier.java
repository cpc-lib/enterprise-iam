package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.access.VerifiedAccessToken;
import reactor.core.publisher.Mono;

/** Deployment port for authoritative Redis/session-version validation. */
@FunctionalInterface
public interface ReactiveSessionStateVerifier {

    Mono<SessionStateVerification> verify(VerifiedAccessToken token);
}
