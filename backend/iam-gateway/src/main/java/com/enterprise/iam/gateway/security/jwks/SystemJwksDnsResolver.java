package com.enterprise.iam.gateway.security.jwks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public final class SystemJwksDnsResolver implements JwksDnsResolver {

    @Override
    public List<InetAddress> resolve(String hostname) {
        try {
            return List.of(InetAddress.getAllByName(hostname));
        } catch (UnknownHostException exception) {
            throw new JwksTransportException("JWKS hostname resolution failed", exception);
        }
    }
}
