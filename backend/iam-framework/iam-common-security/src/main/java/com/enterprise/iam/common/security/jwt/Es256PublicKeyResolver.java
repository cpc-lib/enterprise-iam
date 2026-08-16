package com.enterprise.iam.common.security.jwt;

import java.security.interfaces.ECPublicKey;
import java.util.Optional;

@FunctionalInterface
public interface Es256PublicKeyResolver {

    Optional<ECPublicKey> resolve(String keyId);
}
