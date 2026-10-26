package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.command.AbortUploadCommand;
import com.enterprise.iam.file.application.command.CompleteUploadCommand;
import com.enterprise.iam.file.application.command.CreateFileReferenceCommand;
import com.enterprise.iam.file.application.command.CreateUploadSessionCommand;
import com.enterprise.iam.file.application.command.DeleteFileCommand;
import com.enterprise.iam.file.application.command.FileAccessCommand;
import com.enterprise.iam.file.application.command.GetUploadSessionCommand;
import com.enterprise.iam.file.application.command.ReportPartCommand;
import com.enterprise.iam.file.application.command.SignPartCommand;
import com.enterprise.iam.file.application.exception.FileLockedException;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.model.FileAccessResult;
import com.enterprise.iam.file.application.model.FileAggregate;
import com.enterprise.iam.file.application.model.FileObject;
import com.enterprise.iam.file.application.model.FileReferenceRow;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.model.FileUploadPartRow;
import com.enterprise.iam.file.application.model.SignPartResult;
import com.enterprise.iam.file.application.model.UploadSession;
import com.enterprise.iam.file.application.model.UploadSessionResult;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.application.port.out.FileReferenceRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.FileUploadPartRepository;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileAccessMode;
import com.enterprise.iam.file.domain.model.FileObjectStatus;
import com.enterprise.iam.file.domain.model.FileReferenceStatus;
import com.enterprise.iam.file.domain.model.FileReferenceType;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.UploadMode;
import com.enterprise.iam.file.domain.model.UploadPartStatus;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private StubPositiveIdGenerator ids;
    private StubSessionRepository sessions;
    private StubPartRepository parts;
    private StubObjectRepository objects;
    private StubFileRepository files;
    private StubReferenceRepository references;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ids = new StubPositiveIdGenerator();
        sessions = new StubSessionRepository();
        parts = new StubPartRepository();
        objects = new StubObjectRepository();
        files = new StubFileRepository();
        references = new StubReferenceRepository();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void createFileUploadSessionInstantMode() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var cmd = new CreateUploadSessionCommand(7L, 11L, "doc.txt", 1024L, SHA,
                "text/plain", 100L, "invoice-1", null, "trace-1");
        UploadSessionResult result = useCase.execute(cmd);
        assertThat(result.mode()).isEqualTo(UploadMode.INSTANT);
        assertThat(result.fileId()).isPositive();
        assertThat(result.uploadId()).isNull();
        assertThat(result.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        assertThat(files.store).hasSize(1);
        assertThat(objects.store).hasSize(1);
    }

    @Test
    void createFileUploadSessionMultipartMode() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var cmd = new CreateUploadSessionCommand(7L, 11L, "doc.txt", 10L * 1024 * 1024, SHA,
                "text/plain", 100L, "invoice-1", null, "trace-1");
        UploadSessionResult result = useCase.execute(cmd);
        assertThat(result.mode()).isEqualTo(UploadMode.MULTIPART);
        assertThat(result.uploadId()).isPositive();
        assertThat(result.fileId()).isNull();
        assertThat(result.status()).isEqualTo(UploadSessionStatus.INIT);
        assertThat(result.partSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(result.partCount()).isEqualTo(2);
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void signPartUploadIssuesScopedUrl() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var created = useCase.execute(new CreateUploadSessionCommand(7L, 11L, "doc.txt",
                10L * 1024 * 1024, SHA, "text/plain", 100L, "invoice-1", null, "trace-1"));
        var signUseCase = new SignPartUploadUseCase(sessions, clock);
        SignPartResult result = signUseCase.execute(new SignPartCommand(7L, created.uploadId(),
                1, created.version(), 5L * 1024 * 1024, null, "trace-2"));
        assertThat(result.uploadUrl()).startsWith("https://storage.local/upload/");
        assertThat(result.requiredHeaders()).containsEntry("Content-Type", "application/octet-stream");
        assertThat(result.requiredHeaders()).containsEntry("Content-Length", String.valueOf(5L * 1024 * 1024));
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(SignPartUploadUseCase.SIGN_TTL));
    }

    @Test
    void reportFileUploadPartRecordsIdempotently() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var created = useCase.execute(new CreateUploadSessionCommand(7L, 11L, "doc.txt",
                10L * 1024 * 1024, SHA, "text/plain", 100L, "invoice-1", null, "trace-1"));
        var reportUseCase = new ReportFileUploadPartUseCase(parts, sessions, ids, clock);
        reportUseCase.execute(new ReportPartCommand(7L, created.uploadId(), 1, created.version(),
                "etag-1", 5L * 1024 * 1024, null, "trace-3"));
        assertThat(parts.store).hasSize(1);
        assertThat(parts.store.values().iterator().next().status()).isEqualTo(UploadPartStatus.UPLOADED);
    }

    @Test
    void completeFileUploadCreatesVerifyingFile() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var created = useCase.execute(new CreateUploadSessionCommand(7L, 11L, "doc.txt",
                10L * 1024 * 1024, SHA, "text/plain", 100L, "invoice-1", null, "trace-1"));
        var completeUseCase = new CompleteFileUploadUseCase(sessions, objects, files, ids);
        FileResult result = completeUseCase.execute(new CompleteUploadCommand(7L, created.uploadId(),
                created.version(), SHA,
                List.of(new CompleteUploadCommand.PartEtag(1, "etag-1"),
                        new CompleteUploadCommand.PartEtag(2, "etag-2")),
                "trace-4"));
        assertThat(result.status()).isEqualTo(FileState.VERIFYING);
        assertThat(result.scanStatus()).isEqualTo(FileScanStatus.PENDING);
        assertThat(result.version()).isZero();
        assertThat(objects.store).hasSize(1);
        assertThat(files.store).hasSize(1);
        assertThat(sessions.find(7L, created.uploadId()).get().status()).isEqualTo(UploadSessionStatus.COMPLETED);
    }

    @Test
    void getFileUploadSessionReflectsUploadedParts() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var created = useCase.execute(new CreateUploadSessionCommand(7L, 11L, "doc.txt",
                10L * 1024 * 1024, SHA, "text/plain", 100L, "invoice-1", null, "trace-1"));
        new ReportFileUploadPartUseCase(parts, sessions, ids, clock).execute(
                new ReportPartCommand(7L, created.uploadId(), 1, created.version(),
                        "etag-1", 5L * 1024 * 1024, null, "trace-3"));
        var getUseCase = new GetFileUploadSessionUseCase(sessions, parts);
        UploadSessionResult result = getUseCase.execute(new GetUploadSessionCommand(7L, created.uploadId(), "trace-5"));
        assertThat(result.mode()).isEqualTo(UploadMode.MULTIPART);
        assertThat(result.uploadedParts()).containsExactly(1);
        assertThat(result.partCount()).isEqualTo(2);
    }

    @Test
    void abortFileUploadSessionReleasesQuota() {
        var useCase = new CreateFileUploadSessionUseCase(sessions, objects, files, ids, clock);
        var created = useCase.execute(new CreateUploadSessionCommand(7L, 11L, "doc.txt",
                10L * 1024 * 1024, SHA, "text/plain", 100L, "invoice-1", null, "trace-1"));
        var abortUseCase = new AbortFileUploadSessionUseCase(sessions);
        abortUseCase.execute(new AbortUploadCommand(7L, created.uploadId(), created.version(), "trace-6"));
        assertThat(sessions.find(7L, created.uploadId()).get().status()).isEqualTo(UploadSessionStatus.ABORTED);
    }

    @Test
    void createFileReferenceIncrementsObjectCount() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.AVAILABLE, FileScanStatus.CLEAN, false, 0L, 0L));
        objects.store.put(201L, new FileObject(201L, SHA, 1024L, "iam-file-local",
                "k", null, "STANDARD", FileObjectStatus.ACTIVE, 1L));
        var useCase = new CreateFileReferenceUseCase(references, files, objects, ids);
        var result = useCase.execute(new CreateFileReferenceCommand(7L, 101L, 100L,
                "invoice-1", FileReferenceType.ATTACHMENT, null, 0, "trace-7"));
        assertThat(result.id()).isPositive();
        assertThat(result.status()).isEqualTo(FileReferenceStatus.ACTIVE.name());
        assertThat(references.store).hasSize(1);
        assertThat(objects.store.get(201L).referenceCount()).isEqualTo(2L);
    }

    @Test
    void authorizeFileDownloadReturnsProxyUrl() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.AVAILABLE, FileScanStatus.CLEAN, false, 0L, 0L));
        var useCase = new AuthorizeFileDownloadUseCase(files, ids, clock);
        FileAccessResult result = useCase.execute(new FileAccessCommand(7L, 101L, 100L, "invoice-1",
                null, FileAccessMode.AUTO, false, "trace-8"));
        assertThat(result.mode()).isEqualTo(FileAccessMode.PROXY);
        assertThat(result.accessUrl()).startsWith("https://storage.local/download/");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(AuthorizeFileDownloadUseCase.DOWNLOAD_TTL));
        assertThat(result.auditId()).isPositive();
    }

    @Test
    void authorizeFilePreviewReturnsSandboxMode() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.AVAILABLE, FileScanStatus.CLEAN, false, 0L, 0L));
        var useCase = new AuthorizeFilePreviewUseCase(files, ids, clock);
        FileAccessResult result = useCase.execute(new FileAccessCommand(7L, 101L, 100L, "invoice-1",
                null, FileAccessMode.AUTO, true, "trace-9"));
        assertThat(result.mode()).isEqualTo(FileAccessMode.SANDBOX_PREVIEW);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(AuthorizeFilePreviewUseCase.PREVIEW_TTL));
    }

    @Test
    void authorizeFileDownloadRejectsLockedFile() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.VERIFYING, FileScanStatus.PENDING, false, 0L, 0L));
        var useCase = new AuthorizeFileDownloadUseCase(files, ids, clock);
        assertThatThrownBy(() -> useCase.execute(new FileAccessCommand(7L, 101L, 100L, "invoice-1",
                null, FileAccessMode.AUTO, false, "trace-10")))
                .isInstanceOf(FileLockedException.class);
    }

    @Test
    void deleteLogicalFileSetsDeletedMarkerAndDecrementsObject() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.AVAILABLE, FileScanStatus.CLEAN, false, 0L, 0L));
        objects.store.put(201L, new FileObject(201L, SHA, 1024L, "iam-file-local",
                "k", null, "STANDARD", FileObjectStatus.ACTIVE, 1L));
        var useCase = new DeleteLogicalFileUseCase(files, objects, ids);
        FileResult result = useCase.execute(new DeleteFileCommand(7L, 101L, 0L, "policy", "trace-11"));
        assertThat(result.status()).isEqualTo(FileState.DELETED);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(objects.store.get(201L).referenceCount()).isZero();
    }

    @Test
    void deleteLogicalFileRejectsLegalHold() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.AVAILABLE, FileScanStatus.CLEAN, true, 0L, 0L));
        var useCase = new DeleteLogicalFileUseCase(files, objects, ids);
        assertThatThrownBy(() -> useCase.execute(new DeleteFileCommand(7L, 101L, 0L, "policy", "trace-12")))
                .isInstanceOf(FileLockedException.class);
    }

    @Test
    void deleteLogicalFileRejectsVersionConflict() {
        files.store.put(101L, new FileAggregate(101L, 7L, 201L, "doc.txt", "text/plain", 1024L,
                SHA, 11L, FileState.AVAILABLE, FileScanStatus.CLEAN, false, 0L, 1L));
        var useCase = new DeleteLogicalFileUseCase(files, objects, ids);
        assertThatThrownBy(() -> useCase.execute(new DeleteFileCommand(7L, 101L, 0L, "policy", "trace-13")))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    void signPartUploadRejectsMissingSession() {
        var signUseCase = new SignPartUploadUseCase(sessions, clock);
        assertThatThrownBy(() -> signUseCase.execute(new SignPartCommand(7L, 999L, 1, 0L, 5L * 1024 * 1024,
                null, "trace-14")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class StubPositiveIdGenerator implements PositiveIdGenerator {
        private final AtomicLong counter = new AtomicLong(1000);

        @Override
        public long nextId() {
            return counter.incrementAndGet();
        }
    }

    private static class StubSessionRepository implements FileUploadSessionRepository {
        final Map<Long, UploadSession> store = new HashMap<>();

        @Override
        public UploadSession save(UploadSession session) {
            store.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<UploadSession> findById(long tenantId, long uploadId) {
            return Optional.ofNullable(store.get(uploadId));
        }

        @Override
        public void markStatus(long tenantId, long uploadId, UploadSessionStatus status,
                               Long completedFileId, long expectedVersion) {
            UploadSession current = store.get(uploadId);
            if (current == null || current.version() != expectedVersion) {
                throw new IllegalStateException("version mismatch");
            }
            store.put(uploadId, copyWith(current, status, completedFileId, current.version() + 1));
        }

        @Override
        public void incrementUploadedPartCount(long tenantId, long uploadId) {
            UploadSession current = store.get(uploadId);
            if (current == null) {
                throw new IllegalStateException("session missing");
            }
            UploadSessionStatus next = current.status() == UploadSessionStatus.INIT
                    ? UploadSessionStatus.UPLOADING : current.status();
            store.put(uploadId, new UploadSession(
                    current.id(), current.tenantId(), current.userId(), current.fileName(), current.fileSize(),
                    current.wholeSha256Hex(), current.declaredContentType(), current.storageUploadId(),
                    current.bucketName(), current.objectKey(), current.partSize(), current.partCount(),
                    current.uploadedPartCount() + 1, next, current.expireAt(), current.completedFileId(),
                    current.quotaReservationBytes(), current.version() + 1, current.uploadedParts()));
        }

        @Override
        public void abort(long tenantId, long uploadId, long expectedVersion) {
            UploadSession current = store.get(uploadId);
            if (current == null || current.version() != expectedVersion) {
                throw new IllegalStateException("version mismatch");
            }
            store.put(uploadId, copyWith(current, UploadSessionStatus.ABORTED, current.completedFileId(),
                    current.version() + 1));
        }

        Optional<UploadSession> find(long tenantId, long uploadId) {
            return findById(tenantId, uploadId);
        }

        private UploadSession copyWith(UploadSession s, UploadSessionStatus status, Long completedFileId,
                                      long version) {
            return new UploadSession(s.id(), s.tenantId(), s.userId(), s.fileName(), s.fileSize(),
                    s.wholeSha256Hex(), s.declaredContentType(), s.storageUploadId(), s.bucketName(),
                    s.objectKey(), s.partSize(), s.partCount(), s.uploadedPartCount(), status, s.expireAt(),
                    completedFileId, s.quotaReservationBytes(), version, s.uploadedParts());
        }
    }

    private static class StubPartRepository implements FileUploadPartRepository {
        final Map<String, FileUploadPartRow> store = new HashMap<>();

        @Override
        public void upsertUploaded(long id, long tenantId, long uploadSessionId, int partNumber, long partSize,
                                   String partSha256Hex, String storageEtag, UploadPartStatus status,
                                   Instant uploadedAt) {
            String key = tenantId + ":" + uploadSessionId + ":" + partNumber;
            store.put(key, new FileUploadPartRow(id, tenantId, uploadSessionId, partNumber, partSize,
                    partSha256Hex, storageEtag, status, uploadedAt, 0L));
        }

        @Override
        public List<FileUploadPartRow> findBySession(long tenantId, long uploadSessionId) {
            return store.values().stream()
                    .filter(p -> p.tenantId() == tenantId && p.uploadSessionId() == uploadSessionId)
                    .toList();
        }

        @Override
        public Optional<FileUploadPartRow> findBySessionAndPartNumber(long tenantId, long uploadSessionId,
                                                                      int partNumber) {
            return Optional.ofNullable(store.get(tenantId + ":" + uploadSessionId + ":" + partNumber));
        }
    }

    private static class StubObjectRepository implements FileObjectRepository {
        final Map<Long, FileObject> store = new HashMap<>();

        @Override
        public FileObject save(FileObject fileObject) {
            store.put(fileObject.id(), fileObject);
            return fileObject;
        }

        @Override
        public Optional<FileObject> findById(long objectId) {
            return Optional.ofNullable(store.get(objectId));
        }

        @Override
        public void incrementReferenceCount(long objectId) {
            FileObject current = store.get(objectId);
            store.put(objectId, withCount(current, current.referenceCount() + 1));
        }

        @Override
        public void decrementReferenceCount(long objectId) {
            FileObject current = store.get(objectId);
            store.put(objectId, withCount(current, Math.max(0, current.referenceCount() - 1)));
        }

        private FileObject withCount(FileObject o, long count) {
            return new FileObject(o.id(), o.sha256Hex(), o.fileSize(), o.bucketName(), o.objectKey(),
                    o.storageVersion(), o.storageTier(), o.status(), count);
        }
    }

    private static class StubFileRepository implements FileRepository {
        final Map<Long, FileAggregate> store = new HashMap<>();

        @Override
        public FileAggregate save(FileAggregate file) {
            store.put(file.id(), file);
            return file;
        }

        @Override
        public Optional<FileAggregate> findById(long tenantId, long fileId) {
            return Optional.ofNullable(store.get(fileId));
        }

        @Override
        public int deleteLogical(long tenantId, long fileId, long deleteMarker, long expectedVersion) {
            FileAggregate current = store.get(fileId);
            if (current == null || current.version() != expectedVersion) {
                return 0;
            }
            store.put(fileId, new FileAggregate(current.id(), current.tenantId(), current.objectId(),
                    current.fileName(), current.declaredContentType(), current.fileSize(), current.sha256Hex(),
                    current.ownerUserId(), FileState.DELETED, current.scanStatus(), current.legalHold(),
                    deleteMarker, expectedVersion + 1));
            return 1;
        }
    }

    private static class StubReferenceRepository implements FileReferenceRepository {
        final Map<Long, FileReferenceRow> store = new HashMap<>();

        @Override
        public FileReferenceRow save(FileReferenceRow reference) {
            store.put(reference.id(), reference);
            return reference;
        }

        @Override
        public Optional<FileReferenceRow> findById(long tenantId, long referenceId) {
            return Optional.ofNullable(store.get(referenceId));
        }
    }
}
