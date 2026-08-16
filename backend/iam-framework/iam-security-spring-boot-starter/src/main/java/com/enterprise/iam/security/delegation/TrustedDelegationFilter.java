package com.enterprise.iam.security.delegation;

import com.enterprise.iam.common.security.delegation.DelegationTokenDecoder;
import com.enterprise.iam.common.security.delegation.DelegationValidationFailure;
import com.enterprise.iam.common.security.delegation.DelegationValidationResult;
import com.enterprise.iam.common.security.delegation.TrustedRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** Servlet request fence that publishes context only after full token validation. */
public final class TrustedDelegationFilter extends OncePerRequestFilter {

    public static final String DELEGATION_HEADER = "X-IAM-Delegation";
    public static final String TRUSTED_CONTEXT_ATTRIBUTE = TrustedRequestContext.class.getName();

    private final DelegationTokenDecoder decoder;
    private final RequestMatcher protectedRequestMatcher;
    private final RequestTraceIdResolver traceIdResolver;

    public TrustedDelegationFilter(
            DelegationTokenDecoder decoder,
            RequestMatcher protectedRequestMatcher,
            RequestTraceIdResolver traceIdResolver) {
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
        this.protectedRequestMatcher = Objects.requireNonNull(
                protectedRequestMatcher, "protectedRequestMatcher must not be null");
        this.traceIdResolver = Objects.requireNonNull(traceIdResolver, "traceIdResolver must not be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedRequestMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        DelegationValidationResult result = decoder.decode(request.getHeader(DELEGATION_HEADER));
        if (!result.isValid()) {
            if (result.failure() == DelegationValidationFailure.KEY_RESOLUTION_UNAVAILABLE) {
                reject(
                        request,
                        response,
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "IAM_AUTHENTICATION_DEPENDENCY_UNAVAILABLE",
                        "Authentication temporarily unavailable");
            } else {
                reject(
                        request,
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "IAM_AUTHENTICATION_REQUIRED",
                        "Authentication required");
            }
            return;
        }
        request.setAttribute(TRUSTED_CONTEXT_ATTRIBUTE, result.trustedContext().orElseThrow());
        filterChain.doFilter(request, response);
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        String traceId = normalizeTraceId(traceIdResolver.resolve(request));
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(
                "{\"success\":false,\"code\":\""
                        + code
                        + "\",\"message\":\""
                        + message
                        + "\",\"traceId\":\""
                        + traceId
                        + "\"}");
    }

    private static String normalizeTraceId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
            return "trace-unavailable";
        }
        return value;
    }
}
