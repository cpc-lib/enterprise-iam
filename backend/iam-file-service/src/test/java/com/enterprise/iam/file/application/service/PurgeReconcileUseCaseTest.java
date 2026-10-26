package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.PurgeCandidate;
import com.enterprise.iam.file.application.port.out.FileObjectStoragePort;
import com.enterprise.iam.file.application.port.out.FilePurgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurgeReconcileUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private StubPurgeRepository repository;
    private StubStorage storage;
    private PurgeReconcileUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new StubPurgeRepository();
        storage = new StubStorage();
        useCase = new PurgeReconcileUseCase(
                repository, storage, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30));
    }

    @Test
    void purgingCallsStorageThenMarksPurged() {
        repository.candidates.add(candidate());

        int purged = useCase.purgeOnce(50);

        assertThat(purged).isOne();
        assertThat(repository.events).containsExactly(
                "filePurging:101@0", "objectPurging:201", "purged:101/201");
        assertThat(storage.deletedKeys).containsExactly("k/iam-file-local");
        assertThat(repository.reverted).isEmpty();
    }

    @Test
    void fileWithRecalculatedActiveReferenceIsSkipped() {
        repository.candidates.add(candidate());
        repository.activeReferences = 1;

        int purged = useCase.purgeOnce(50);

        assertThat(purged).isZero();
        assertThat(repository.events).isEmpty();
        assertThat(storage.deletedKeys).isEmpty();
    }

    @Test
    void legalHoldAndRetentionFilterAtRepositoryYieldsNoPurge() {
        // No candidates returned: the repository SQL already excludes legal_hold = 1 and
        // files whose retention_until / default retention has not been reached.
        int purged = useCase.purgeOnce(50);

        assertThat(purged).isZero();
        assertThat(repository.events).isEmpty();
        assertThat(storage.deletedKeys).isEmpty();
    }

    @Test
    void remoteDeleteFailureRollsBackForRetry() {
        repository.candidates.add(candidate());
        storage.fail = true;

        int purged = useCase.purgeOnce(50);

        assertThat(purged).isZero();
        assertThat(repository.events).containsExactly(
                "filePurging:101@0", "objectPurging:201", "revert:101/201");
    }

    private static PurgeCandidate candidate() {
        return new PurgeCandidate(7L, 101L, 201L, "iam-file-local", "k", 0L);
    }

    private static final class StubStorage implements FileObjectStoragePort {

        private final List<String> deletedKeys = new ArrayList<>();
        private boolean fail;

        @Override
        public void delete(String bucketName, String objectKey) {
            if (fail) {
                throw new IllegalStateException("object store unavailable");
            }
            deletedKeys.add(objectKey + "/" + bucketName);
        }
    }

    private static final class StubPurgeRepository implements FilePurgeRepository {

        private final List<PurgeCandidate> candidates = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private final List<String> reverted = new ArrayList<>();
        private int activeReferences;

        @Override
        public List<PurgeCandidate> findPurgeCandidates(Instant retentionDue, Instant createdBeforeCutoff,
                                                        int limit) {
            return List.copyOf(candidates);
        }

        @Override
        public int countActiveReferences(long tenantId, long fileId) {
            return activeReferences;
        }

        @Override
        public int markFilePurging(long tenantId, long fileId, long expectedVersion) {
            events.add("filePurging:" + fileId + "@" + expectedVersion);
            return 1;
        }

        @Override
        public void markObjectPurging(long objectId) {
            events.add("objectPurging:" + objectId);
        }

        @Override
        public void markPurged(long tenantId, long fileId, long objectId) {
            events.add("purged:" + fileId + "/" + objectId);
        }

        @Override
        public void revertPurging(long tenantId, long fileId, long objectId) {
            String event = "revert:" + fileId + "/" + objectId;
            events.add(event);
            reverted.add(event);
        }
    }
}
