package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.OrphanLoginSessionReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class ScheduledOrphanLoginSessionReconciler {

    private static final Logger log = LoggerFactory.getLogger(
            ScheduledOrphanLoginSessionReconciler.class);

    private final OrphanLoginSessionReconciler reconciler;

    public ScheduledOrphanLoginSessionReconciler(OrphanLoginSessionReconciler reconciler) {
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler must not be null");
    }

    @Scheduled(
            initialDelayString = "${iam.auth.session-issuance.orphan-reconciliation-poll-delay:10s}",
            fixedDelayString = "${iam.auth.session-issuance.orphan-reconciliation-poll-delay:10s}")
    public void reconcile() {
        try {
            int count = reconciler.reconcileExpired();
            if (count > 0) {
                log.info("reconciled {} unconfirmed login deliveries", count);
            }
        } catch (RuntimeException exception) {
            log.error("unconfirmed login delivery reconciliation failed", exception);
        }
    }
}
