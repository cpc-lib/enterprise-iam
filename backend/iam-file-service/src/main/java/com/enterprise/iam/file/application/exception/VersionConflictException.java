package com.enterprise.iam.file.application.exception;

public class VersionConflictException extends FileResourceException {

    public VersionConflictException(String message) {
        super("IAM_FILE_VERSION_CONFLICT", message);
    }
}
