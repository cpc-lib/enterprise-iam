package com.enterprise.iam.audit.application.port.out;

import com.enterprise.iam.audit.domain.model.AuditAnalyticsSummary;
import com.enterprise.iam.audit.domain.model.AuditArchiveExportSummary;

public interface AuditAnalyticsPort {

    AuditAnalyticsSummary computeSummary(long tenantId);

    AuditArchiveExportSummary exportToColdArchive(long tenantId);
}
