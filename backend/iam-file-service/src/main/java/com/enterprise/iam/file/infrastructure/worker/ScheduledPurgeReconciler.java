package com.enterprise.iam.file.infrastructure.worker;

import com.enterprise.iam.file.application.service.PurgeReconcileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Physical purge poller (SPEC 35 §38/§72/§73). In V1 the storage port is a logging
 * no-op; the PURGING/PURGED state transitions still run for real.
 */
public final class ScheduledPurgeReconciler {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPurgeReconciler.class);

    private final PurgeReconcileUseCase useCase;
    private final int batchSize;

    public ScheduledPurgeReconciler(PurgeReconcileUseCase useCase, int batchSize) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${iam.file.purge.poll-interval:10m}",
            initialDelayString = "${iam.file.purge.poll-interval:10m}")
    public void poll() {
        try {
            int purged = useCase.purgeOnce(batchSize);
            log.debug("purge reconcile tick purged {} files", purged);
        } catch (RuntimeException ex) {
            log.error("scheduled purge reconcile tick failed", ex);
        }
    }
}
