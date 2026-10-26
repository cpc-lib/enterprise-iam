package com.enterprise.iam.audit.application.service;

import com.enterprise.iam.audit.application.port.out.AuditAnalyticsPort;
import com.enterprise.iam.audit.domain.model.AuditAnalyticsSummary;
import com.enterprise.iam.audit.domain.model.AuditArchiveExportSummary;

import java.util.Objects;

public final class AuditAnalyticsUseCase {

    private final AuditAnalyticsPort analyticsPort;

    public AuditAnalyticsUseCase(AuditAnalyticsPort analyticsPort) {
        this.analyticsPort = Objects.requireNonNull(analyticsPort);
    }

    public AuditAnalyticsSummary summarize(long tenantId) {
        return analyticsPort.computeSummary(tenantId);
    }

    public AuditArchiveExportSummary exportArchive(long tenantId) {
        return analyticsPort.exportToColdArchive(tenantId);
    }
}
