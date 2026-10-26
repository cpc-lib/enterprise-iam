package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.FileReferenceStatus;
import com.enterprise.iam.file.domain.model.FileReferenceType;

import java.util.Objects;

public final class FileReferenceRow {

    private final long id;
    private final long tenantId;
    private final long fileId;
    private final long resourceId;
    private final String resourceInstanceKey;
    private final FileReferenceType referenceType;
    private final long fieldId;
    private final int sortOrder;
    private final FileReferenceStatus status;
    private final Integer activeSlot;
    private final long version;

    public FileReferenceRow(
            long id,
            long tenantId,
            long fileId,
            long resourceId,
            String resourceInstanceKey,
            FileReferenceType referenceType,
            long fieldId,
            int sortOrder,
            FileReferenceStatus status,
            Integer activeSlot,
            long version) {
        if (id <= 0 || tenantId <= 0 || fileId <= 0 || resourceId <= 0) {
            throw new IllegalArgumentException("id, tenantId, fileId and resourceId must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.fileId = fileId;
        this.resourceId = resourceId;
        this.resourceInstanceKey = Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        this.referenceType = Objects.requireNonNull(referenceType, "referenceType must not be null");
        this.fieldId = fieldId;
        this.sortOrder = sortOrder;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.activeSlot = activeSlot;
        this.version = version;
    }

    public long id() { return id; }
    public long tenantId() { return tenantId; }
    public long fileId() { return fileId; }
    public long resourceId() { return resourceId; }
    public String resourceInstanceKey() { return resourceInstanceKey; }
    public FileReferenceType referenceType() { return referenceType; }
    public long fieldId() { return fieldId; }
    public int sortOrder() { return sortOrder; }
    public FileReferenceStatus status() { return status; }
    public Integer activeSlot() { return activeSlot; }
    public long version() { return version; }
}
