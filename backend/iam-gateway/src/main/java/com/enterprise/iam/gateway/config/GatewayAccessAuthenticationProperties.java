package com.enterprise.iam.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties("iam.gateway.access-authentication")
public final class GatewayAccessAuthenticationProperties {

    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");

    private boolean enabled;
    private String issuer = "iam-auth-service";
    private String audience = "iam-gateway";
    private Duration maximumTtl = Duration.ofMinutes(5);
    private Duration clockSkew = Duration.ofSeconds(30);
    private Duration jwksCacheTtl = Duration.ofMinutes(5);
    private Duration unknownKeyTtl = Duration.ofSeconds(30);
    private Duration unknownKeyRefreshMinimumInterval = Duration.ofSeconds(5);
    private URI jwksUri;
    private Set<String> jwksAllowedHosts = new LinkedHashSet<>();
    private Duration jwksConnectTimeout = Duration.ofSeconds(2);
    private Duration jwksRequestTimeout = Duration.ofSeconds(3);

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        requireServiceName(issuer, "issuer");
        requireServiceName(audience, "audience");
        requirePositiveAtMost(maximumTtl, Duration.ofMinutes(5), "maximum-ttl");
        requireNonNegativeAtMost(clockSkew, Duration.ofSeconds(30), "clock-skew");
        requirePositiveAtMost(jwksCacheTtl, Duration.ofMinutes(10), "jwks-cache-ttl");
        requirePositiveAtMost(unknownKeyTtl, jwksCacheTtl, "unknown-key-ttl");
        requirePositiveAtMost(
                unknownKeyRefreshMinimumInterval,
                unknownKeyTtl,
                "unknown-key-refresh-minimum-interval");
        requirePositiveAtMost(
                jwksConnectTimeout, Duration.ofSeconds(5), "jwks-connect-timeout");
        requirePositiveAtMost(
                jwksRequestTimeout, Duration.ofSeconds(10), "jwks-request-timeout");
    }

    public void validateJwksTransportConfiguration() {
        validateEnabledConfiguration();
        if (jwksUri == null) {
            throw new IllegalStateException(
                    "iam.gateway.access-authentication.jwks-uri is required");
        }
        if (jwksAllowedHosts == null || jwksAllowedHosts.isEmpty()) {
            throw new IllegalStateException(
                    "iam.gateway.access-authentication.jwks-allowed-hosts is required");
        }
    }

    private static void requireServiceName(String value, String name) {
        if (value == null || !SERVICE_NAME.matcher(value).matches()) {
            throw new IllegalStateException(
                    "iam.gateway.access-authentication." + name + " is invalid");
        }
    }

    private static void requirePositiveAtMost(
            Duration value,
            Duration maximum,
            String name) {
        if (value == null || value.isZero() || value.isNegative()
                || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(
                    "iam.gateway.access-authentication."
                            + name
                            + " must be positive and at most "
                            + maximum);
        }
    }

    private static void requireNonNegativeAtMost(
            Duration value,
            Duration maximum,
            String name) {
        if (value == null || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(
                    "iam.gateway.access-authentication."
                            + name
                            + " must be non-negative and at most "
                            + maximum);
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

    public URI getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(URI jwksUri) {
        this.jwksUri = jwksUri;
    }

    public Set<String> getJwksAllowedHosts() {
        return Set.copyOf(jwksAllowedHosts);
    }

    public void setJwksAllowedHosts(Set<String> jwksAllowedHosts) {
        this.jwksAllowedHosts = jwksAllowedHosts == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(jwksAllowedHosts);
    }

    public Duration getJwksConnectTimeout() {
        return jwksConnectTimeout;
    }

    public void setJwksConnectTimeout(Duration jwksConnectTimeout) {
        this.jwksConnectTimeout = jwksConnectTimeout;
    }

    public Duration getJwksRequestTimeout() {
        return jwksRequestTimeout;
    }

    public void setJwksRequestTimeout(Duration jwksRequestTimeout) {
        this.jwksRequestTimeout = jwksRequestTimeout;
    }
}
