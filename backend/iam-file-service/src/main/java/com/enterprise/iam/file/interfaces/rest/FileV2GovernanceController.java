package com.enterprise.iam.file.interfaces.rest;

import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.service.FileWatermarkAndDlpUseCase;
import com.enterprise.iam.file.domain.model.FileDlpInspectionResult;
import com.enterprise.iam.file.domain.model.FileDrDrillRecord;
import com.enterprise.iam.file.domain.model.FileWatermarkSpec;
import com.enterprise.iam.file.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.file.interfaces.rest.dto.FileResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@ConditionalOnProperty(prefix = "iam.file", name = "enabled", havingValue = "true")
public class FileV2GovernanceController {

    private final FileWatermarkAndDlpUseCase governanceUseCase;

    public FileV2GovernanceController(FileWatermarkAndDlpUseCase governanceUseCase) {
        this.governanceUseCase = governanceUseCase;
    }

    @GetMapping("/{fileId}/watermark")
    public ResponseEntity<ApiResponse<WatermarkResponse>> getWatermark(
            @PathVariable("fileId") long fileId,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "userId", required = false) Long userId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId, null);
        FileWatermarkSpec spec = governanceUseCase.generateWatermark(resolvedTenantId, fileId, userId, traceId);
        return ResponseEntity.ok(ApiResponse.ok(
                new WatermarkResponse(
                        String.valueOf(spec.fileId()),
                        String.valueOf(spec.tenantId()),
                        String.valueOf(spec.userId()),
                        spec.watermarkText(),
                        spec.subText(),
                        spec.opacity(),
                        spec.rotateAngle(),
                        spec.fontSize(),
                        spec.color(),
                        spec.signature(),
                        spec.issuedAt()),
                traceId));
    }

    @PostMapping("/{fileId}/inspect-dlp")
    public ResponseEntity<ApiResponse<DlpInspectionResponse>> inspectDlp(
            @PathVariable("fileId") long fileId,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestBody(required = false) InspectDlpRequest request) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId, request != null ? request.tenantId() : null);
        FileDlpInspectionResult result = governanceUseCase.inspectDlpAndArchiveBomb(
                resolvedTenantId,
                fileId,
                request != null ? request.compressionRatio() : null,
                request != null ? request.nestedArchiveDepth() : null,
                request != null ? request.contentSample() : null);
        return ResponseEntity.ok(ApiResponse.ok(
                new DlpInspectionResponse(
                        String.valueOf(result.fileId()),
                        String.valueOf(result.tenantId()),
                        result.dlpStatus().name(),
                        result.archiveBombDetected(),
                        result.compressionRatio(),
                        result.nestedArchiveDepth(),
                        result.matchedPatterns(),
                        result.fileStatus().name(),
                        result.inspectedAt()),
                traceId));
    }

    @PostMapping("/{fileId}/archive")
    public ResponseEntity<ApiResponse<FileResponse>> archive(
            @PathVariable("fileId") long fileId,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId, null);
        FileResult result = governanceUseCase.archiveToGlacier(resolvedTenantId, fileId);
        return ResponseEntity.ok(ApiResponse.ok(
                new FileResponse(result.fileId(), result.status(), result.scanStatus(), result.version()),
                traceId));
    }

    @PostMapping("/{fileId}/restore")
    public ResponseEntity<ApiResponse<FileResponse>> restore(
            @PathVariable("fileId") long fileId,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId, null);
        FileResult result = governanceUseCase.restoreFromGlacier(resolvedTenantId, fileId);
        return ResponseEntity.ok(ApiResponse.ok(
                new FileResponse(result.fileId(), result.status(), result.scanStatus(), result.version()),
                traceId));
    }

    @PostMapping("/dr-drills")
    public ResponseEntity<ApiResponse<DrDrillResponse>> executeDrDrill(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestBody(required = false) ExecuteDrDrillRequest request) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId, request != null ? request.tenantId() : null);
        FileDrDrillRecord record = governanceUseCase.executeDrDrill(
                resolvedTenantId,
                request != null ? request.scenarioCode() : null,
                request != null ? request.targetRpoSeconds() : null,
                request != null ? request.actualRpoSeconds() : null,
                request != null ? request.targetRtoSeconds() : null,
                request != null ? request.actualRtoSeconds() : null,
                request != null ? request.verifiedObjects() : null);
        return ResponseEntity.ok(ApiResponse.ok(toDrDrillResponse(record), traceId));
    }

    @GetMapping("/dr-drills")
    public ResponseEntity<ApiResponse<List<DrDrillResponse>>> listDrDrills(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId) {
        String traceId = UUID.randomUUID().toString();
        long resolvedTenantId = resolveTenantId(tenantId, headerTenantId, null);
        List<DrDrillResponse> drills = governanceUseCase.listDrDrills(resolvedTenantId)
                .stream()
                .map(this::toDrDrillResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(drills, traceId));
    }

    private DrDrillResponse toDrDrillResponse(FileDrDrillRecord r) {
        return new DrDrillResponse(
                String.valueOf(r.id()),
                String.valueOf(r.tenantId()),
                r.scenarioCode(),
                r.targetRpoSeconds(),
                r.actualRpoSeconds(),
                r.targetRtoSeconds(),
                r.actualRtoSeconds(),
                r.verifiedObjects(),
                r.status(),
                r.executedAt());
    }

    private static long resolveTenantId(Long queryTenantId, Long headerTenantId, Long bodyTenantId) {
        if (queryTenantId != null && queryTenantId > 0) {
            return queryTenantId;
        }
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        if (bodyTenantId != null && bodyTenantId > 0) {
            return bodyTenantId;
        }
        return 1L;
    }

    public record WatermarkResponse(
            String fileId,
            String tenantId,
            String userId,
            String watermarkText,
            String subText,
            double opacity,
            int rotateAngle,
            int fontSize,
            String color,
            String signature,
            Instant issuedAt) {
    }

    public record InspectDlpRequest(
            Long tenantId,
            Double compressionRatio,
            Integer nestedArchiveDepth,
            String contentSample) {
    }

    public record DlpInspectionResponse(
            String fileId,
            String tenantId,
            String dlpStatus,
            boolean archiveBombDetected,
            double compressionRatio,
            int nestedArchiveDepth,
            List<String> matchedPatterns,
            String fileStatus,
            Instant inspectedAt) {
    }

    public record ExecuteDrDrillRequest(
            Long tenantId,
            String scenarioCode,
            Integer targetRpoSeconds,
            Integer actualRpoSeconds,
            Integer targetRtoSeconds,
            Integer actualRtoSeconds,
            Integer verifiedObjects) {
    }

    public record DrDrillResponse(
            String id,
            String tenantId,
            String scenarioCode,
            int targetRpoSeconds,
            int actualRpoSeconds,
            int targetRtoSeconds,
            int actualRtoSeconds,
            int verifiedObjects,
            String status,
            Instant executedAt) {
    }
}
