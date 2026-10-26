package com.enterprise.iam.security.delegation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathPatternDelegationRequestMatcherTest {

    @Test
    void matchesOnlyInternalApplicationPathsIncludingContextPath() {
        var matcher = new PathPatternDelegationRequestMatcher(
                List.of("/internal/v1/**", "/internal/system/ping"));
        MockHttpServletRequest protectedRequest =
                new MockHttpServletRequest("GET", "/iam/internal/v1/decision");
        protectedRequest.setContextPath("/iam");

        assertThat(matcher.matches(protectedRequest)).isTrue();
        assertThat(matcher.matches(
                new MockHttpServletRequest("GET", "/actuator/health"))).isFalse();
    }

    @Test
    void matchesAnExplicitApiApplicationPath() {
        var matcher = new PathPatternDelegationRequestMatcher(
                List.of("/api/v1/auth/password/change"));

        assertThat(matcher.matches(new MockHttpServletRequest(
                "POST", "/api/v1/auth/password/change"))).isTrue();
        assertThat(matcher.matches(new MockHttpServletRequest(
                "POST", "/api/v1/auth/refresh"))).isFalse();
    }

    @Test
    void rejectsBroadRootConfiguration() {
        assertThatThrownBy(() -> new PathPatternDelegationRequestMatcher(List.of("/**")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact /api paths");
    }

    @Test
    void rejectsWildcardApiConfiguration() {
        assertThatThrownBy(() -> new PathPatternDelegationRequestMatcher(List.of("/api/**")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact /api paths");
    }

    @Test
    void doesNotStripAnUnrelatedContextPathPrefix() {
        var matcher = new PathPatternDelegationRequestMatcher(List.of("/internal/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users");
        request.setContextPath("/iam");

        assertThat(matcher.matches(request)).isFalse();
    }
}
