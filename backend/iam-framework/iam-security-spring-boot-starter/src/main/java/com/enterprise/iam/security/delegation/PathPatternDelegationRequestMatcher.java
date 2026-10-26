package com.enterprise.iam.security.delegation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Objects;

/** Matches explicitly configured service paths without permitting broad public-API patterns. */
public final class PathPatternDelegationRequestMatcher implements RequestMatcher {

    private final List<PathPattern> patterns;

    public PathPatternDelegationRequestMatcher(List<String> patterns) {
        Objects.requireNonNull(patterns, "patterns must not be null");
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("at least one protected path is required");
        }
        PathPatternParser parser = new PathPatternParser();
        this.patterns = patterns.stream()
                .map(PathPatternDelegationRequestMatcher::requireServicePattern)
                .map(parser::parse)
                .toList();
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && !requestUri.startsWith(contextPath)) {
            return false;
        }
        String applicationPath = contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());
        PathContainer path = PathContainer.parsePath(applicationPath);
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static String requireServicePattern(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("protected path must not be blank");
        }
        String pattern = value.trim();
        boolean internalPattern = pattern.equals("/internal")
                || pattern.startsWith("/internal/");
        boolean exactApiPath = pattern.startsWith("/api/")
                && !pattern.contains("*")
                && !pattern.contains("{")
                && !pattern.contains("}");
        if (!internalPattern && !exactApiPath) {
            throw new IllegalArgumentException(
                    "delegation protected paths must remain below /internal or be exact /api paths");
        }
        return pattern;
    }
}
