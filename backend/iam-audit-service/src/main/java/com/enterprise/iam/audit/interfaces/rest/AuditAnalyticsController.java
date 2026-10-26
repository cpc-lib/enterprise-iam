package com.enterprise.iam.audit.interfaces.rest;

import com.enterprise.iam.audit.application.service.AuditAnalyticsUseCase;
import com.enterprise.iam.audit.domain.model.AuditAnalyticsSummary;
import com.enterprise.iam.audit.domain.model.AuditArchiveExportSummary;
import com.enterprise.iam.audit.domain.model.AuditBucketCount;
import com.enterprise.iam.audit.interfaces.rest.dto.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@ConditionalOnProperty(prefix = "iam.audit", name = "enabled", havingValue = "true")
public class AuditAnalyticsController {

    private final AuditAnalyticsUseCase analyticsUseCase;

    public AuditAnalyticsController(AuditAnalyticsUseCase analyticsUseCase) {
        this.analyticsUseCase = analyticsUseCase;
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<ApiResponse<AuditAnalyticsSummaryResponse>> getSummary(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId);
        AuditAnalyticsSummary summary = analyticsUseCase.summarize(resolvedTenantId);
        return ResponseEntity.ok(ApiResponse.ok(
                new AuditAnalyticsSummaryResponse(
                        String.valueOf(summary.tenantId()),
                        summary.totalEvents(),
                        summary.allowCount(),
                        summary.denyCount(),
                        summary.highRiskEventCount(),
                        summary.actionDistribution(),
                        summary.resourceDistribution(),
                        summary.topActions(),
                        summary.topResources(),
                        summary.olapEngine()),
                traceId));
    }

    @PostMapping("/archive-export")
    public ResponseEntity<ApiResponse<AuditArchiveExportResponse>> exportArchive(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId);
        AuditArchiveExportSummary result = analyticsUseCase.exportArchive(resolvedTenantId);
        return ResponseEntity.ok(ApiResponse.ok(
                new AuditArchiveExportResponse(
                        String.valueOf(result.tenantId()),
                        result.exportedCount(),
                        result.storageTarget(),
                        result.olapEngine(),
                        result.exportedAt()),
                traceId));
    }

    private static long resolveTenantId(Long queryTenantId, Long headerTenantId) {
        if (queryTenantId != null && queryTenantId > 0) {
            return queryTenantId;
        }
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        return 1L;
    }

    public record AuditAnalyticsSummaryResponse(
            String tenantId,
            long totalEvents,
            long allowCount,
            long denyCount,
            long highRiskEventCount,
            Map<String, Long> actionDistribution,
            Map<String, Long> resourceDistribution,
            List<AuditBucketCount> topActions,
            List<AuditBucketCount> topResources,
            String olapEngine) {
    }

    public record AuditArchiveExportResponse(
            String tenantId,
            long exportedCount,
            String storageTarget,
            String olapEngine,
            Instant exportedAt) {
    }
}
