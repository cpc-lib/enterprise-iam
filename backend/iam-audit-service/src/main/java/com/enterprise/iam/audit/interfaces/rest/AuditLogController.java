package com.enterprise.iam.audit.interfaces.rest;

import com.enterprise.iam.audit.application.command.RecordAuditLogCommand;
import com.enterprise.iam.audit.application.query.AuditLogQuery;
import com.enterprise.iam.audit.application.service.QueryAuditLogUseCase;
import com.enterprise.iam.audit.application.service.RecordAuditLogUseCase;
import com.enterprise.iam.audit.domain.model.AuditLog;
import com.enterprise.iam.audit.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.audit.interfaces.rest.dto.AuditLogResponse;
import com.enterprise.iam.audit.interfaces.rest.dto.RecordAuditLogRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping
@ConditionalOnProperty(prefix = "iam.audit", name = "enabled", havingValue = "true")
public class AuditLogController {

    private static final int DEFAULT_LIMIT = 100;

    private final RecordAuditLogUseCase recordAuditLogUseCase;
    private final QueryAuditLogUseCase queryAuditLogUseCase;

    public AuditLogController(
            RecordAuditLogUseCase recordAuditLogUseCase,
            QueryAuditLogUseCase queryAuditLogUseCase) {
        this.recordAuditLogUseCase = recordAuditLogUseCase;
        this.queryAuditLogUseCase = queryAuditLogUseCase;
    }

    @PostMapping("/internal/v1/audit/logs")
    public ResponseEntity<ApiResponse<Long>> record(
            @Valid @RequestBody RecordAuditLogRequest request) {
        String traceId = UUID.randomUUID().toString();
        var command = new RecordAuditLogCommand(
                request.tenantId(),
                request.actorId(),
                request.action(),
                request.resourceType(),
                request.resourceId(),
                request.detail(),
                traceId);
        var result = recordAuditLogUseCase.record(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(result.auditLogId(), traceId));
    }

    @GetMapping("/admin/v1/audit/logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> query(
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "limit", required = false, defaultValue = "" + DEFAULT_LIMIT) int limit) {
        long tenantId = queryTenantId != null ? queryTenantId : (headerTenantId != null ? headerTenantId : 10001L);
        String traceId = UUID.randomUUID().toString();
        var query = new AuditLogQuery(tenantId, actorId, action, from, to, limit);
        List<AuditLog> logs = queryAuditLogUseCase.query(query);
        List<AuditLogResponse> data = logs.stream()
                .map(log -> new AuditLogResponse(
                        log.id(),
                        log.tenantId(),
                        log.actorId(),
                        log.action(),
                        log.resourceType(),
                        log.resourceId(),
                        log.detail(),
                        log.createdAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data, traceId));
    }
}
