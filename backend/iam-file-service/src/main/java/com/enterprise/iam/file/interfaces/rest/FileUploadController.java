package com.enterprise.iam.file.interfaces.rest;

import com.enterprise.iam.file.application.command.AbortUploadCommand;
import com.enterprise.iam.file.application.command.CompleteUploadCommand;
import com.enterprise.iam.file.application.command.CreateUploadSessionCommand;
import com.enterprise.iam.file.application.command.GetUploadSessionCommand;
import com.enterprise.iam.file.application.command.ReportPartCommand;
import com.enterprise.iam.file.application.command.SignPartCommand;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.model.SignPartResult;
import com.enterprise.iam.file.application.model.UploadSessionResult;
import com.enterprise.iam.file.application.service.AbortFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.CompleteFileUploadUseCase;
import com.enterprise.iam.file.application.service.CreateFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.GetFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.ReportFileUploadPartUseCase;
import com.enterprise.iam.file.application.service.SignPartUploadUseCase;
import com.enterprise.iam.file.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.file.interfaces.rest.dto.CompleteUploadRequest;
import com.enterprise.iam.file.interfaces.rest.dto.CreateUploadRequest;
import com.enterprise.iam.file.interfaces.rest.dto.FileResponse;
import com.enterprise.iam.file.interfaces.rest.dto.ReportPartRequest;
import com.enterprise.iam.file.interfaces.rest.dto.SignPartRequest;
import com.enterprise.iam.file.interfaces.rest.dto.SignPartResponse;
import com.enterprise.iam.file.interfaces.rest.dto.UploadSessionResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/file-uploads")
@ConditionalOnProperty(prefix = "iam.file", name = "enabled", havingValue = "true")
public class FileUploadController {

    private final CreateFileUploadSessionUseCase createUseCase;
    private final SignPartUploadUseCase signUseCase;
    private final ReportFileUploadPartUseCase reportUseCase;
    private final CompleteFileUploadUseCase completeUseCase;
    private final GetFileUploadSessionUseCase getUseCase;
    private final AbortFileUploadSessionUseCase abortUseCase;

    public FileUploadController(
            CreateFileUploadSessionUseCase createUseCase,
            SignPartUploadUseCase signUseCase,
            ReportFileUploadPartUseCase reportUseCase,
            CompleteFileUploadUseCase completeUseCase,
            GetFileUploadSessionUseCase getUseCase,
            AbortFileUploadSessionUseCase abortUseCase) {
        this.createUseCase = createUseCase;
        this.signUseCase = signUseCase;
        this.reportUseCase = reportUseCase;
        this.completeUseCase = completeUseCase;
        this.getUseCase = getUseCase;
        this.abortUseCase = abortUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UploadSessionResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateUploadRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new CreateUploadSessionCommand(
                tenantId,
                userId,
                request.fileName(),
                request.fileSize(),
                request.sha256(),
                request.declaredContentType(),
                request.resourceId(),
                request.resourceInstanceKey(),
                request.fieldId(),
                traceId);
        UploadSessionResult result = createUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(toDto(result), traceId));
    }

    @PostMapping("/{uploadId}/parts/{partNumber}/sign")
    public ResponseEntity<ApiResponse<SignPartResponse>> signPart(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long uploadId,
            @PathVariable int partNumber,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SignPartRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new SignPartCommand(
                tenantId,
                uploadId,
                partNumber,
                request.version(),
                request.expectedSize(),
                request.partSha256(),
                traceId);
        SignPartResult result = signUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(toDto(result), traceId));
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ResponseEntity<Void> reportPart(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long uploadId,
            @PathVariable int partNumber,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReportPartRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new ReportPartCommand(
                tenantId,
                uploadId,
                partNumber,
                request.version(),
                request.storageEtag(),
                request.partSize(),
                request.partSha256(),
                traceId);
        reportUseCase.execute(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<ApiResponse<FileResponse>> complete(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long uploadId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CompleteUploadRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new CompleteUploadCommand(
                tenantId,
                uploadId,
                request.version(),
                request.wholeSha256(),
                request.parts().stream()
                        .map(p -> new CompleteUploadCommand.PartEtag(p.partNumber(), p.storageEtag()))
                        .toList(),
                traceId);
        FileResult result = completeUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(toDto(result), traceId));
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> getSession(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long uploadId) {
        String traceId = UUID.randomUUID().toString();
        var command = new GetUploadSessionCommand(tenantId, uploadId, traceId);
        UploadSessionResult result = getUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(toDto(result), traceId));
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> abort(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long uploadId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
            @Valid @RequestBody(required = false) AbortRequest body) {
        String traceId = UUID.randomUUID().toString();
        long version = ifMatchVersion != null ? ifMatchVersion
                : (body != null && body.version() != null ? body.version() : 0L);
        var command = new AbortUploadCommand(tenantId, uploadId, version, traceId);
        abortUseCase.execute(command);
        return ResponseEntity.noContent().build();
    }

    public record AbortRequest(Long version) {
    }

    private UploadSessionResponse toDto(UploadSessionResult r) {
        List<Integer> uploadedParts = r.uploadedParts() == null ? List.of() : r.uploadedParts();
        return new UploadSessionResponse(
                r.mode(),
                r.uploadId(),
                r.fileId(),
                r.status(),
                r.partSize(),
                r.partCount(),
                uploadedParts,
                r.expiresAt(),
                r.version());
    }

    private SignPartResponse toDto(SignPartResult r) {
        Map<String, String> headers = r.requiredHeaders() == null ? Map.of() : r.requiredHeaders();
        return new SignPartResponse(r.uploadUrl(), r.expiresAt(), headers);
    }

    private FileResponse toDto(FileResult r) {
        return new FileResponse(r.fileId(), r.status(), r.scanStatus(), r.version());
    }
}
