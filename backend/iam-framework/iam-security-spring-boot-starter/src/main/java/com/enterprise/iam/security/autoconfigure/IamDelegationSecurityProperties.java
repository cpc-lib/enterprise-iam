package com.enterprise.iam.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@ConfigurationProperties("iam.security.delegation")
public final class IamDelegationSecurityProperties {

    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");

    private boolean enabled;
    private String issuer;
    private String audience;
    private Duration maximumTtl = Duration.ofSeconds(30);
    private Duration clockSkew = Duration.ofSeconds(5);
    private Duration jwksCacheTtl = Duration.ofMinutes(5);
    private Duration unknownKeyTtl = Duration.ofSeconds(30);
    private Duration unknownKeyRefreshMinimumInterval = Duration.ofSeconds(5);
    private List<String> protectedPaths = List.of("/internal/**");
    private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 1_000;

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (issuer == null || !SERVICE_NAME.matcher(issuer).matches()) {
            throw new IllegalStateException("iam.security.delegation.issuer is invalid");
        }
        if (audience == null || !SERVICE_NAME.matcher(audience).matches()) {
            throw new IllegalStateException("iam.security.delegation.audience is invalid");
        }
        if (maximumTtl == null || maximumTtl.isZero() || maximumTtl.isNegative()
                || maximumTtl.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException(
                    "iam.security.delegation.maximum-ttl must be within 1-30 seconds");
        }
        if (clockSkew == null || clockSkew.isNegative()
                || clockSkew.compareTo(Duration.ofSeconds(5)) > 0) {
            throw new IllegalStateException(
                    "iam.security.delegation.clock-skew must be within 0-5 seconds");
        }
        if (protectedPaths == null || protectedPaths.isEmpty()) {
            throw new IllegalStateException(
                    "iam.security.delegation.protected-paths must not be empty");
        }
        if (jwksCacheTtl == null || jwksCacheTtl.isZero() || jwksCacheTtl.isNegative()
                || jwksCacheTtl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalStateException(
                    "iam.security.delegation.jwks-cache-ttl must be within 1ms-10m");
        }
        if (unknownKeyTtl == null || unknownKeyTtl.isZero() || unknownKeyTtl.isNegative()
                || unknownKeyTtl.compareTo(jwksCacheTtl) > 0) {
            throw new IllegalStateException(
                    "iam.security.delegation.unknown-key-ttl must be positive and not exceed the JWKS cache TTL");
        }
        if (unknownKeyRefreshMinimumInterval == null
                || unknownKeyRefreshMinimumInterval.isZero()
                || unknownKeyRefreshMinimumInterval.isNegative()
                || unknownKeyRefreshMinimumInterval.compareTo(unknownKeyTtl) > 0) {
            throw new IllegalStateException(
                    "iam.security.delegation.unknown-key-refresh-minimum-interval must be positive and not exceed unknown-key-ttl");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getMaximumTtl() {
        return maximumTtl;
    }

    public void setMaximumTtl(Duration maximumTtl) {
        this.maximumTtl = maximumTtl;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public Duration getJwksCacheTtl() {
        return jwksCacheTtl;
    }

    public void setJwksCacheTtl(Duration jwksCacheTtl) {
        this.jwksCacheTtl = jwksCacheTtl;
    }

    public Duration getUnknownKeyTtl() {
        return unknownKeyTtl;
    }

    public void setUnknownKeyTtl(Duration unknownKeyTtl) {
        this.unknownKeyTtl = unknownKeyTtl;
    }

    public Duration getUnknownKeyRefreshMinimumInterval() {
        return unknownKeyRefreshMinimumInterval;
    }

    public void setUnknownKeyRefreshMinimumInterval(
            Duration unknownKeyRefreshMinimumInterval) {
        this.unknownKeyRefreshMinimumInterval = unknownKeyRefreshMinimumInterval;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths == null ? null : List.copyOf(protectedPaths);
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }
}
