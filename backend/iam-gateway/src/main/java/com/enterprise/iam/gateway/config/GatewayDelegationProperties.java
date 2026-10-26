package com.enterprise.iam.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("iam.gateway.delegation")
public final class GatewayDelegationProperties {

    private boolean enabled;
    private Map<String, String> routeAudiences = new LinkedHashMap<>();
    private Set<String> publicRoutes = new LinkedHashSet<>();
    private String privateKeyPem;
    private String privateKeyPath;
    private String delegationKeyId;
    private String delegationIssuer = "iam-gateway";
    private Duration delegationTokenTtl = Duration.ofSeconds(10);

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

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getDelegationKeyId() {
        return delegationKeyId;
    }

    public void setDelegationKeyId(String delegationKeyId) {
        this.delegationKeyId = delegationKeyId;
    }

    public String getDelegationIssuer() {
        return delegationIssuer;
    }

    public void setDelegationIssuer(String delegationIssuer) {
        this.delegationIssuer = delegationIssuer;
    }

    public Duration getDelegationTokenTtl() {
        return delegationTokenTtl;
    }

    public void setDelegationTokenTtl(Duration delegationTokenTtl) {
        this.delegationTokenTtl = delegationTokenTtl;
    }
}
