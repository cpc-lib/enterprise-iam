package com.enterprise.iam.common.security.delegation;

import com.enterprise.iam.common.security.jwt.JwkSetLoader;

/** Loads the current public JWKS document from a trusted, deployment-owned source. */
@FunctionalInterface
public interface DelegationJwkSetLoader extends JwkSetLoader {
}
