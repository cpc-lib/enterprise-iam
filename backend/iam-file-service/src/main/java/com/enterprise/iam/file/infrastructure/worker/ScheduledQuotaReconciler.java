package com.enterprise.iam.file.infrastructure.worker;

import com.enterprise.iam.file.application.service.QuotaReconcileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Recomputes tenant used/reserved quota bytes and writes drift-only corrections.
 */
public final class ScheduledQuotaReconciler {

    private static final Logger log = LoggerFactory.getLogger(ScheduledQuotaReconciler.class);

    private final QuotaReconcileUseCase useCase;

    public ScheduledQuotaReconciler(QuotaReconcileUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    }

    @Scheduled(
            fixedDelayString = "${iam.file.quota-reconcile.poll-interval:5m}",
            initialDelayString = "${iam.file.quota-reconcile.poll-interval:5m}")
    public void poll() {
        try {
            int adjusted = useCase.reconcileOnce();
            log.debug("quota reconcile tick corrected {} tenant rows", adjusted);
        } catch (RuntimeException ex) {
            log.error("scheduled quota reconcile tick failed", ex);
        }
    }
}
