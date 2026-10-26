package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.FileAggregate;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcFileRepository implements FileRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFileRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public FileAggregate save(FileAggregate file) {
        int updated = mapper.insertFile(file, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("file insert did not affect exactly one row");
        }
        return file;
    }

    @Override
    public Optional<FileAggregate> findById(long tenantId, long fileId) {
        return Optional.ofNullable(mapper.findFileById(tenantId, fileId));
    }

    @Override
    public int deleteLogical(long tenantId, long fileId, long deleteMarker, long expectedVersion) {
        return mapper.deleteFileLogical(tenantId, fileId, deleteMarker, expectedVersion, Instant.now());
    }
}
