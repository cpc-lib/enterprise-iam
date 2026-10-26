package com.enterprise.iam.file.interfaces.rest;

import com.enterprise.iam.file.application.command.CreateFileReferenceCommand;
import com.enterprise.iam.file.application.command.DeleteFileCommand;
import com.enterprise.iam.file.application.command.FileAccessCommand;
import com.enterprise.iam.file.application.model.EntityResult;
import com.enterprise.iam.file.application.model.FileAccessResult;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.service.AuthorizeFileDownloadUseCase;
import com.enterprise.iam.file.application.service.AuthorizeFilePreviewUseCase;
import com.enterprise.iam.file.application.service.CreateFileReferenceUseCase;
import com.enterprise.iam.file.application.service.DeleteLogicalFileUseCase;
import com.enterprise.iam.file.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.file.interfaces.rest.dto.CreateFileReferenceRequest;
import com.enterprise.iam.file.interfaces.rest.dto.DeleteFileRequest;
import com.enterprise.iam.file.interfaces.rest.dto.EntityResponse;
import com.enterprise.iam.file.interfaces.rest.dto.FileAccessRequest;
import com.enterprise.iam.file.interfaces.rest.dto.FileAccessResponse;
import com.enterprise.iam.file.interfaces.rest.dto.FileResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@ConditionalOnProperty(prefix = "iam.file", name = "enabled", havingValue = "true")
public class FileReferenceController {

    private final CreateFileReferenceUseCase createReferenceUseCase;
    private final AuthorizeFileDownloadUseCase downloadUseCase;
    private final AuthorizeFilePreviewUseCase previewUseCase;
    private final DeleteLogicalFileUseCase deleteUseCase;

    public FileReferenceController(
            CreateFileReferenceUseCase createReferenceUseCase,
            AuthorizeFileDownloadUseCase downloadUseCase,
            AuthorizeFilePreviewUseCase previewUseCase,
            DeleteLogicalFileUseCase deleteUseCase) {
        this.createReferenceUseCase = createReferenceUseCase;
        this.downloadUseCase = downloadUseCase;
        this.previewUseCase = previewUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Object>>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.ok(List.of(), traceId));
    }

    @PostMapping("/{fileId}/references")
    public ResponseEntity<ApiResponse<EntityResponse>> createReference(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long fileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateFileReferenceRequest request) {
        String traceId = UUID.randomUUID().toString();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        var command = new CreateFileReferenceCommand(
                tenantId,
                fileId,
                request.resourceId(),
                request.resourceInstanceKey(),
                request.referenceType(),
                request.fieldId(),
                sortOrder,
                traceId);
        EntityResult result = createReferenceUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(new EntityResponse(result.id(), result.version(), result.status()), traceId));
    }

    @PostMapping("/{fileId}/download")
    public ResponseEntity<ApiResponse<FileAccessResponse>> download(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long fileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody FileAccessRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new FileAccessCommand(
                tenantId,
                fileId,
                request.resourceId(),
                request.resourceInstanceKey(),
                request.referenceId(),
                request.requestedMode(),
                false,
                traceId);
        FileAccessResult result = downloadUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(toDto(result), traceId));
    }

    @PostMapping("/{fileId}/preview")
    public ResponseEntity<ApiResponse<FileAccessResponse>> preview(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long fileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody FileAccessRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new FileAccessCommand(
                tenantId,
                fileId,
                request.resourceId(),
                request.resourceInstanceKey(),
                request.referenceId(),
                request.requestedMode(),
                true,
                traceId);
        FileAccessResult result = previewUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(toDto(result), traceId));
    }

    @PostMapping("/{fileId}/delete")
    public ResponseEntity<ApiResponse<FileResponse>> delete(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long fileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DeleteFileRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new DeleteFileCommand(
                tenantId,
                fileId,
                request.version(),
                request.reason(),
                traceId);
        FileResult result = deleteUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(toDto(result), traceId));
    }

    private FileAccessResponse toDto(FileAccessResult r) {
        return new FileAccessResponse(r.mode(), r.accessUrl(), r.expiresAt(), r.auditId());
    }

    private FileResponse toDto(FileResult r) {
        return new FileResponse(r.fileId(), r.status(), r.scanStatus(), r.version());
    }
}
