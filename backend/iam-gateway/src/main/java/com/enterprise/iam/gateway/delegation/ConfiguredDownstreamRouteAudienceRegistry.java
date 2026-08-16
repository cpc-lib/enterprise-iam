package com.enterprise.iam.gateway.delegation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfiguredDownstreamRouteAudienceRegistry
        implements DownstreamRouteAudienceRegistry {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");
    private final Map<String, String> routeAudiences;
    private final Set<String> publicRoutes;

    public ConfiguredDownstreamRouteAudienceRegistry(
            Map<String, String> routeAudiences,
            Set<String> publicRoutes) {
        Objects.requireNonNull(routeAudiences, "routeAudiences must not be null");
        Objects.requireNonNull(publicRoutes, "publicRoutes must not be null");
        if (routeAudiences.isEmpty() && publicRoutes.isEmpty()) {
            throw new IllegalArgumentException("at least one explicit route security policy is required");
        }
        routeAudiences.forEach((routeId, audience) -> {
            if (routeId == null || !NAME.matcher(routeId).matches()) {
                throw new IllegalArgumentException("protected route ID format is invalid");
            }
            if (audience == null || !NAME.matcher(audience).matches()) {
                throw new IllegalArgumentException("protected route audience format is invalid");
            }
        });
        publicRoutes.forEach(routeId -> {
            if (routeId == null || !NAME.matcher(routeId).matches()) {
                throw new IllegalArgumentException("public route ID format is invalid");
            }
            if (routeAudiences.containsKey(routeId)) {
                throw new IllegalArgumentException(
                        "a route cannot be both public and delegation protected");
            }
        });
        this.routeAudiences = Map.copyOf(routeAudiences);
        this.publicRoutes = Set.copyOf(publicRoutes);
    }

    @Override
    public Optional<String> audienceForRoute(String routeId) {
        return Optional.ofNullable(routeAudiences.get(routeId));
    }

    @Override
    public boolean isExplicitPublicRoute(String routeId) {
        return publicRoutes.contains(routeId);
    }
}
