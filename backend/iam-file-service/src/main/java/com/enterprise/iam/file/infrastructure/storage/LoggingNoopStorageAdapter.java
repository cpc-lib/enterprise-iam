package com.enterprise.iam.file.infrastructure.storage;

import com.enterprise.iam.file.application.port.out.FileObjectStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DEV-ONLY {@link FileObjectStoragePort}: the purge worker logs the object it would delete
 * and reports success, letting the full DELETED -&gt; PURGING -&gt; PURGED lifecycle run end
 * to end without a remote object store. Production purge selects {@link MinioStorageAdapter}
 * via {@code iam.file.purge.storage=minio}.
 */
public final class LoggingNoopStorageAdapter implements FileObjectStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNoopStorageAdapter.class);

    @Override
    public void delete(String bucketName, String objectKey) {
        log.info("would delete objectKey={} from bucket={} (logging no-op storage adapter; no real object is deleted)",
                objectKey, bucketName);
    }
}
