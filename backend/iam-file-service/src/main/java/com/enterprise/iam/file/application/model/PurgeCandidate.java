package com.enterprise.iam.file.application.model;

/**
 * A logically-deleted file eligible for physical purge (SPEC 35 §38): retention reached,
 * no legal hold; active references are recalculated by the use case before deletion.
 */
public record PurgeCandidate(
        long tenantId,
        long fileId,
        long objectId,
        String bucketName,
        String objectKey,
        long version) {
}
