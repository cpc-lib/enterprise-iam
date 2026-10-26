package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.SignPartCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.model.SignPartResult;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SignPartUploadUseCase {

    static final Duration SIGN_TTL = Duration.ofMinutes(15);
    static final String STORAGE_BASE = "https://storage.local/upload";

    private final FileUploadSessionRepository sessionRepository;
    private final Clock clock;

    public SignPartUploadUseCase(FileUploadSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public SignPartResult execute(SignPartCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var session = sessionRepository.findById(command.tenantId(), command.uploadId())
                .orElseThrow(() -> new ResourceNotFoundException("upload session not found"));
        if (session.version() != command.version()) {
            throw new VersionConflictException("session version mismatch");
        }
        UploadSessionStatus status = session.status();
        if (status != UploadSessionStatus.INIT && status != UploadSessionStatus.UPLOADING) {
            throw new VersionConflictException("session is not signable in status " + status);
        }

        Instant expiresAt = clock.instant().plus(SIGN_TTL);
        String uploadUrl = STORAGE_BASE + "/" + command.uploadId() + "/" + command.partNumber()
                + "?token=" + Long.toUnsignedString(command.uploadId()) + "-" + command.partNumber();
        Map<String, String> requiredHeaders = new LinkedHashMap<>();
        requiredHeaders.put("Content-Type", "application/octet-stream");
        requiredHeaders.put("Content-Length", String.valueOf(command.expectedSize()));
        return new SignPartResult(uploadUrl, expiresAt, requiredHeaders);
    }
}
