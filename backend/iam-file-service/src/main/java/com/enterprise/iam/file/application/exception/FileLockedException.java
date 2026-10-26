package com.enterprise.iam.file.application.exception;

public class FileLockedException extends FileResourceException {

    public FileLockedException(String message) {
        super("IAM_FILE_LOCKED", message);
    }
}
