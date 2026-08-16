package com.enterprise.iam.gateway.security.jwks;

import java.net.InetAddress;
import java.util.List;

/** Resolves every configured JWKS fetch so all answers can be policy checked. */
@FunctionalInterface
public interface JwksDnsResolver {

    List<InetAddress> resolve(String hostname);
}
