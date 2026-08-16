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
    void rejectsConfigurationOutsideInternalNamespace() {
        assertThatThrownBy(() -> new PathPatternDelegationRequestMatcher(List.of("/**")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below /internal");
    }

    @Test
    void doesNotStripAnUnrelatedContextPathPrefix() {
        var matcher = new PathPatternDelegationRequestMatcher(List.of("/internal/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users");
        request.setContextPath("/iam");

        assertThat(matcher.matches(request)).isFalse();
    }
}
