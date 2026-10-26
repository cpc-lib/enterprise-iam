package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.PurgeCandidate;

import java.time.Instant;
import java.util.List;

/**
 * Persistence for the physical purge transaction (SPEC 35 §38/§72/§73).
 */
public interface FilePurgeRepository {

    /**
     * DELETED files with legal_hold = 0 whose retention is due: retention_until &lt;= now,
     * or when retention_until is NULL, created_at &lt;= {@code createdBeforeCutoff}
     * (configured default retention).
     */
    List<PurgeCandidate> findPurgeCandidates(Instant retentionDue, Instant createdBeforeCutoff, int limit);

    /** Recalculated count of ACTIVE references (status='ACTIVE' and active_slot=1). */
    int countActiveReferences(long tenantId, long fileId);

    /** Version-guarded iam_file DELETED -&gt; PURGING; returns 0 when the row moved concurrently. */
    int markFilePurging(long tenantId, long fileId, long expectedVersion);

    /** iam_file_object ACTIVE/QUARANTINED -&gt; PURGING; already-PURGING rows are a no-op. */
    void markObjectPurging(long objectId);

    /** iam_file PURGING -&gt; PURGED and iam_file_object PURGING -&gt; PURGED. */
    void markPurged(long tenantId, long fileId, long objectId);

    /** Rolls the file/object back from PURGING after a failed remote delete so it retries. */
    void revertPurging(long tenantId, long fileId, long objectId);
}
