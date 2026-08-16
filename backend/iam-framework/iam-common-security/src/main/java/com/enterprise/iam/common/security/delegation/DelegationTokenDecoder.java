package com.enterprise.iam.common.security.delegation;

@FunctionalInterface
public interface DelegationTokenDecoder {

    DelegationValidationResult decode(String compactToken);
}
