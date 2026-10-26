package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.FileObjectStatus;

import java.util.Objects;

public final class FileObject {

    private final long id;
    private final String sha256Hex;
    private final long fileSize;
    private final String bucketName;
    private final String objectKey;
    private final String storageVersion;
    private final String storageTier;
    private final FileObjectStatus status;
    private final long referenceCount;

    public FileObject(
            long id,
            String sha256Hex,
            long fileSize,
            String bucketName,
            String objectKey,
            String storageVersion,
            String storageTier,
            FileObjectStatus status,
            long referenceCount) {
        if (id <= 0 || fileSize <= 0) {
            throw new IllegalArgumentException("id and fileSize must be positive");
        }
        this.id = id;
        this.sha256Hex = Objects.requireNonNull(sha256Hex, "sha256Hex must not be null");
        this.fileSize = fileSize;
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName must not be null");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey must not be null");
        this.storageVersion = storageVersion;
        this.storageTier = storageTier == null ? "STANDARD" : storageTier;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.referenceCount = referenceCount;
    }

    public long id() { return id; }
    public String sha256Hex() { return sha256Hex; }
    public long fileSize() { return fileSize; }
    public String bucketName() { return bucketName; }
    public String objectKey() { return objectKey; }
    public String storageVersion() { return storageVersion; }
    public String storageTier() { return storageTier; }
    public FileObjectStatus status() { return status; }
    public long referenceCount() { return referenceCount; }
}
