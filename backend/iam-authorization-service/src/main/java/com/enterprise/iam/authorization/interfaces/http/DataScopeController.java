package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.CreateDataScopeUseCase;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.CreateDataScopeRequest;
import com.enterprise.iam.authorization.interfaces.http.dto.EntityResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/data-scopes")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class DataScopeController {

    private final CreateDataScopeUseCase createDataScopeUseCase;

    public DataScopeController(CreateDataScopeUseCase createDataScopeUseCase) {
        this.createDataScopeUseCase = createDataScopeUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EntityResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @Valid @RequestBody CreateDataScopeRequest request) {
        String traceId = UUID.randomUUID().toString();
        var result = createDataScopeUseCase.create(
                tenantId, request.scopeCode(), request.scopeName(), request.scopeType(), request.customPolicyId());
        var response = new EntityResponse(result.id(), result.version(), result.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }
}
