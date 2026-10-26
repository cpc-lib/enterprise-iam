package com.enterprise.iam.file.infrastructure.persistence.mapper;

import com.enterprise.iam.file.application.model.ClaimedScan;
import com.enterprise.iam.file.application.model.ExpiredSession;
import com.enterprise.iam.file.application.model.FileAggregate;
import com.enterprise.iam.file.application.model.FileObject;
import com.enterprise.iam.file.application.model.FileReferenceRow;
import com.enterprise.iam.file.application.model.FileUploadPartRow;
import com.enterprise.iam.file.application.model.PurgeCandidate;
import com.enterprise.iam.file.application.model.TenantQuotaRow;
import com.enterprise.iam.file.application.model.UploadSession;
import com.enterprise.iam.file.domain.model.FileDrDrillRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface FilePersistenceMapper {

    int insertFileObject(@Param("id") long id, @Param("sha256Hex") String sha256Hex,
                         @Param("fileSize") long fileSize, @Param("bucketName") String bucketName,
                         @Param("objectKey") String objectKey, @Param("storageVersion") String storageVersion,
                         @Param("storageTier") String storageTier, @Param("status") String status,
                         @Param("referenceCount") long referenceCount, @Param("now") Instant now);

    FileObject findFileObjectById(@Param("objectId") long objectId);

    int incrementFileObjectReferenceCount(@Param("objectId") long objectId, @Param("now") Instant now);

    int decrementFileObjectReferenceCount(@Param("objectId") long objectId, @Param("now") Instant now);

    List<PurgeCandidate> findPurgeCandidates(@Param("retentionDue") Instant retentionDue,
                                             @Param("createdBeforeCutoff") Instant createdBeforeCutoff,
                                             @Param("limit") int limit);

    int countActiveReferences(@Param("tenantId") long tenantId, @Param("fileId") long fileId);

    int markFilePurging(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                        @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);

    int markObjectPurging(@Param("objectId") long objectId, @Param("now") Instant now);

    int markFilePurged(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                       @Param("now") Instant now);

    int markObjectPurged(@Param("objectId") long objectId, @Param("now") Instant now);

    int revertFilePurging(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                          @Param("now") Instant now);

    int revertObjectPurging(@Param("objectId") long objectId, @Param("now") Instant now);

    int insertFileReference(@Param("reference") FileReferenceRow reference, @Param("now") Instant now);

    FileReferenceRow findFileReferenceById(@Param("tenantId") long tenantId,
                                           @Param("referenceId") long referenceId);

    int insertFile(@Param("file") FileAggregate file, @Param("now") Instant now);

    FileAggregate findFileById(@Param("tenantId") long tenantId, @Param("fileId") long fileId);

    int deleteFileLogical(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                          @Param("deleteMarker") long deleteMarker,
                          @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);

    List<Long> findPendingScanIds(@Param("limit") int limit);

    int claimPendingScan(@Param("fileId") long fileId, @Param("now") Instant now);

    int claimRetryScan(@Param("fileId") long fileId, @Param("tenantId") long tenantId,
                       @Param("now") Instant now);

    List<RetryCandidateRow> findRetryCandidates(@Param("limit") int limit);

    int countExhaustedScans(@Param("maxAttempts") int maxAttempts);

    ClaimedScan findClaimedScan(@Param("fileId") long fileId, @Param("attempt") int attempt);

    int insertScanStart(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("fileId") long fileId, @Param("attempt") int attempt,
                        @Param("scanner") String scanner, @Param("startedAt") Instant startedAt,
                        @Param("now") Instant now);

    int completeScanRecord(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                           @Param("attempt") int attempt, @Param("result") String result,
                           @Param("threatCode") String threatCode, @Param("errorCode") String errorCode,
                           @Param("signatureVersion") String signatureVersion,
                           @Param("completedAt") Instant completedAt);

    int markFileScanFailed(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                           @Param("now") Instant now);

    int transitionFileScanTerminal(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                                   @Param("status") String status, @Param("scanStatus") String scanStatus,
                                   @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);

    int upsertUploadPart(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("uploadSessionId") long uploadSessionId, @Param("partNumber") int partNumber,
                         @Param("partSize") long partSize, @Param("partSha256Hex") String partSha256Hex,
                         @Param("storageEtag") String storageEtag, @Param("status") String status,
                         @Param("uploadedAt") Instant uploadedAt, @Param("now") Instant now);

    List<FileUploadPartRow> findUploadParts(@Param("tenantId") long tenantId,
                                            @Param("uploadSessionId") long uploadSessionId);

    FileUploadPartRow findUploadPart(@Param("tenantId") long tenantId,
                                     @Param("uploadSessionId") long uploadSessionId,
                                     @Param("partNumber") int partNumber);

    int insertUploadSession(@Param("session") UploadSession session, @Param("now") Instant now);

    UploadSessionRow findUploadSession(@Param("tenantId") long tenantId, @Param("uploadId") long uploadId);

    int markUploadSessionStatus(@Param("tenantId") long tenantId, @Param("uploadId") long uploadId,
                                @Param("status") String status, @Param("completedFileId") Long completedFileId,
                                @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);

    int incrementUploadedPartCount(@Param("tenantId") long tenantId, @Param("uploadId") long uploadId,
                                   @Param("now") Instant now);

    int abortUploadSession(@Param("tenantId") long tenantId, @Param("uploadId") long uploadId,
                           @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);

    List<TenantQuotaRow> findAllTenantQuotas();

    long sumLogicalUsage(@Param("tenantId") long tenantId);

    long sumReservedBytes(@Param("tenantId") long tenantId);

    int adjustQuota(@Param("tenantId") long tenantId, @Param("usedLogicalBytes") long usedLogicalBytes,
                    @Param("reservedBytes") long reservedBytes, @Param("expectedVersion") long expectedVersion,
                    @Param("now") Instant now);

    List<ExpiredSession> findExpiredSessions(@Param("now") Instant now, @Param("limit") int limit);

    int markUploadSessionExpired(@Param("tenantId") long tenantId, @Param("sessionId") long sessionId,
                                 @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);

    int releaseQuotaReservation(@Param("tenantId") long tenantId, @Param("bytes") long bytes,
                                @Param("now") Instant now);

    int updateFileDlpOutcome(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                             @Param("dlpStatus") String dlpStatus, @Param("status") String status,
                             @Param("now") Instant now);

    int updateFileStorageTier(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                              @Param("storageTier") String storageTier, @Param("status") String status,
                              @Param("now") Instant now);

    int insertDrDrillLog(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("scenarioCode") String scenarioCode,
                         @Param("targetRpoSeconds") int targetRpoSeconds,
                         @Param("actualRpoSeconds") int actualRpoSeconds,
                         @Param("targetRtoSeconds") int targetRtoSeconds,
                         @Param("actualRtoSeconds") int actualRtoSeconds,
                         @Param("verifiedObjects") int verifiedObjects,
                         @Param("status") String status,
                         @Param("executedAt") Instant executedAt);

    List<FileDrDrillRecord> findDrDrillsByTenant(@Param("tenantId") long tenantId);

    record RetryCandidateRow(
            long fileId,
            long tenantId,
            String fileName,
            String declaredContentType,
            long fileSize,
            String sha256Hex,
            long version,
            int lastAttempt,
            Instant lastCompletedAt) {
    }

    record UploadSessionRow(
            long id,
            long tenantId,
            long userId,
            String fileName,
            long fileSize,
            String wholeSha256Hex,
            String declaredContentType,
            String storageUploadId,
            String bucketName,
            String objectKey,
            long partSize,
            int partCount,
            int uploadedPartCount,
            String status,
            Instant expireAt,
            Long completedFileId,
            long quotaReservationBytes,
            long version) {
    }
}
