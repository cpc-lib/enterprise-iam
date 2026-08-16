package com.enterprise.iam.common.security.access;

@FunctionalInterface
public interface AccessTokenSigner {

    /** Signing capability may be backed by an HSM/KMS; raw key export is not required. */
    SignedAccessToken sign(AccessTokenSigningRequest request);
}
