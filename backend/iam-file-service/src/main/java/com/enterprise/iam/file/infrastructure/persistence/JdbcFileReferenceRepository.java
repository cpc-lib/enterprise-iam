package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.FileReferenceRow;
import com.enterprise.iam.file.application.port.out.FileReferenceRepository;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcFileReferenceRepository implements FileReferenceRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFileReferenceRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public FileReferenceRow save(FileReferenceRow reference) {
        int updated = mapper.insertFileReference(reference, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("file reference insert did not affect exactly one row");
        }
        return reference;
    }

    @Override
    public Optional<FileReferenceRow> findById(long tenantId, long referenceId) {
        return Optional.ofNullable(mapper.findFileReferenceById(tenantId, referenceId));
    }
}
