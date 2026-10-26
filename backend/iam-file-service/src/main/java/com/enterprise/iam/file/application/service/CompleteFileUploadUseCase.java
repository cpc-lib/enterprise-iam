package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.CompleteUploadCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.model.FileAggregate;
import com.enterprise.iam.file.application.model.FileObject;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileObjectStatus;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.util.Objects;

public final class CompleteFileUploadUseCase {

    private final FileUploadSessionRepository sessionRepository;
    private final FileObjectRepository fileObjectRepository;
    private final FileRepository fileRepository;
    private final PositiveIdGenerator idGenerator;

    public CompleteFileUploadUseCase(
            FileUploadSessionRepository sessionRepository,
            FileObjectRepository fileObjectRepository,
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository);
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    public FileResult execute(CompleteUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var session = sessionRepository.findById(command.tenantId(), command.uploadId())
                .orElseThrow(() -> new ResourceNotFoundException("upload session not found"));
        if (session.version() != command.version()) {
            throw new VersionConflictException("session version mismatch");
        }
        if (session.status() == UploadSessionStatus.COMPLETED
                || session.status() == UploadSessionStatus.ABORTED
                || session.status() == UploadSessionStatus.EXPIRED) {
            throw new VersionConflictException("session is already terminal in status " + session.status());
        }

        long objectId = idGenerator.nextId();
        FileObject fileObject = new FileObject(
                objectId,
                session.wholeSha256Hex(),
                session.fileSize(),
                session.bucketName(),
                session.objectKey(),
                null,
                "STANDARD",
                FileObjectStatus.ACTIVE,
                1L);
        fileObjectRepository.save(fileObject);

        long fileId = idGenerator.nextId();
        FileAggregate file = new FileAggregate(
                fileId,
                session.tenantId(),
                objectId,
                session.fileName(),
                session.declaredContentType(),
                session.fileSize(),
                session.wholeSha256Hex(),
                session.userId(),
                FileState.VERIFYING,
                FileScanStatus.PENDING,
                false,
                0L,
                0L);
        fileRepository.save(file);

        sessionRepository.markStatus(
                command.tenantId(),
                command.uploadId(),
                UploadSessionStatus.COMPLETED,
                fileId,
                command.version());

        return new FileResult(fileId, FileState.VERIFYING, FileScanStatus.PENDING, 0L);
    }
}
