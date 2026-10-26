package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.ExpiredSession;
import com.enterprise.iam.file.application.port.out.UploadSessionReaperRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Expires active upload sessions (INIT/UPLOADING/VERIFYING/MERGING) whose expire_at has
 * passed and releases their quota reservation. The version guard and the clamped release
 * ensure neither operation can corrupt concurrent state or drive reserved_bytes negative.
 */
public final class UploadSessionReaperUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionReaperUseCase.class);

    private final UploadSessionReaperRepository repository;
    private final Clock clock;

    public UploadSessionReaperUseCase(UploadSessionReaperRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public int reapOnce(int limit) {
        List<ExpiredSession> expired = repository.findExpiredActiveSessions(clock.instant(), limit);
        int reaped = 0;
        for (ExpiredSession session : expired) {
            try {
                int updated = repository.markExpired(
                        session.tenantId(), session.sessionId(), session.version());
                if (updated != 1) {
                    continue;
                }
                repository.releaseReservation(session.tenantId(), session.quotaReservationBytes());
                reaped++;
            } catch (RuntimeException ex) {
                log.error("failed to expire upload session {}", session.sessionId(), ex);
            }
        }
        return reaped;
    }
}
