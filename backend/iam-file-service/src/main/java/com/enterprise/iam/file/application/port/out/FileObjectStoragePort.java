package com.enterprise.iam.file.application.port.out;

/**
 * Physical object storage operations used by file lifecycle workers. V1 ships a logging
 * no-op implementation (DEV default) and a real MinIO/S3 deleting adapter (D-18).
 */
public interface FileObjectStoragePort {

    /**
     * Permanently deletes the object. Implementations MUST throw a runtime exception when
     * the remote deletion did not succeed so the purge transaction can be retried
     * (SPEC 35 §73/§74).
     */
    void delete(String bucketName, String objectKey);
}
