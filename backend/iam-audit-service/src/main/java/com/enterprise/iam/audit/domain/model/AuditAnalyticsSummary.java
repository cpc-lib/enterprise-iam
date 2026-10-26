package com.enterprise.iam.audit.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AuditAnalyticsSummary(
        long tenantId,
        long totalEvents,
        long allowCount,
        long denyCount,
        long highRiskEventCount,
        Map<String, Long> actionDistribution,
        Map<String, Long> resourceDistribution,
        List<AuditBucketCount> topActions,
        List<AuditBucketCount> topResources,
        String olapEngine) {

    public AuditAnalyticsSummary {
        actionDistribution = actionDistribution == null ? Map.of() : Map.copyOf(actionDistribution);
        resourceDistribution = resourceDistribution == null ? Map.of() : Map.copyOf(resourceDistribution);
        topActions = topActions == null ? List.of() : List.copyOf(topActions);
        topResources = topResources == null ? List.of() : List.copyOf(topResources);
        Objects.requireNonNull(olapEngine, "olapEngine must not be null");
    }
}
