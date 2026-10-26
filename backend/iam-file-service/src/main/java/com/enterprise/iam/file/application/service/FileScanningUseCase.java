package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.ClaimedScan;
import com.enterprise.iam.file.application.model.RetryClaimResult;
import com.enterprise.iam.file.application.port.out.FileScanPort;
import com.enterprise.iam.file.application.port.out.FileScanRepository;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.ScanPolicy;
import com.enterprise.iam.file.domain.model.ScanVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Drives claimed files through the scan pipeline (SPEC 35 §9/§13/§15/§16):
 *
 * <pre>
 * claim (VERIFYING/PENDING or retryable FAILED)
 *   -&gt; scan_record SCANNING
 *   -&gt; FileScanPort
 *   -&gt; CLEAN      : iam_file (AVAILABLE, CLEAN)
 *      INFECTED   : iam_file (QUARANTINED, INFECTED) — access endpoints block non-AVAILABLE files
 *      FAILED     : scan_status FAILED, status stays SCANNING until bounded retry/exhaustion
 *      SKIPPED    : only (AVAILABLE, NOT_REQUIRED) when policy allows; otherwise FAILED
 * </pre>
 *
 * Terminal files are never selected by the conditional claim SQL, so repeated ticks are
 * idempotent.
 */
public final class FileScanningUseCase {

    private static final Logger log = LoggerFactory.getLogger(FileScanningUseCase.class);

    static final String ERROR_SCANNER_FAILURE = "SCANNER_ERROR";
    static final String ERROR_SKIP_DENIED = "SCAN_SKIP_DENIED";

    private final FileScanRepository scanRepository;
    private final FileScanPort scanner;
    private final ScanPolicy policy;
    private final ScanRetryPolicy retryPolicy;
    private final String scannerName;
    private final Clock clock;

    public FileScanningUseCase(
            FileScanRepository scanRepository,
            FileScanPort scanner,
            ScanPolicy policy,
            ScanRetryPolicy retryPolicy,
            String scannerName,
            Clock clock) {
        this.scanRepository = Objects.requireNonNull(scanRepository, "scanRepository must not be null");
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.scannerName = Objects.requireNonNull(scannerName, "scannerName must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Claims and scans fresh pending files. Returns the number processed this tick. */
    public int processPending(int limit) {
        List<ClaimedScan> claimed = scanRepository.claimPendingBatch(limit);
        claimed.forEach(this::processClaimed);
        return claimed.size();
    }

    /** Claims and scans due retryable failures; logs files that exhausted all attempts. */
    public RetryClaimResult processRetries(int limit) {
        RetryClaimResult result = scanRepository.claimRetryableFailures(
                clock.instant(), policy.maxAttempts(), retryPolicy::backoffFor, limit);
        result.claimed().forEach(this::processClaimed);
        if (result.exhaustedCount() > 0) {
            log.warn("{} file(s) exhausted all {} scan attempts; files remain unavailable",
                    result.exhaustedCount(), policy.maxAttempts());
        }
        return result;
    }

    void processClaimed(ClaimedScan claimed) {
        try {
            Instant startedAt = clock.instant();
            scanRepository.recordScanStart(
                    claimed.tenantId(), claimed.fileId(), claimed.attempt(), scannerName, startedAt);
            ScanVerdict verdict = invokeScanner(claimed);
            applyVerdict(claimed, verdict, clock.instant());
        } catch (RuntimeException ex) {
            log.error("scan processing failed for file {} attempt {}",
                    claimed.fileId(), claimed.attempt(), ex);
        }
    }

    private ScanVerdict invokeScanner(ClaimedScan claimed) {
        try {
            return scanner.scan(claimed.toTarget());
        } catch (RuntimeException ex) {
            log.error("scanner {} failed for file {}", scannerName, claimed.fileId(), ex);
            return ScanVerdict.failed(ERROR_SCANNER_FAILURE);
        }
    }

    private void applyVerdict(ClaimedScan claimed, ScanVerdict verdict, Instant completedAt) {
        switch (verdict.result()) {
            case CLEAN -> finish(claimed, verdict, FileState.AVAILABLE, FileScanStatus.CLEAN, completedAt);
            case INFECTED -> finish(claimed, verdict, FileState.QUARANTINED, FileScanStatus.INFECTED, completedAt);
            case SKIPPED -> {
                if (policy.scanRequired()) {
                    log.warn("scanner returned SKIPPED for file {} but scan is required; failing attempt",
                            claimed.fileId());
                    scanRepository.recordScanOutcome(
                            claimed.tenantId(), claimed.fileId(), claimed.attempt(),
                            ScanVerdict.failed(ERROR_SKIP_DENIED), completedAt);
                } else {
                    finish(claimed, verdict, FileState.AVAILABLE, FileScanStatus.NOT_REQUIRED, completedAt);
                }
            }
            case FAILED -> scanRepository.recordScanOutcome(
                    claimed.tenantId(), claimed.fileId(), claimed.attempt(), verdict, completedAt);
        }
    }

    private void finish(ClaimedScan claimed, ScanVerdict verdict, FileState status,
                        FileScanStatus scanStatus, Instant completedAt) {
        scanRepository.recordScanOutcome(
                claimed.tenantId(), claimed.fileId(), claimed.attempt(), verdict, completedAt);
        int updated = scanRepository.transitionTerminal(
                claimed.tenantId(), claimed.fileId(), status, scanStatus, claimed.version());
        if (updated == 0) {
            log.warn("terminal transition to {}/{} skipped for file {} (already moved)",
                    status, scanStatus, claimed.fileId());
        }
    }
}
