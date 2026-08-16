package com.enterprise.iam.common.security.delegation;

import com.enterprise.iam.common.security.jwt.JwkSetKeyResolutionException;

/** @deprecated use the profile-neutral {@link JwkSetKeyResolutionException}. */
@Deprecated(forRemoval = false)
public final class DelegationKeyResolutionException extends JwkSetKeyResolutionException {

    public DelegationKeyResolutionException(String message) {
        super(message);
    }

    public DelegationKeyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
