package com.enterprise.iam.gateway.delegation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredDownstreamRouteAudienceRegistryTest {

    @Test
    void resolvesOnlyExplicitRouteAudienceBindings() {
        var registry = new ConfiguredDownstreamRouteAudienceRegistry(Map.of(
                "authorization-route", "iam-authorization-service"),
                Set.of("public-route"));

        assertThat(registry.audienceForRoute("authorization-route"))
                .contains("iam-authorization-service");
        assertThat(registry.audienceForRoute("unregistered-route")).isEmpty();
        assertThat(registry.isExplicitPublicRoute("public-route")).isTrue();
        assertThat(registry.isExplicitPublicRoute("unregistered-route")).isFalse();
    }

    @Test
    void rejectsUnsafeRouteOrAudienceNames() {
        assertThatThrownBy(() -> new ConfiguredDownstreamRouteAudienceRegistry(
                Map.of("../route", "iam-authorization-service"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredDownstreamRouteAudienceRegistry(
                Map.of("authorization-route", "https://attacker.example"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredDownstreamRouteAudienceRegistry(
                Map.of("authorization-route", "iam-authorization-service"),
                Set.of("authorization-route")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both public");
    }
}
