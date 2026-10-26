package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository.ResourceFieldInput;
import com.enterprise.iam.authorization.application.service.ReplaceResourceFieldsUseCase;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.EntityResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.ReplaceResourceFieldsRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/resources")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class ResourceFieldController {

    private final ReplaceResourceFieldsUseCase replaceResourceFieldsUseCase;

    public ResourceFieldController(ReplaceResourceFieldsUseCase replaceResourceFieldsUseCase) {
        this.replaceResourceFieldsUseCase = replaceResourceFieldsUseCase;
    }

    @PutMapping("/{resourceId}/fields")
    public ResponseEntity<ApiResponse<EntityResponse>> replaceFields(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long resourceId,
            @Valid @RequestBody ReplaceResourceFieldsRequest request) {
        String traceId = UUID.randomUUID().toString();
        var inputs = request.fields().stream()
                .map(f -> new ResourceFieldInput(
                        f.fieldCode(), f.propertyPath(), f.columnName(),
                        f.dataType(), f.sensitiveLevel(), f.systemManaged()))
                .toList();
        var result = replaceResourceFieldsUseCase.replace(
                tenantId, resourceId, inputs, request.resourceVersion());
        var response = new EntityResponse(result.id(), result.version(), result.status());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
