package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.AbortUploadCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;

import java.util.Objects;

public final class AbortFileUploadSessionUseCase {

    private final FileUploadSessionRepository sessionRepository;

    public AbortFileUploadSessionUseCase(FileUploadSessionRepository sessionRepository) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
    }

    public void execute(AbortUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var session = sessionRepository.findById(command.tenantId(), command.uploadId())
                .orElseThrow(() -> new ResourceNotFoundException("upload session not found"));
        if (session.version() != command.version()) {
            throw new VersionConflictException("session version mismatch");
        }
        sessionRepository.abort(command.tenantId(), command.uploadId(), command.version());
    }
}
