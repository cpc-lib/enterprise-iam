package com.enterprise.iam.common.security.delegation;

import com.enterprise.iam.common.security.jwt.BoundedRefreshingJwkSetPublicKeyResolver;

import java.time.Clock;
import java.time.Duration;

/** Delegation-profile type binding for the shared bounded JWKS resolver. */
public final class RefreshingJwkSetDelegationPublicKeyResolver
        extends BoundedRefreshingJwkSetPublicKeyResolver
        implements DelegationPublicKeyResolver {

    public RefreshingJwkSetDelegationPublicKeyResolver(
            DelegationJwkSetLoader loader,
            Clock clock,
            Duration cacheTtl,
            Duration unknownKeyTtl) {
        super(loader, clock, cacheTtl, unknownKeyTtl);
    }

    public RefreshingJwkSetDelegationPublicKeyResolver(
            DelegationJwkSetLoader loader,
            Clock clock,
            Duration cacheTtl,
            Duration unknownKeyTtl,
            Duration unknownRefreshMinimumInterval) {
        super(loader, clock, cacheTtl, unknownKeyTtl, unknownRefreshMinimumInterval);
    }
}
