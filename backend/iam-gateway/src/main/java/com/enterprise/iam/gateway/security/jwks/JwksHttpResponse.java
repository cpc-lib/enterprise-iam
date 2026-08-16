package com.enterprise.iam.gateway.security.jwks;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Streaming response abstraction keeps the maximum body bound enforceable. */
public record JwksHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        InputStream body) implements AutoCloseable {

    public JwksHttpResponse {
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
        body = Objects.requireNonNull(body, "body must not be null");
    }

    public List<String> headerValues(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException exception) {
            throw new JwksTransportException("JWKS response close failed", exception);
        }
    }
}
