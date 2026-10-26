package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.ClaimedScan;
import com.enterprise.iam.file.application.model.RetryClaimResult;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.ScanVerdict;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Persistence operations for the scan pipeline (SPEC 35 §9/§16). Claims are conditional
 * updates so concurrent workers can never process the same file twice.
 */
public interface FileScanRepository {

    /**
     * Atomically claims up to {@code limit} fresh files: iam_file
     * (VERIFYING, PENDING) -&gt; (SCANNING, SCANNING). Returns the rows this caller won.
     */
    List<ClaimedScan> claimPendingBatch(int limit);

    /**
     * Claims failed files whose latest scan_record is FAILED, whose attempt count is below
     * {@code maxAttempts} and whose bounded backoff ({@code backoffByAttempt} applied to the
     * last attempt) has elapsed. Files at/over {@code maxAttempts} are not claimed and are
     * reported via {@link RetryClaimResult#exhaustedCount()}.
     */
    RetryClaimResult claimRetryableFailures(
            Instant now, int maxAttempts, IntFunction<Duration> backoffByAttempt, int limit);

    /**
     * Inserts the scan_record row for an attempt in state SCANNING (unique on
     * tenant/file/attempt).
     */
    void recordScanStart(long tenantId, long fileId, int attempt, String scanner, Instant startedAt);

    /**
     * Completes the SCANNING scan_record with the verdict. A FAILED verdict also keeps
     * iam_file.scan_status = FAILED while iam_file.status stays SCANNING so the retry
     * pipeline can pick the file up. SKIPPED (policy-allowed only) is recorded as CLEAN
     * because the scan_record CHECK has no SKIPPED value.
     */
    void recordScanOutcome(long tenantId, long fileId, int attempt, ScanVerdict verdict, Instant completedAt);

    /**
     * Version-guarded terminal transition of iam_file, e.g. (AVAILABLE, CLEAN) or
     * (QUARANTINED, INFECTED). Returns 0 when another writer already moved the file
     * (idempotent tick).
     */
    int transitionTerminal(
            long tenantId, long fileId, FileState status, FileScanStatus scanStatus, long expectedVersion);
}
