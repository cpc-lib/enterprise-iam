package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.FileUploadPartRow;
import com.enterprise.iam.file.application.port.out.FileUploadPartRepository;
import com.enterprise.iam.file.domain.model.UploadPartStatus;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcFileUploadPartRepository implements FileUploadPartRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFileUploadPartRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void upsertUploaded(long id, long tenantId, long uploadSessionId, int partNumber, long partSize,
                               String partSha256Hex, String storageEtag, UploadPartStatus status,
                               Instant uploadedAt) {
        mapper.upsertUploadPart(id, tenantId, uploadSessionId, partNumber, partSize, partSha256Hex,
                storageEtag, status.name(), uploadedAt, Instant.now());
    }

    @Override
    public List<FileUploadPartRow> findBySession(long tenantId, long uploadSessionId) {
        return mapper.findUploadParts(tenantId, uploadSessionId);
    }

    @Override
    public Optional<FileUploadPartRow> findBySessionAndPartNumber(long tenantId, long uploadSessionId,
                                                                  int partNumber) {
        return Optional.ofNullable(mapper.findUploadPart(tenantId, uploadSessionId, partNumber));
    }
}
