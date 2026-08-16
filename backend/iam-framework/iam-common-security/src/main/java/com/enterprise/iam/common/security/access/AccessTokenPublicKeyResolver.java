package com.enterprise.iam.common.security.access;

import com.enterprise.iam.common.security.jwt.Es256PublicKeyResolver;

@FunctionalInterface
public interface AccessTokenPublicKeyResolver extends Es256PublicKeyResolver {
}
