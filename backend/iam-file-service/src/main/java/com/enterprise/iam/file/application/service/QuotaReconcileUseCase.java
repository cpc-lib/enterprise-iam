package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.TenantQuotaRow;
import com.enterprise.iam.file.application.port.out.QuotaReconcileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Reconciles tenant quota counters from source of truth (SPEC 35 §71 logical file
 * ownership usage):
 * <ul>
 *   <li>used_logical_bytes = SUM(file_size) of consuming statuses
 *       (DELETED/PURGING/PURGED do not consume);</li>
 *   <li>reserved_bytes = SUM(quota_reservation_bytes) of active non-terminal sessions.</li>
 * </ul>
 * Rows are only written when drift is detected, using the optimistic version column.
 */
public final class QuotaReconcileUseCase {

    private static final Logger log = LoggerFactory.getLogger(QuotaReconcileUseCase.class);

    private final QuotaReconcileRepository repository;

    public QuotaReconcileUseCase(QuotaReconcileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public int reconcileOnce() {
        List<TenantQuotaRow> rows = repository.findAll();
        int adjusted = 0;
        for (TenantQuotaRow row : rows) {
            try {
                long used = repository.sumLogicalUsage(row.tenantId());
                long reserved = repository.sumReservedBytes(row.tenantId());
                if (used == row.usedLogicalBytes() && reserved == row.reservedBytes()) {
                    continue;
                }
                if (used + reserved > row.quotaBytes()) {
                    log.warn("quota drift for tenant {} would exceed quota (used={}, reserved={}, quota={}); "
                            + "skipping automatic correction", row.tenantId(), used, reserved, row.quotaBytes());
                    continue;
                }
                int updated = repository.adjustQuota(
                        row.tenantId(), used, reserved, row.version());
                if (updated == 1) {
                    adjusted++;
                } else {
                    log.warn("quota correction for tenant {} skipped (concurrent version change)",
                            row.tenantId());
                }
            } catch (RuntimeException ex) {
                log.error("quota reconciliation failed for tenant {}", row.tenantId(), ex);
            }
        }
        return adjusted;
    }
}
