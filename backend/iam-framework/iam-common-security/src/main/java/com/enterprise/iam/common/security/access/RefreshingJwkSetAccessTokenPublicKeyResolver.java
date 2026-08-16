package com.enterprise.iam.common.security.access;

import com.enterprise.iam.common.security.jwt.BoundedRefreshingJwkSetPublicKeyResolver;

import java.time.Clock;
import java.time.Duration;

/** Access-token profile type binding for the shared bounded JWKS resolver. */
public final class RefreshingJwkSetAccessTokenPublicKeyResolver
        extends BoundedRefreshingJwkSetPublicKeyResolver
        implements AccessTokenPublicKeyResolver {

    public RefreshingJwkSetAccessTokenPublicKeyResolver(
            AccessTokenJwkSetLoader loader,
            Clock clock,
            Duration cacheTtl,
            Duration unknownKeyTtl,
            Duration unknownRefreshMinimumInterval) {
        super(loader, clock, cacheTtl, unknownKeyTtl, unknownRefreshMinimumInterval);
    }
}
