package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.UploadMode;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class UploadSession {

    private final long id;
    private final long tenantId;
    private final long userId;
    private final String fileName;
    private final long fileSize;
    private final String wholeSha256Hex;
    private final String declaredContentType;
    private final String storageUploadId;
    private final String bucketName;
    private final String objectKey;
    private final long partSize;
    private final int partCount;
    private final int uploadedPartCount;
    private final UploadSessionStatus status;
    private final Instant expireAt;
    private final Long completedFileId;
    private final long quotaReservationBytes;
    private final long version;
    private final List<Integer> uploadedParts;

    public UploadSession(
            long id,
            long tenantId,
            long userId,
            String fileName,
            long fileSize,
            String wholeSha256Hex,
            String declaredContentType,
            String storageUploadId,
            String bucketName,
            String objectKey,
            long partSize,
            int partCount,
            int uploadedPartCount,
            UploadSessionStatus status,
            Instant expireAt,
            Long completedFileId,
            long quotaReservationBytes,
            long version,
            List<Integer> uploadedParts) {
        if (id <= 0 || tenantId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("id, tenantId and userId must be positive");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.fileSize = fileSize;
        this.wholeSha256Hex = Objects.requireNonNull(wholeSha256Hex, "wholeSha256Hex must not be null");
        this.declaredContentType = Objects.requireNonNull(declaredContentType, "declaredContentType must not be null");
        this.storageUploadId = storageUploadId;
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName must not be null");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey must not be null");
        this.partSize = partSize;
        this.partCount = partCount;
        this.uploadedPartCount = uploadedPartCount;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.expireAt = Objects.requireNonNull(expireAt, "expireAt must not be null");
        this.completedFileId = completedFileId;
        this.quotaReservationBytes = quotaReservationBytes;
        this.version = version;
        this.uploadedParts = uploadedParts == null ? List.of() : List.copyOf(uploadedParts);
    }

    public long id() { return id; }
    public long tenantId() { return tenantId; }
    public long userId() { return userId; }
    public String fileName() { return fileName; }
    public long fileSize() { return fileSize; }
    public String wholeSha256Hex() { return wholeSha256Hex; }
    public String declaredContentType() { return declaredContentType; }
    public String storageUploadId() { return storageUploadId; }
    public String bucketName() { return bucketName; }
    public String objectKey() { return objectKey; }
    public long partSize() { return partSize; }
    public int partCount() { return partCount; }
    public int uploadedPartCount() { return uploadedPartCount; }
    public UploadSessionStatus status() { return status; }
    public Instant expireAt() { return expireAt; }
    public Long completedFileId() { return completedFileId; }
    public long quotaReservationBytes() { return quotaReservationBytes; }
    public long version() { return version; }
    public List<Integer> uploadedParts() { return uploadedParts; }

    public UploadMode mode() {
        return partCount > 1 || storageUploadId != null ? UploadMode.MULTIPART : UploadMode.INSTANT;
    }
}
