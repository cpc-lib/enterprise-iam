package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.ClaimedScan;
import com.enterprise.iam.file.application.model.RetryClaimResult;
import com.enterprise.iam.file.application.port.out.FileScanRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.ScanVerdict;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper.RetryCandidateRow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * MyBatis implementation of the scan claim pipeline. Conditional updates ensure
 * that only the winning worker processes a file.
 */
public final class JdbcFileScanRepository implements FileScanRepository {

    private final FilePersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;

    public JdbcFileScanRepository(FilePersistenceMapper mapper, PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public List<ClaimedScan> claimPendingBatch(int limit) {
        List<ClaimedScan> claimed = new ArrayList<>();
        Instant now = Instant.now();
        for (Long id : mapper.findPendingScanIds(limit)) {
            if (mapper.claimPendingScan(id, now) == 1) {
                ClaimedScan row = mapper.findClaimedScan(id, 1);
                if (row != null) {
                    claimed.add(row);
                }
            }
        }
        return claimed;
    }

    @Override
    public RetryClaimResult claimRetryableFailures(
            Instant now, int maxAttempts, IntFunction<Duration> backoffByAttempt, int limit) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(backoffByAttempt, "backoffByAttempt must not be null");

        int exhaustedCount = mapper.countExhaustedScans(maxAttempts);
        int fetchLimit = Math.max(limit * 8, limit);
        List<RetryCandidateRow> candidates = mapper.findRetryCandidates(fetchLimit);

        List<ClaimedScan> claimed = new ArrayList<>();
        Instant claimAt = Instant.now();
        for (RetryCandidateRow candidate : candidates) {
            if (claimed.size() >= limit) {
                break;
            }
            if (candidate.lastAttempt() >= maxAttempts) {
                continue;
            }
            Duration backoff = backoffByAttempt.apply(candidate.lastAttempt());
            if (candidate.lastCompletedAt().plus(backoff).isAfter(now)) {
                continue;
            }
            if (mapper.claimRetryScan(candidate.fileId(), candidate.tenantId(), claimAt) == 1) {
                ClaimedScan row = mapper.findClaimedScan(candidate.fileId(), candidate.lastAttempt() + 1);
                if (row != null) {
                    claimed.add(row);
                }
            }
        }
        return new RetryClaimResult(claimed, exhaustedCount);
    }

    @Override
    public void recordScanStart(long tenantId, long fileId, int attempt, String scanner, Instant startedAt) {
        int updated = mapper.insertScanStart(
                idGenerator.nextId(), tenantId, fileId, attempt, scanner, startedAt, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("scan record start insert did not affect exactly one row");
        }
    }

    @Override
    public void recordScanOutcome(long tenantId, long fileId, int attempt, ScanVerdict verdict,
                                  Instant completedAt) {
        Objects.requireNonNull(verdict, "verdict must not be null");
        Instant now = Instant.now();
        Instant completed = completedAt == null ? now : completedAt;
        String result = verdict.result() == ScanVerdict.Result.SKIPPED
                ? FileScanStatus.CLEAN.name()
                : verdict.result().name();

        int updated = mapper.completeScanRecord(
                tenantId, fileId, attempt, result, verdict.threatCode(), verdict.errorCode(),
                verdict.signatureVersion(), completed);
        if (updated == 0) {
            throw new IllegalStateException(
                    "scan record outcome update did not find a SCANNING row for attempt " + attempt);
        }

        if (verdict.result() == ScanVerdict.Result.FAILED
                && mapper.markFileScanFailed(tenantId, fileId, now) != 1) {
            throw new IllegalStateException("marking file scan FAILED did not affect exactly one row");
        }
    }

    @Override
    public int transitionTerminal(long tenantId, long fileId, FileState status,
                                  FileScanStatus scanStatus, long expectedVersion) {
        return mapper.transitionFileScanTerminal(
                tenantId, fileId, status.name(), scanStatus.name(), expectedVersion, Instant.now());
    }
}
