package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.FileObject;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcFileObjectRepository implements FileObjectRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFileObjectRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public FileObject save(FileObject fileObject) {
        int updated = mapper.insertFileObject(
                fileObject.id(), fileObject.sha256Hex(), fileObject.fileSize(), fileObject.bucketName(),
                fileObject.objectKey(), fileObject.storageVersion(), fileObject.storageTier(),
                fileObject.status().name(), fileObject.referenceCount(), Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("file object insert did not affect exactly one row");
        }
        return fileObject;
    }

    @Override
    public Optional<FileObject> findById(long objectId) {
        return Optional.ofNullable(mapper.findFileObjectById(objectId));
    }

    @Override
    public void incrementReferenceCount(long objectId) {
        int updated = mapper.incrementFileObjectReferenceCount(objectId, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("file object reference increment did not affect exactly one row");
        }
    }

    @Override
    public void decrementReferenceCount(long objectId) {
        int updated = mapper.decrementFileObjectReferenceCount(objectId, Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("file object reference decrement did not affect exactly one row");
        }
    }
}
