package com.enterprise.iam.audit.infrastructure.persistence;

import com.enterprise.iam.audit.application.port.out.AuditAnalyticsPort;
import com.enterprise.iam.audit.domain.model.AuditAnalyticsSummary;
import com.enterprise.iam.audit.domain.model.AuditArchiveExportSummary;
import com.enterprise.iam.audit.domain.model.AuditBucketCount;
import com.enterprise.iam.audit.infrastructure.persistence.mapper.AuditLogPersistenceMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConfigurableAuditAnalyticsAdapter implements AuditAnalyticsPort {

    private final AuditLogPersistenceMapper mapper;
    private final String configuredEngine;
    private final String archiveStorageTarget;
    private final Clock clock;

    public ConfigurableAuditAnalyticsAdapter(
            AuditLogPersistenceMapper mapper,
            String configuredEngine,
            String archiveStorageTarget,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.configuredEngine = configuredEngine == null ? "relational" : configuredEngine;
        this.archiveStorageTarget = (archiveStorageTarget == null || archiveStorageTarget.isBlank())
                ? "s3://iam-audit-cold-archive/v2/parquet"
                : archiveStorageTarget;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AuditAnalyticsSummary computeSummary(long tenantId) {
        long totalEvents = mapper.countTotalByTenant(tenantId);
        List<AuditBucketCount> actionBuckets = mapper.countGroupByAction(tenantId);
        List<AuditBucketCount> resourceBuckets = mapper.countGroupByResourceType(tenantId);

        Map<String, Long> actionDistribution = new LinkedHashMap<>();
        long denyCount = 0L;
        long highRiskCount = 0L;

        for (AuditBucketCount bucket : actionBuckets) {
            actionDistribution.put(bucket.key(), bucket.count());
            String upper = bucket.key().toUpperCase(Locale.ROOT);
            if (upper.contains("FAIL") || upper.contains("DENY") || upper.contains("REJECT") || upper.contains("REVOKE")) {
                denyCount += bucket.count();
            }
            if (upper.contains("DELETE") || upper.contains("REVOKE") || upper.contains("PURGE")
                    || upper.contains("PRIVILEGE") || upper.contains("SOD") || upper.contains("FAIL")) {
                highRiskCount += bucket.count();
            }
        }
        long allowCount = Math.max(0L, totalEvents - denyCount);

        Map<String, Long> resourceDistribution = new LinkedHashMap<>();
        for (AuditBucketCount bucket : resourceBuckets) {
            resourceDistribution.put(bucket.key(), bucket.count());
        }

        return new AuditAnalyticsSummary(
                tenantId,
                totalEvents,
                allowCount,
                denyCount,
                highRiskCount,
                actionDistribution,
                resourceDistribution,
                actionBuckets,
                resourceBuckets,
                resolveOlapEngineLabel());
    }

    @Override
    public AuditArchiveExportSummary exportToColdArchive(long tenantId) {
        long totalEvents = mapper.countTotalByTenant(tenantId);
        return new AuditArchiveExportSummary(
                tenantId,
                totalEvents,
                archiveStorageTarget,
                resolveOlapEngineLabel(),
                Instant.now(clock));
    }

    private String resolveOlapEngineLabel() {
        return "clickhouse".equalsIgnoreCase(configuredEngine) ? "CLICKHOUSE" : "RELATIONAL_OLAP";
    }
}
