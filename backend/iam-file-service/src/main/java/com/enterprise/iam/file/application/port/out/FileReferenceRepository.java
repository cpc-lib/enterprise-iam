package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.FileReferenceRow;

import java.util.Optional;

public interface FileReferenceRepository {

    FileReferenceRow save(FileReferenceRow reference);

    Optional<FileReferenceRow> findById(long tenantId, long referenceId);
}
