package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;

import java.time.Instant;
import java.util.Objects;

public final class FileAggregate {

    private final long id;
    private final long tenantId;
    private final long objectId;
    private final String fileName;
    private final String declaredContentType;
    private final long fileSize;
    private final String sha256Hex;
    private final long ownerUserId;
    private final FileState status;
    private final FileScanStatus scanStatus;
    private final boolean legalHold;
    private final long deleteMarker;
    private final long version;

    public FileAggregate(
            long id,
            long tenantId,
            long objectId,
            String fileName,
            String declaredContentType,
            long fileSize,
            String sha256Hex,
            long ownerUserId,
            FileState status,
            FileScanStatus scanStatus,
            boolean legalHold,
            long deleteMarker,
            long version) {
        if (id <= 0 || tenantId <= 0 || objectId <= 0 || ownerUserId <= 0) {
            throw new IllegalArgumentException("id, tenantId, objectId and ownerUserId must be positive");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.objectId = objectId;
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.declaredContentType = Objects.requireNonNull(declaredContentType, "declaredContentType must not be null");
        this.fileSize = fileSize;
        this.sha256Hex = Objects.requireNonNull(sha256Hex, "sha256Hex must not be null");
        this.ownerUserId = ownerUserId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.scanStatus = Objects.requireNonNull(scanStatus, "scanStatus must not be null");
        this.legalHold = legalHold;
        this.deleteMarker = deleteMarker;
        this.version = version;
    }

    public long id() { return id; }
    public long tenantId() { return tenantId; }
    public long objectId() { return objectId; }
    public String fileName() { return fileName; }
    public String declaredContentType() { return declaredContentType; }
    public long fileSize() { return fileSize; }
    public String sha256Hex() { return sha256Hex; }
    public long ownerUserId() { return ownerUserId; }
    public FileState status() { return status; }
    public FileScanStatus scanStatus() { return scanStatus; }
    public boolean legalHold() { return legalHold; }
    public long deleteMarker() { return deleteMarker; }
    public long version() { return version; }
}
