package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.FileUploadPartRow;
import com.enterprise.iam.file.domain.model.UploadPartStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FileUploadPartRepository {

    void upsertUploaded(long id, long tenantId, long uploadSessionId, int partNumber, long partSize,
                        String partSha256Hex, String storageEtag, UploadPartStatus status, Instant uploadedAt);

    List<FileUploadPartRow> findBySession(long tenantId, long uploadSessionId);

    Optional<FileUploadPartRow> findBySessionAndPartNumber(long tenantId, long uploadSessionId, int partNumber);
}
