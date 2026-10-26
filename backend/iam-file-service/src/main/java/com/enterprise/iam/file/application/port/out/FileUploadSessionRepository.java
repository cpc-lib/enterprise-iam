package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.UploadSession;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.util.Optional;

public interface FileUploadSessionRepository {

    UploadSession save(UploadSession session);

    Optional<UploadSession> findById(long tenantId, long uploadId);

    void markStatus(long tenantId, long uploadId, UploadSessionStatus status, Long completedFileId, long expectedVersion);

    void incrementUploadedPartCount(long tenantId, long uploadId);

    void abort(long tenantId, long uploadId, long expectedVersion);
}
