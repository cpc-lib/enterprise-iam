package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.FileAccessCommand;
import com.enterprise.iam.file.application.exception.FileLockedException;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.model.FileAccessResult;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileAccessMode;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class AuthorizeFileDownloadUseCase {

    static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);
    static final String DOWNLOAD_BASE = "https://storage.local/download";

    private final FileRepository fileRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public AuthorizeFileDownloadUseCase(
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public FileAccessResult execute(FileAccessCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return authorize(command, DOWNLOAD_TTL, FileAccessMode.PROXY);
    }

    protected FileAccessResult authorize(FileAccessCommand command, Duration ttl, FileAccessMode defaultMode) {
        var file = fileRepository.findById(command.tenantId(), command.fileId())
                .orElseThrow(() -> new ResourceNotFoundException("file not found"));
        if (file.status() != FileState.AVAILABLE
                || file.scanStatus() != FileScanStatus.CLEAN) {
            throw new FileLockedException("file is not available for access in status "
                    + file.status() + "/" + file.scanStatus());
        }

        FileAccessMode mode = resolveMode(command, defaultMode);
        Instant expiresAt = clock.instant().plus(ttl);
        long auditId = idGenerator.nextId();
        String accessUrl = DOWNLOAD_BASE + "/" + command.fileId() + "?audit=" + auditId
                + "&mode=" + mode.name();
        return new FileAccessResult(mode, accessUrl, expiresAt, auditId);
    }

    private FileAccessMode resolveMode(FileAccessCommand command, FileAccessMode defaultMode) {
        if (command.requestedMode() == null
                || command.requestedMode() == FileAccessMode.AUTO) {
            return defaultMode;
        }
        return command.requestedMode();
    }
}
