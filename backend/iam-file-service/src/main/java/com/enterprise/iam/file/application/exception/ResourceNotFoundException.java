package com.enterprise.iam.file.application.exception;

public class ResourceNotFoundException extends FileResourceException {

    public ResourceNotFoundException(String message) {
        super("IAM_FILE_NOT_FOUND", message);
    }
}
