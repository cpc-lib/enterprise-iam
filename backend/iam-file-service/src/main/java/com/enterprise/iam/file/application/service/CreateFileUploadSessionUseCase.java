package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.CreateUploadSessionCommand;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.model.FileObject;
import com.enterprise.iam.file.application.model.FileAggregate;
import com.enterprise.iam.file.application.model.UploadSession;
import com.enterprise.iam.file.application.model.UploadSessionResult;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileObjectStatus;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.UploadMode;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class CreateFileUploadSessionUseCase {

    static final long PART_SIZE_BYTES = 5L * 1024 * 1024; // 5 MiB
    static final long MAX_FILE_SIZE = 53_687_091_200L; // 50 GiB
    static final String BUCKET_NAME = "iam-file-local";
    static final Duration MULTIPART_TTL = Duration.ofHours(1);

    private final FileUploadSessionRepository sessionRepository;
    private final FileObjectRepository fileObjectRepository;
    private final FileRepository fileRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateFileUploadSessionUseCase(
            FileUploadSessionRepository sessionRepository,
            FileObjectRepository fileObjectRepository,
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository);
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public UploadSessionResult execute(CreateUploadSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        long sessionId = idGenerator.nextId();

        if (command.fileSize() > MAX_FILE_SIZE) {
            throw new ResourceNotFoundException("file size exceeds the 50 GiB policy ceiling");
        }

        boolean multipart = command.fileSize() > PART_SIZE_BYTES;
        String objectKey = command.tenantId() + "/" + sessionId + "/" + command.fileName();
        long quotaReservation = command.fileSize();

        if (multipart) {
            long partSize = PART_SIZE_BYTES;
            int partCount = (int) Math.ceil((double) command.fileSize() / partSize);
            UploadSession session = new UploadSession(
                    sessionId,
                    command.tenantId(),
                    command.userId(),
                    command.fileName(),
                    command.fileSize(),
                    command.sha256Hex(),
                    command.declaredContentType(),
                    "upload-" + sessionId,
                    BUCKET_NAME,
                    objectKey,
                    partSize,
                    partCount,
                    0,
                    UploadSessionStatus.INIT,
                    now.plus(MULTIPART_TTL),
                    null,
                    quotaReservation,
                    0,
                    List.of());
            sessionRepository.save(session);
            return new UploadSessionResult(
                    UploadMode.MULTIPART,
                    sessionId,
                    null,
                    UploadSessionStatus.INIT,
                    partSize,
                    partCount,
                    List.of(),
                    session.expireAt(),
                    0);
        }

        // INSTANT: object + file + completed session in one transaction-equivalent flow.
        long objectId = idGenerator.nextId();
        FileObject fileObject = new FileObject(
                objectId,
                command.sha256Hex(),
                command.fileSize(),
                BUCKET_NAME,
                objectKey,
                null,
                "STANDARD",
                FileObjectStatus.ACTIVE,
                1L);
        fileObjectRepository.save(fileObject);

        long fileId = idGenerator.nextId();
        FileAggregate file = new FileAggregate(
                fileId,
                command.tenantId(),
                objectId,
                command.fileName(),
                command.declaredContentType(),
                command.fileSize(),
                command.sha256Hex(),
                command.userId(),
                FileState.VERIFYING,
                FileScanStatus.PENDING,
                false,
                0L,
                0L);
        fileRepository.save(file);

        UploadSession session = new UploadSession(
                sessionId,
                command.tenantId(),
                command.userId(),
                command.fileName(),
                command.fileSize(),
                command.sha256Hex(),
                command.declaredContentType(),
                null,
                BUCKET_NAME,
                objectKey,
                PART_SIZE_BYTES,
                1,
                0,
                UploadSessionStatus.COMPLETED,
                now.plus(MULTIPART_TTL),
                fileId,
                quotaReservation,
                0,
                List.of());
        sessionRepository.save(session);

        return new UploadSessionResult(
                UploadMode.INSTANT,
                null,
                fileId,
                UploadSessionStatus.COMPLETED,
                null,
                null,
                List.of(),
                null,
                0);
    }
}
