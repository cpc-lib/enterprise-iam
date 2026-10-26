package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.CreateFieldPolicyUseCase;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.CreateFieldPolicyRequest;
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
@RequestMapping("/admin/v1/field-policies")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class FieldPolicyController {

    private final CreateFieldPolicyUseCase createFieldPolicyUseCase;

    public FieldPolicyController(CreateFieldPolicyUseCase createFieldPolicyUseCase) {
        this.createFieldPolicyUseCase = createFieldPolicyUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EntityResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @Valid @RequestBody CreateFieldPolicyRequest request) {
        String traceId = UUID.randomUUID().toString();
        var result = createFieldPolicyUseCase.create(
                tenantId, request.resourceId(), request.operationId(), request.fieldId(),
                request.readable(), request.writable(), request.hidden(),
                request.maskStrategyId(), request.priority());
        var response = new EntityResponse(result.id(), result.version(), result.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }
}
