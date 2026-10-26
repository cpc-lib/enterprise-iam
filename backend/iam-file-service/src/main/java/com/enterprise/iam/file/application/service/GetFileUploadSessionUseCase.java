package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.GetUploadSessionCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.model.UploadSessionResult;
import com.enterprise.iam.file.application.port.out.FileUploadPartRepository;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.domain.model.UploadMode;

import java.util.Objects;
import java.util.stream.Collectors;

public final class GetFileUploadSessionUseCase {

    private final FileUploadSessionRepository sessionRepository;
    private final FileUploadPartRepository partRepository;

    public GetFileUploadSessionUseCase(
            FileUploadSessionRepository sessionRepository,
            FileUploadPartRepository partRepository) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.partRepository = Objects.requireNonNull(partRepository);
    }

    public UploadSessionResult execute(GetUploadSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var session = sessionRepository.findById(command.tenantId(), command.uploadId())
                .orElseThrow(() -> new ResourceNotFoundException("upload session not found"));
        var parts = partRepository.findBySession(command.tenantId(), command.uploadId());
        var uploadedParts = parts.stream()
                .map(p -> p.partNumber())
                .collect(Collectors.toList());
        UploadMode mode = session.mode();
        Long partSize = mode == UploadMode.MULTIPART ? session.partSize() : null;
        Integer partCount = mode == UploadMode.MULTIPART ? session.partCount() : null;
        return new UploadSessionResult(
                mode,
                mode == UploadMode.MULTIPART ? session.id() : null,
                session.completedFileId(),
                session.status(),
                partSize,
                partCount,
                uploadedParts,
                mode == UploadMode.MULTIPART ? session.expireAt() : null,
                session.version());
    }
}
