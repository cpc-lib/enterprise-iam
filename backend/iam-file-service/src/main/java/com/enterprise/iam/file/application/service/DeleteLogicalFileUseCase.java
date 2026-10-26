package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.DeleteFileCommand;
import com.enterprise.iam.file.application.exception.FileLockedException;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileState;

import java.util.Objects;

public final class DeleteLogicalFileUseCase {

    private final FileRepository fileRepository;
    private final FileObjectRepository fileObjectRepository;
    private final PositiveIdGenerator idGenerator;

    public DeleteLogicalFileUseCase(
            FileRepository fileRepository,
            FileObjectRepository fileObjectRepository,
            PositiveIdGenerator idGenerator) {
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    public FileResult execute(DeleteFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var file = fileRepository.findById(command.tenantId(), command.fileId())
                .orElseThrow(() -> new ResourceNotFoundException("file not found"));
        if (file.legalHold()) {
            throw new FileLockedException("file is under legal hold");
        }
        if (file.version() != command.version()) {
            throw new VersionConflictException("file version mismatch");
        }
        long deleteMarker = idGenerator.nextId();
        int updated = fileRepository.deleteLogical(
                command.tenantId(),
                command.fileId(),
                deleteMarker,
                command.version());
        if (updated == 0) {
            throw new VersionConflictException("file version mismatch during delete");
        }
        fileObjectRepository.decrementReferenceCount(file.objectId());
        return new FileResult(
                command.fileId(),
                FileState.DELETED,
                file.scanStatus(),
                command.version() + 1);
    }
}
