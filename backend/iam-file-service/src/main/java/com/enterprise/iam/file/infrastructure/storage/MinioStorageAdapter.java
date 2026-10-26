package com.enterprise.iam.file.infrastructure.storage;

import com.enterprise.iam.file.application.port.out.FileObjectStoragePort;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;

import java.util.Objects;

/**
 * Production {@link FileObjectStoragePort}: permanently deletes the object through the
 * MinIO/S3 API. Any remote failure is wrapped into an {@link IllegalStateException} so the
 * purge worker rolls PURGING back and retries the candidate on a later tick
 * (SPEC 35 §73/§74). S3 delete semantics make removing an already-absent object a
 * successful no-op, which keeps the purge transaction idempotent.
 */
public final class MinioStorageAdapter implements FileObjectStoragePort {

    private final MinioClient client;

    public MinioStorageAdapter(MinioClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "remote object delete failed for bucket=" + bucketName + " objectKey=" + objectKey, ex);
        }
    }
}
