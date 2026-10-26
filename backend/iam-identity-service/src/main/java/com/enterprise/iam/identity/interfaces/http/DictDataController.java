package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.service.DictDataUseCase;
import com.enterprise.iam.identity.domain.model.DictData;
import com.enterprise.iam.identity.domain.model.DictStatus;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.CreateDictDataRequest;
import com.enterprise.iam.identity.interfaces.http.dto.DictDataDto;
import com.enterprise.iam.identity.interfaces.http.dto.PageResponse;
import com.enterprise.iam.identity.interfaces.http.dto.UpdateDictDataRequest;
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
@RequestMapping("/admin/v1/dicts/data")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class DictDataController {

    private final DictDataUseCase dictDataUseCase;

    public DictDataController(DictDataUseCase dictDataUseCase) {
        this.dictDataUseCase = Objects.requireNonNull(dictDataUseCase);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DictDataDto>>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @RequestParam(value = "dictType", required = false) String dictType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        String traceId = UUID.randomUUID().toString();
        var result = dictDataUseCase.list(tenantId, dictType, keyword, status, page, pageSize);
        List<DictDataDto> dtos = result.items().stream().map(this::toDto).toList();
        var pageResponse = new PageResponse<>(dtos, result.total(), result.page(), result.pageSize());
        return ResponseEntity.ok(ApiResponse.ok(pageResponse, traceId));
    }

    @GetMapping("/type/{dictType}")
    public ResponseEntity<ApiResponse<List<DictDataDto>>> listByType(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable String dictType) {
        String traceId = UUID.randomUUID().toString();
        List<DictData> items = dictDataUseCase.getByType(tenantId, dictType);
        List<DictDataDto> dtos = items.stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DictDataDto>> get(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable long id) {
        String traceId = UUID.randomUUID().toString();
        return dictDataUseCase.getById(tenantId, id)
                .map(d -> ResponseEntity.ok(ApiResponse.ok(toDto(d), traceId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DictDataDto>> create(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @Valid @RequestBody CreateDictDataRequest request) {
        String traceId = UUID.randomUUID().toString();
        DictStatus status = request.status() != null && !request.status().isBlank()
                ? DictStatus.valueOf(request.status().trim().toUpperCase())
                : DictStatus.ACTIVE;
        DictData created = dictDataUseCase.create(
                tenantId,
                request.dictType(),
                request.dictLabel(),
                request.dictValue(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                status,
                request.cssTag(),
                Boolean.TRUE.equals(request.isDefault()),
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toDto(created), traceId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DictDataDto>> update(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody UpdateDictDataRequest request) {
        String traceId = UUID.randomUUID().toString();
        DictStatus status = request.status() != null && !request.status().isBlank()
                ? DictStatus.valueOf(request.status().trim().toUpperCase())
                : null;
        DictData updated = dictDataUseCase.update(
                tenantId,
                id,
                request.dictLabel(),
                request.dictValue(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                status,
                request.cssTag(),
                Boolean.TRUE.equals(request.isDefault()),
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
        dictDataUseCase.delete(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    private DictDataDto toDto(DictData d) {
        return new DictDataDto(
                d.id(),
                d.tenantId(),
                d.dictType(),
                d.dictLabel(),
                d.dictValue(),
                d.sortOrder(),
                d.status().name(),
                d.cssTag(),
                d.isDefault(),
                d.description(),
                d.version(),
                d.createdAt(),
                d.updatedAt()
        );
    }
}
