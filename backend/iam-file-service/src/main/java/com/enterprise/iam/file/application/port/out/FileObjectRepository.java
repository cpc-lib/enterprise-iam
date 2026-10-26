package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.FileObject;

import java.util.Optional;

public interface FileObjectRepository {

    FileObject save(FileObject fileObject);

    Optional<FileObject> findById(long objectId);

    void incrementReferenceCount(long objectId);

    void decrementReferenceCount(long objectId);
}
