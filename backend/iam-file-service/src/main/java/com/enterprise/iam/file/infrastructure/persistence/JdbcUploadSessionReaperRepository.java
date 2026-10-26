package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.ExpiredSession;
import com.enterprise.iam.file.application.port.out.UploadSessionReaperRepository;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcUploadSessionReaperRepository implements UploadSessionReaperRepository {

    private final FilePersistenceMapper mapper;

    public JdbcUploadSessionReaperRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<ExpiredSession> findExpiredActiveSessions(Instant now, int limit) {
        return mapper.findExpiredSessions(now, limit);
    }

    @Override
    public int markExpired(long tenantId, long sessionId, long expectedVersion) {
        return mapper.markUploadSessionExpired(tenantId, sessionId, expectedVersion, Instant.now());
    }

    @Override
    public int releaseReservation(long tenantId, long bytes) {
        return mapper.releaseQuotaReservation(tenantId, bytes, Instant.now());
    }
}
