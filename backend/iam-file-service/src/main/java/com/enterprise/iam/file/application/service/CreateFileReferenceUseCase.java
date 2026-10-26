package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.CreateFileReferenceCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.model.EntityResult;
import com.enterprise.iam.file.application.model.FileReferenceRow;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.application.port.out.FileReferenceRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileReferenceStatus;

import java.util.Objects;

public final class CreateFileReferenceUseCase {

    private final FileReferenceRepository referenceRepository;
    private final FileRepository fileRepository;
    private final FileObjectRepository fileObjectRepository;
    private final PositiveIdGenerator idGenerator;

    public CreateFileReferenceUseCase(
            FileReferenceRepository referenceRepository,
            FileRepository fileRepository,
            FileObjectRepository fileObjectRepository,
            PositiveIdGenerator idGenerator) {
        this.referenceRepository = Objects.requireNonNull(referenceRepository);
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    public EntityResult execute(CreateFileReferenceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var file = fileRepository.findById(command.tenantId(), command.fileId())
                .orElseThrow(() -> new ResourceNotFoundException("file not found"));
        long referenceId = idGenerator.nextId();
        FileReferenceRow reference = new FileReferenceRow(
                referenceId,
                command.tenantId(),
                command.fileId(),
                command.resourceId(),
                command.resourceInstanceKey(),
                command.referenceType(),
                command.fieldId() == null ? 0L : command.fieldId(),
                command.sortOrder(),
                FileReferenceStatus.ACTIVE,
                1,
                0L);
        referenceRepository.save(reference);
        fileObjectRepository.incrementReferenceCount(file.objectId());
        return new EntityResult(referenceId, 0L, FileReferenceStatus.ACTIVE.name());
    }
}
