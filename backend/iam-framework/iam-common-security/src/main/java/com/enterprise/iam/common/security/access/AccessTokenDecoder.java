package com.enterprise.iam.common.security.access;

@FunctionalInterface
public interface AccessTokenDecoder {

    AccessTokenValidationResult decode(String compactToken);
}
