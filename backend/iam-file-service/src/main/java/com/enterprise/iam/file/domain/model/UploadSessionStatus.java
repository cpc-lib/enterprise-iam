package com.enterprise.iam.file.domain.model;

public enum UploadSessionStatus {
    INIT,
    UPLOADING,
    VERIFYING,
    MERGING,
    COMPLETED,
    FAILED,
    ABORTED,
    EXPIRED
}
