package com.enterprise.iam.file.infrastructure.worker;

import com.enterprise.iam.file.application.model.RetryClaimResult;
import com.enterprise.iam.file.application.service.FileScanningUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * In-process scan poller (SPEC 35 §9/§16). Each tick claims pending and due retry files;
 * any exception is logged so a failed tick can never kill the scheduler.
 */
public final class ScheduledFileScanPoller {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFileScanPoller.class);

    private final FileScanningUseCase useCase;
    private final int batchSize;

    public ScheduledFileScanPoller(FileScanningUseCase useCase, int batchSize) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${iam.file.scan.poll-interval:5s}",
            initialDelayString = "${iam.file.scan.poll-interval:5s}")
    public void poll() {
        try {
            int pending = useCase.processPending(batchSize);
            RetryClaimResult retry = useCase.processRetries(batchSize);
            log.debug("file scan tick processed {} pending and {} retry files ({} exhausted)",
                    pending, retry.claimed().size(), retry.exhaustedCount());
        } catch (RuntimeException ex) {
            log.error("scheduled file scan tick failed", ex);
        }
    }
}
