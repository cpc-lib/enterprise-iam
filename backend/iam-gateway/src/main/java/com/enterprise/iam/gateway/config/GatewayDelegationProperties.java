package com.enterprise.iam.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("iam.gateway.delegation")
public final class GatewayDelegationProperties {

    private boolean enabled;
    private Map<String, String> routeAudiences = new LinkedHashMap<>();
    private Set<String> publicRoutes = new LinkedHashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getRouteAudiences() {
        return routeAudiences;
    }

    public void setRouteAudiences(Map<String, String> routeAudiences) {
        this.routeAudiences = routeAudiences == null
                ? null
                : new LinkedHashMap<>(routeAudiences);
    }

    public Set<String> getPublicRoutes() {
        return publicRoutes;
    }

    public void setPublicRoutes(Set<String> publicRoutes) {
        this.publicRoutes = publicRoutes == null
                ? null
                : new LinkedHashSet<>(publicRoutes);
    }
}
