package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.FileAccessCommand;
import com.enterprise.iam.file.application.model.FileAccessResult;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileAccessMode;

import java.time.Clock;
import java.time.Duration;

public class AuthorizeFilePreviewUseCase extends AuthorizeFileDownloadUseCase {

    static final Duration PREVIEW_TTL = Duration.ofMinutes(2);

    public AuthorizeFilePreviewUseCase(
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        super(fileRepository, idGenerator, clock);
    }

    @Override
    public FileAccessResult execute(FileAccessCommand command) {
        return authorize(command, PREVIEW_TTL, FileAccessMode.SANDBOX_PREVIEW);
    }
}
