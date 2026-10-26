package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.ExpiredSession;

import java.time.Instant;
import java.util.List;

/**
 * Persistence for expiring stale upload sessions and releasing their quota reservations.
 */
public interface UploadSessionReaperRepository {

    List<ExpiredSession> findExpiredActiveSessions(Instant now, int limit);

    /** Version-and-status guarded INIT/UPLOADING/VERIFYING/MERGING -&gt; EXPIRED. */
    int markExpired(long tenantId, long sessionId, long expectedVersion);

    /**
     * Releases reservation bytes, clamped at zero so a concurrent adjustment can never drive
     * reserved_bytes negative.
     */
    int releaseReservation(long tenantId, long bytes);
}
