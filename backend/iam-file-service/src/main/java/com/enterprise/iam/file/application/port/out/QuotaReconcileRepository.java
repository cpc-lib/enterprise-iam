package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.TenantQuotaRow;

import java.util.List;

/**
 * Read model and drift-correction writes for tenant quota reconciliation
 * (SPEC 35 §70/§71 logical file ownership usage).
 */
public interface QuotaReconcileRepository {

    List<TenantQuotaRow> findAll();

    /** SUM(file_size) of consuming file statuses (DELETED/PURGING/PURGED do not consume). */
    long sumLogicalUsage(long tenantId);

    /** SUM(quota_reservation_bytes) of non-terminal active upload sessions. */
    long sumReservedBytes(long tenantId);

    /**
     * Optimistic version-guarded correction. Returns 0 on version conflict or when the
     * corrected values would violate the quota CHECK constraint.
     */
    int adjustQuota(long tenantId, long usedLogicalBytes, long reservedBytes, long expectedVersion);
}
