package com.enterprise.iam.file.application.exception;

public abstract class FileResourceException extends RuntimeException {

    private final String code;

    protected FileResourceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
