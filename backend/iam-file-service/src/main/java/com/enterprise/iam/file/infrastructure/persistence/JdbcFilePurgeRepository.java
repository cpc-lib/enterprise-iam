package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.PurgeCandidate;
import com.enterprise.iam.file.application.port.out.FilePurgeRepository;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcFilePurgeRepository implements FilePurgeRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFilePurgeRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<PurgeCandidate> findPurgeCandidates(Instant retentionDue, Instant createdBeforeCutoff, int limit) {
        return mapper.findPurgeCandidates(retentionDue, createdBeforeCutoff, limit);
    }

    @Override
    public int countActiveReferences(long tenantId, long fileId) {
        return mapper.countActiveReferences(tenantId, fileId);
    }

    @Override
    public int markFilePurging(long tenantId, long fileId, long expectedVersion) {
        return mapper.markFilePurging(tenantId, fileId, expectedVersion, Instant.now());
    }

    @Override
    public void markObjectPurging(long objectId) {
        mapper.markObjectPurging(objectId, Instant.now());
    }

    @Override
    public void markPurged(long tenantId, long fileId, long objectId) {
        Instant now = Instant.now();
        int files = mapper.markFilePurged(tenantId, fileId, now);
        if (files != 1) {
            throw new IllegalStateException("file PURGED transition did not affect exactly one row");
        }
        mapper.markObjectPurged(objectId, now);
    }

    @Override
    public void revertPurging(long tenantId, long fileId, long objectId) {
        Instant now = Instant.now();
        mapper.revertFilePurging(tenantId, fileId, now);
        mapper.revertObjectPurging(objectId, now);
    }
}
