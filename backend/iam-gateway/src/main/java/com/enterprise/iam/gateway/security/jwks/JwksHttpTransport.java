package com.enterprise.iam.gateway.security.jwks;

import java.net.URI;
import java.time.Duration;

@FunctionalInterface
public interface JwksHttpTransport {

    JwksHttpResponse get(URI uri, Duration requestTimeout);
}
