package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.service.DictTypeUseCase;
import com.enterprise.iam.identity.domain.model.DictStatus;
import com.enterprise.iam.identity.domain.model.DictType;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.CreateDictTypeRequest;
import com.enterprise.iam.identity.interfaces.http.dto.DictTypeDto;
import com.enterprise.iam.identity.interfaces.http.dto.PageResponse;
import com.enterprise.iam.identity.interfaces.http.dto.UpdateDictTypeRequest;
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
@RequestMapping("/admin/v1/dicts/types")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class DictTypeController {

    private final DictTypeUseCase dictTypeUseCase;

    public DictTypeController(DictTypeUseCase dictTypeUseCase) {
        this.dictTypeUseCase = Objects.requireNonNull(dictTypeUseCase);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DictTypeDto>>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        String traceId = UUID.randomUUID().toString();
        var result = dictTypeUseCase.list(tenantId, keyword, status, page, pageSize);
        List<DictTypeDto> dtos = result.items().stream().map(this::toDto).toList();
        var pageResponse = new PageResponse<>(dtos, result.total(), result.page(), result.pageSize());
        return ResponseEntity.ok(ApiResponse.ok(pageResponse, traceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DictTypeDto>> get(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable long id) {
        String traceId = UUID.randomUUID().toString();
        return dictTypeUseCase.getById(tenantId, id)
                .map(t -> ResponseEntity.ok(ApiResponse.ok(toDto(t), traceId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DictTypeDto>> create(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @Valid @RequestBody CreateDictTypeRequest request) {
        String traceId = UUID.randomUUID().toString();
        DictStatus status = request.status() != null && !request.status().isBlank()
                ? DictStatus.valueOf(request.status().trim().toUpperCase())
                : DictStatus.ACTIVE;
        DictType created = dictTypeUseCase.create(
                tenantId,
                request.dictType(),
                request.dictName(),
                status,
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toDto(created), traceId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DictTypeDto>> update(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody UpdateDictTypeRequest request) {
        String traceId = UUID.randomUUID().toString();
        DictStatus status = request.status() != null && !request.status().isBlank()
                ? DictStatus.valueOf(request.status().trim().toUpperCase())
                : null;
        DictType updated = dictTypeUseCase.update(
                tenantId,
                id,
                request.dictName(),
                status,
                request.description(),
                request.version()
        );
        return ResponseEntity.ok(ApiResponse.ok(toDto(updated), traceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable long id) {
        String traceId = UUID.randomUUID().toString();
        dictTypeUseCase.delete(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    private DictTypeDto toDto(DictType t) {
        return new DictTypeDto(
                t.id(),
                t.tenantId(),
                t.dictType(),
                t.dictName(),
                t.status().name(),
                t.description(),
                t.version(),
                t.createdAt(),
                t.updatedAt()
        );
    }
}
