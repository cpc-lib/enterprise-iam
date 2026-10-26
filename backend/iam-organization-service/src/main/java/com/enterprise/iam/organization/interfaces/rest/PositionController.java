package com.enterprise.iam.organization.interfaces.rest;

import com.enterprise.iam.organization.application.service.CreatePositionUseCase;
import com.enterprise.iam.organization.application.service.DeletePositionUseCase;
import com.enterprise.iam.organization.application.service.DisablePositionUseCase;
import com.enterprise.iam.organization.application.service.EnablePositionUseCase;
import com.enterprise.iam.organization.application.service.GetPositionDetailUseCase;
import com.enterprise.iam.organization.application.service.ListPositionsUseCase;
import com.enterprise.iam.organization.application.service.UpdatePositionUseCase;
import com.enterprise.iam.organization.domain.model.Position;
import com.enterprise.iam.organization.domain.model.PositionStatus;
import com.enterprise.iam.organization.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.CreatePositionRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.EntityResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.PositionResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.UpdatePositionRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.VersionCommand;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/positions")
@ConditionalOnProperty(prefix = "iam.organization", name = "enabled", havingValue = "true")
public class PositionController {

    private final CreatePositionUseCase createPositionUseCase;
    private final UpdatePositionUseCase updatePositionUseCase;
    private final DisablePositionUseCase disablePositionUseCase;
    private final EnablePositionUseCase enablePositionUseCase;
    private final ListPositionsUseCase listPositionsUseCase;
    private final GetPositionDetailUseCase getPositionDetailUseCase;
    private final DeletePositionUseCase deletePositionUseCase;

    public PositionController(
            CreatePositionUseCase createPositionUseCase,
            UpdatePositionUseCase updatePositionUseCase,
            DisablePositionUseCase disablePositionUseCase,
            EnablePositionUseCase enablePositionUseCase,
            ListPositionsUseCase listPositionsUseCase,
            GetPositionDetailUseCase getPositionDetailUseCase,
            DeletePositionUseCase deletePositionUseCase) {
        this.createPositionUseCase = Objects.requireNonNull(createPositionUseCase);
        this.updatePositionUseCase = Objects.requireNonNull(updatePositionUseCase);
        this.disablePositionUseCase = Objects.requireNonNull(disablePositionUseCase);
        this.enablePositionUseCase = Objects.requireNonNull(enablePositionUseCase);
        this.listPositionsUseCase = Objects.requireNonNull(listPositionsUseCase);
        this.getPositionDetailUseCase = Objects.requireNonNull(getPositionDetailUseCase);
        this.deletePositionUseCase = Objects.requireNonNull(deletePositionUseCase);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PositionResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @Valid @RequestBody CreatePositionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Position position = createPositionUseCase.create(
                tenantId,
                request.positionCode(),
                request.positionName(),
                request.organizationId(),
                request.sortOrder(),
                request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(toResponse(position), traceId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PositionResponse>>> list(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @RequestParam(value = "organizationId", required = false) Long organizationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        String traceId = UUID.randomUUID().toString();
        PositionStatus positionStatus = status != null && !status.isBlank() ? PositionStatus.valueOf(status.trim().toUpperCase()) : null;
        List<Position> list = listPositionsUseCase.listPositions(tenantId, organizationId, positionStatus, keyword);
        List<PositionResponse> responses = list.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(responses, traceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionResponse>> getDetail(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id) {
        String traceId = UUID.randomUUID().toString();
        return getPositionDetailUseCase.getDetail(tenantId, id)
                .map(pos -> ResponseEntity.ok(ApiResponse.ok(toResponse(pos), traceId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionResponse>> update(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody UpdatePositionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Position updated = updatePositionUseCase.update(
                tenantId,
                id,
                request.positionCode(),
                request.positionName(),
                request.organizationId(),
                request.sortOrder(),
                request.description(),
                request.version());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(updated), traceId));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<PositionResponse>> disable(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Position disabled = disablePositionUseCase.disable(tenantId, id, request.version());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(disabled), traceId));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<PositionResponse>> enable(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Position enabled = enablePositionUseCase.enable(tenantId, id, request.version());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(enabled), traceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @RequestParam("version") long version) {
        String traceId = UUID.randomUUID().toString();
        deletePositionUseCase.delete(tenantId, id, version);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    private PositionResponse toResponse(Position pos) {
        return new PositionResponse(
                pos.id(),
                pos.positionCode(),
                pos.positionName(),
                pos.organizationId(),
                pos.sortOrder(),
                pos.description(),
                pos.status().name(),
                pos.version(),
                pos.createdAt(),
                pos.updatedAt());
    }
}
