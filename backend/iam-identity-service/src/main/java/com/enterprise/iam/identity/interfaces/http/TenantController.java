package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.command.TenantBootstrapCommand;
import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.application.service.DisableTenantUseCase;
import com.enterprise.iam.identity.application.service.EnableTenantUseCase;
import com.enterprise.iam.identity.application.service.ListTenantsUseCase;
import com.enterprise.iam.identity.application.service.TenantBootstrapUseCase;
import com.enterprise.iam.identity.application.service.UpdateTenantUseCase;
import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.TenantBootstrapRequest;
import com.enterprise.iam.identity.interfaces.http.dto.TenantBootstrapResponse;
import com.enterprise.iam.identity.interfaces.http.dto.TenantDto;
import com.enterprise.iam.identity.interfaces.http.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/tenants")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class TenantController {

    private final TenantBootstrapUseCase tenantBootstrapUseCase;
    private final ListTenantsUseCase listTenantsUseCase;
    private final EnableTenantUseCase enableTenantUseCase;
    private final DisableTenantUseCase disableTenantUseCase;
    private final UpdateTenantUseCase updateTenantUseCase;
    private final TenantRepository tenantRepository;

    public TenantController(TenantBootstrapUseCase tenantBootstrapUseCase) {
        this(tenantBootstrapUseCase, null, null, null, null, null);
    }

    public TenantController(
            TenantBootstrapUseCase tenantBootstrapUseCase,
            ListTenantsUseCase listTenantsUseCase,
            EnableTenantUseCase enableTenantUseCase,
            DisableTenantUseCase disableTenantUseCase,
            UpdateTenantUseCase updateTenantUseCase,
            TenantRepository tenantRepository) {
        this.tenantBootstrapUseCase = tenantBootstrapUseCase;
        this.listTenantsUseCase = listTenantsUseCase;
        this.enableTenantUseCase = enableTenantUseCase;
        this.disableTenantUseCase = disableTenantUseCase;
        this.updateTenantUseCase = updateTenantUseCase;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantDto>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        String traceId = UUID.randomUUID().toString();
        if (listTenantsUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), traceId));
        }
        List<Tenant> tenants = listTenantsUseCase.listTenants(keyword, status);
        List<TenantDto> dtos = tenants.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDto>> getById(
            @PathVariable("tenantId") long tenantId) {
        String traceId = UUID.randomUUID().toString();
        if (tenantRepository == null) {
            return ResponseEntity.notFound().build();
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + tenantId));
        return ResponseEntity.ok(ApiResponse.ok(toDto(tenant), traceId));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable("tenantId") long tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        String traceId = UUID.randomUUID().toString();
        if (updateTenantUseCase != null) {
            updateTenantUseCase.updateTenantName(tenantId, request.tenantName());
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PostMapping("/{tenantId}/enable")
    public ResponseEntity<ApiResponse<Void>> enable(
            @PathVariable("tenantId") long tenantId) {
        String traceId = UUID.randomUUID().toString();
        if (enableTenantUseCase != null) {
            enableTenantUseCase.enableTenant(tenantId);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PostMapping("/{tenantId}/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @PathVariable("tenantId") long tenantId) {
        String traceId = UUID.randomUUID().toString();
        if (disableTenantUseCase != null) {
            disableTenantUseCase.disableTenant(tenantId);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<ApiResponse<TenantBootstrapResponse>> bootstrap(
            @Valid @RequestBody TenantBootstrapRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new TenantBootstrapCommand(
                request.tenantCode(),
                request.tenantName(),
                request.initialAdmin().identityType(),
                request.initialAdmin().identity(),
                request.initialAdmin().displayName(),
                request.initialAdmin().initialPassword(),
                traceId);
        var result = tenantBootstrapUseCase.bootstrap(command);
        var response = new TenantBootstrapResponse(result.tenantId(), result.status(), result.operationId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted(response, traceId));
    }

    private TenantDto toDto(Tenant t) {
        return new TenantDto(
                String.valueOf(t.id()),
                t.tenantCode(),
                t.tenantName(),
                t.status().name(),
                t.version(),
                t.createdAt(),
                t.updatedAt());
    }
}
