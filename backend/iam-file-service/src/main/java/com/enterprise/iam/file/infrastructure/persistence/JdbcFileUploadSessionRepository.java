package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.UploadSession;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper.UploadSessionRow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcFileUploadSessionRepository implements FileUploadSessionRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFileUploadSessionRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public UploadSession save(UploadSession session) {
        int updated = mapper.insertUploadSession(session, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("upload session insert did not affect exactly one row");
        }
        return session;
    }

    @Override
    public Optional<UploadSession> findById(long tenantId, long uploadId) {
        UploadSessionRow row = mapper.findUploadSession(tenantId, uploadId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new UploadSession(
                row.id(), row.tenantId(), row.userId(), row.fileName(), row.fileSize(),
                row.wholeSha256Hex(), row.declaredContentType(), row.storageUploadId(),
                row.bucketName(), row.objectKey(), row.partSize(), row.partCount(),
                row.uploadedPartCount(), UploadSessionStatus.valueOf(row.status()), row.expireAt(),
                row.completedFileId(), row.quotaReservationBytes(), row.version(), List.of()));
    }

    @Override
    public void markStatus(long tenantId, long uploadId, UploadSessionStatus status,
                           Long completedFileId, long expectedVersion) {
        int updated = mapper.markUploadSessionStatus(
                tenantId, uploadId, status.name(), completedFileId, expectedVersion, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("upload session status update did not affect exactly one row");
        }
    }

    @Override
    public void incrementUploadedPartCount(long tenantId, long uploadId) {
        int updated = mapper.incrementUploadedPartCount(tenantId, uploadId, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("upload session part count increment did not affect exactly one row");
        }
    }

    @Override
    public void abort(long tenantId, long uploadId, long expectedVersion) {
        int updated = mapper.abortUploadSession(tenantId, uploadId, expectedVersion, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("upload session abort did not affect exactly one row");
        }
    }
}
