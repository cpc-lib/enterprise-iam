package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.ReportPartCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.port.out.FileUploadPartRepository;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.UploadPartStatus;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class ReportFileUploadPartUseCase {

    private final FileUploadPartRepository partRepository;
    private final FileUploadSessionRepository sessionRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public ReportFileUploadPartUseCase(
            FileUploadPartRepository partRepository,
            FileUploadSessionRepository sessionRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.partRepository = Objects.requireNonNull(partRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public void execute(ReportPartCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var session = sessionRepository.findById(command.tenantId(), command.uploadId())
                .orElseThrow(() -> new ResourceNotFoundException("upload session not found"));
        if (session.version() != command.version()) {
            throw new VersionConflictException("session version mismatch");
        }
        UploadSessionStatus status = session.status();
        if (status != UploadSessionStatus.INIT
                && status != UploadSessionStatus.UPLOADING
                && status != UploadSessionStatus.VERIFYING) {
            throw new VersionConflictException("session is not part-acceptable in status " + status);
        }

        long partId = idGenerator.nextId();
        Instant now = clock.instant();
        partRepository.upsertUploaded(
                partId,
                command.tenantId(),
                command.uploadId(),
                command.partNumber(),
                command.partSize(),
                command.partSha256Hex(),
                command.storageEtag(),
                UploadPartStatus.UPLOADED,
                now);
        sessionRepository.incrementUploadedPartCount(command.tenantId(), command.uploadId());
    }
}
