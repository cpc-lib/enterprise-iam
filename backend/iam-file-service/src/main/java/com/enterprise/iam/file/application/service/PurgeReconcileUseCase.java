package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.PurgeCandidate;
import com.enterprise.iam.file.application.port.out.FileObjectStoragePort;
import com.enterprise.iam.file.application.port.out.FilePurgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Physical purge worker (SPEC 35 §38/§72/§73/§74):
 *
 * <pre>
 * DELETED + retention due + legal hold absent
 *   -&gt; recalculate ACTIVE references; skip when &gt; 0 (never trust cached reference_count)
 *   -&gt; mark file/object PURGING (version guarded)
 *   -&gt; FileObjectStoragePort.delete(bucket, objectKey)
 *   -&gt; mark file/object PURGED
 * </pre>
 *
 * A failed remote delete rolls PURGING back so the candidate is retried on a later tick;
 * the logical file stays invisible throughout.
 */
public final class PurgeReconcileUseCase {

    private static final Logger log = LoggerFactory.getLogger(PurgeReconcileUseCase.class);

    private final FilePurgeRepository repository;
    private final FileObjectStoragePort storagePort;
    private final Clock clock;
    private final Duration defaultRetention;

    public PurgeReconcileUseCase(
            FilePurgeRepository repository,
            FileObjectStoragePort storagePort,
            Clock clock,
            Duration defaultRetention) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.defaultRetention = Objects.requireNonNull(defaultRetention, "defaultRetention must not be null");
        if (defaultRetention.isNegative()) {
            throw new IllegalArgumentException("defaultRetention must not be negative");
        }
    }

    public int purgeOnce(int limit) {
        Instant now = clock.instant();
        List<PurgeCandidate> candidates =
                repository.findPurgeCandidates(now, now.minus(defaultRetention), limit);
        int purged = 0;
        for (PurgeCandidate candidate : candidates) {
            try {
                if (repository.countActiveReferences(candidate.tenantId(), candidate.fileId()) > 0) {
                    continue;
                }
                if (repository.markFilePurging(
                        candidate.tenantId(), candidate.fileId(), candidate.version()) != 1) {
                    continue;
                }
                repository.markObjectPurging(candidate.objectId());
                try {
                    storagePort.delete(candidate.bucketName(), candidate.objectKey());
                } catch (RuntimeException ex) {
                    log.error("remote object delete failed for file {}; rolling back PURGING for retry",
                            candidate.fileId(), ex);
                    repository.revertPurging(
                            candidate.tenantId(), candidate.fileId(), candidate.objectId());
                    continue;
                }
                repository.markPurged(candidate.tenantId(), candidate.fileId(), candidate.objectId());
                purged++;
            } catch (RuntimeException ex) {
                log.error("purge processing failed for file {}", candidate.fileId(), ex);
            }
        }
        return purged;
    }
}
