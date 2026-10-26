package com.enterprise.iam.file.infrastructure.worker;

import com.enterprise.iam.file.application.service.UploadSessionReaperUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Expires stale upload sessions and releases their quota reservations.
 */
public final class ScheduledUploadSessionReaper {

    private static final Logger log = LoggerFactory.getLogger(ScheduledUploadSessionReaper.class);

    private final UploadSessionReaperUseCase useCase;
    private final int batchSize;

    public ScheduledUploadSessionReaper(UploadSessionReaperUseCase useCase, int batchSize) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${iam.file.reaper.poll-interval:60s}",
            initialDelayString = "${iam.file.reaper.poll-interval:60s}")
    public void poll() {
        try {
            int reaped = useCase.reapOnce(batchSize);
            log.debug("upload session reaper tick expired {} sessions", reaped);
        } catch (RuntimeException ex) {
            log.error("scheduled upload session reaper tick failed", ex);
        }
    }
}
