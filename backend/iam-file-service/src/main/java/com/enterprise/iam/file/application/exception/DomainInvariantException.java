package com.enterprise.iam.file.application.exception;

public class DomainInvariantException extends FileResourceException {

    public DomainInvariantException(String message) {
        super("IAM_FILE_INVARIANT_VIOLATION", message);
    }
}
