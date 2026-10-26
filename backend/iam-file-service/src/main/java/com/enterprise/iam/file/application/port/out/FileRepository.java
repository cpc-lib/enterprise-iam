package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.FileAggregate;

import java.util.Optional;

public interface FileRepository {

    FileAggregate save(FileAggregate file);

    Optional<FileAggregate> findById(long tenantId, long fileId);

    int deleteLogical(long tenantId, long fileId, long deleteMarker, long expectedVersion);
}
