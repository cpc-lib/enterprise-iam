package com.enterprise.iam.common.security.delegation;

import com.enterprise.iam.common.security.jwt.Es256PublicKeyResolver;

@FunctionalInterface
public interface DelegationPublicKeyResolver extends Es256PublicKeyResolver {
}
