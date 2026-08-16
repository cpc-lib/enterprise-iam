package com.enterprise.iam.security.delegation;

import com.enterprise.iam.common.security.delegation.DelegationTokenDecoder;
import com.enterprise.iam.common.security.delegation.DelegationValidationFailure;
import com.enterprise.iam.common.security.delegation.DelegationValidationResult;
import com.enterprise.iam.common.security.delegation.TrustedRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedDelegationFilterTest {

    @Test
    void rejectsMissingOrInvalidDelegationWithoutLeakingValidationReason() throws Exception {
        TrustedDelegationFilter filter = filter(token -> DelegationValidationResult.invalid(
                DelegationValidationFailure.INVALID_SIGNATURE));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/resource");
        request.addHeader("X-Request-Id", "request-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).contains("IAM_AUTHENTICATION_REQUIRED");
        assertThat(response.getContentAsString()).contains("request-0001");
        assertThat(response.getContentAsString()).doesNotContain("INVALID_SIGNATURE");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void publishesTrustedContextOnlyAfterDecoderSuccess() throws Exception {
        TrustedRequestContext context = new TrustedRequestContext(
                10, 20, 30, "token-0001", "request-0001", Instant.parse("2026-08-12T12:00:30Z"));
        TrustedDelegationFilter filter = filter(token -> DelegationValidationResult.valid(context));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/resource");
        request.addHeader(TrustedDelegationFilter.DELEGATION_HEADER, "signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(TrustedDelegationFilter.TRUSTED_CONTEXT_ATTRIBUTE))
                .isEqualTo(context);
    }

    @Test
    void reportsKeyResolutionOutageAsGenericServiceUnavailable() throws Exception {
        TrustedDelegationFilter filter = filter(token -> DelegationValidationResult.invalid(
                DelegationValidationFailure.KEY_RESOLUTION_UNAVAILABLE));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/resource");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .contains("IAM_AUTHENTICATION_DEPENDENCY_UNAVAILABLE")
                .doesNotContain("KEY_RESOLUTION_UNAVAILABLE");
    }

    @Test
    void doesNotRequireDelegationForExplicitlyUnprotectedPath() throws Exception {
        TrustedDelegationFilter filter = filter(token -> DelegationValidationResult.invalid(
                DelegationValidationFailure.MISSING_TOKEN));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private TrustedDelegationFilter filter(DelegationTokenDecoder decoder) {
        return new TrustedDelegationFilter(
                decoder,
                new PathPatternDelegationRequestMatcher(List.of("/internal/**")),
                request -> request.getHeader("X-Request-Id"));
    }
}
