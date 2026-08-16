package com.enterprise.iam.gateway.delegation;

import java.util.Optional;

public interface DownstreamRouteAudienceRegistry {

    Optional<String> audienceForRoute(String routeId);

    boolean isExplicitPublicRoute(String routeId);
}
