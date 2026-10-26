package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.UploadPartStatus;

import java.time.Instant;
import java.util.Objects;

public final class FileUploadPartRow {

    private final long id;
    private final long tenantId;
    private final long uploadSessionId;
    private final int partNumber;
    private final long partSize;
    private final String partSha256Hex;
    private final String storageEtag;
    private final UploadPartStatus status;
    private final Instant uploadedAt;
    private final long version;

    public FileUploadPartRow(
            long id,
            long tenantId,
            long uploadSessionId,
            int partNumber,
            long partSize,
            String partSha256Hex,
            String storageEtag,
            UploadPartStatus status,
            Instant uploadedAt,
            long version) {
        if (id <= 0 || tenantId <= 0 || uploadSessionId <= 0) {
            throw new IllegalArgumentException("id, tenantId and uploadSessionId must be positive");
        }
        if (partNumber < 1 || partNumber > 10000) {
            throw new IllegalArgumentException("partNumber must be 1..10000");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.uploadSessionId = uploadSessionId;
        this.partNumber = partNumber;
        this.partSize = partSize;
        this.partSha256Hex = partSha256Hex;
        this.storageEtag = storageEtag;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.uploadedAt = uploadedAt;
        this.version = version;
    }

    public long id() { return id; }
    public long tenantId() { return tenantId; }
    public long uploadSessionId() { return uploadSessionId; }
    public int partNumber() { return partNumber; }
    public long partSize() { return partSize; }
    public String partSha256Hex() { return partSha256Hex; }
    public String storageEtag() { return storageEtag; }
    public UploadPartStatus status() { return status; }
    public Instant uploadedAt() { return uploadedAt; }
    public long version() { return version; }
}
