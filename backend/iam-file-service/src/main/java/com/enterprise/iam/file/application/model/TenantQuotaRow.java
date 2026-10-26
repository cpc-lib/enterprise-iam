package com.enterprise.iam.file.application.model;

/**
 * Current quota row snapshot used by the quota reconciler.
 */
public record TenantQuotaRow(
        long tenantId,
        long quotaBytes,
        long usedLogicalBytes,
        long reservedBytes,
        long version) {
}
