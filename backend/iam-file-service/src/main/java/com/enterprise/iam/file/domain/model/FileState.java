package com.enterprise.iam.file.domain.model;

public enum FileState {
    VERIFYING,
    SCANNING,
    AVAILABLE,
    QUARANTINED,
    INFECTED,
    DELETED,
    ARCHIVED,
    PURGING,
    PURGED,
    FAILED
}
