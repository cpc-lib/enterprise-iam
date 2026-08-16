package com.enterprise.iam.gateway.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalIdentityHeaderSanitizingFilterTest {

    private final ExternalIdentityHeaderSanitizingFilter filter =
            new ExternalIdentityHeaderSanitizingFilter();

    @Test
    @DisplayName("SEC-TEN-001 external identity and delegation headers are stripped")
    void stripsSpoofableIdentityAndInternalHeadersCaseInsensitively() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/protected")
                .header("x-tenant-id", "victim")
                .header("X-User-Id", "999")
                .header("X-Subject-Id", "999")
                .header("X-Session-Id", "999")
                .header("X-Resource-Id", "999")
                .header("X-IAM-Delegation", "forged")
                .header("X-Service-Token", "forged")
                .header(HttpHeaders.AUTHORIZATION, "Bearer external-user-token")
                .header("X-Request-Id", "request-0001")
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };

        filter.filter(MockServerWebExchange.from(request), chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.containsKey("X-Tenant-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-Subject-Id")).isFalse();
        assertThat(headers.containsKey("X-Session-Id")).isFalse();
        assertThat(headers.containsKey("X-Resource-Id")).isFalse();
        assertThat(headers.containsKey("X-IAM-Delegation")).isFalse();
        assertThat(headers.containsKey("X-Service-Token")).isFalse();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer external-user-token");
        assertThat(headers.getFirst("X-Request-Id")).isEqualTo("request-0001");
    }

    @Test
    void runsBeforeAuthenticationAndRoutingFilters() {
        assertThat(filter.getOrder()).isEqualTo(Integer.MIN_VALUE);
    }
}
