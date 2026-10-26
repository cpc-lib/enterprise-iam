package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository.DataScopeBindingInput;
import com.enterprise.iam.authorization.application.service.ReplaceRolePermissionDataScopesUseCase;
import com.enterprise.iam.authorization.application.service.ReplaceRolePermissionFieldPoliciesUseCase;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.DataScopeBinding;
import com.enterprise.iam.authorization.interfaces.http.dto.PermissionVersionResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.ReplaceDataScopesRequest;
import com.enterprise.iam.authorization.interfaces.http.dto.ReplaceFieldPoliciesRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/role-permissions")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class RolePermissionBindingController {

    private final ReplaceRolePermissionDataScopesUseCase replaceDataScopesUseCase;
    private final ReplaceRolePermissionFieldPoliciesUseCase replaceFieldPoliciesUseCase;

    public RolePermissionBindingController(
            ReplaceRolePermissionDataScopesUseCase replaceDataScopesUseCase,
            ReplaceRolePermissionFieldPoliciesUseCase replaceFieldPoliciesUseCase) {
        this.replaceDataScopesUseCase = replaceDataScopesUseCase;
        this.replaceFieldPoliciesUseCase = replaceFieldPoliciesUseCase;
    }

    @PutMapping("/{rolePermissionId}/data-scopes")
    public ResponseEntity<ApiResponse<PermissionVersionResponse>> replaceDataScopes(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long rolePermissionId,
            @Valid @RequestBody ReplaceDataScopesRequest request) {
        String traceId = UUID.randomUUID().toString();
        List<DataScopeBindingInput> bindings = request.bindings().stream()
                .map(this::toInput)
                .toList();
        var result = replaceDataScopesUseCase.replace(
                tenantId, rolePermissionId, bindings, request.bindingVersion());
        var response = new PermissionVersionResponse(result.bindingVersion(), result.permissionVersion());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PutMapping("/{rolePermissionId}/field-policies")
    public ResponseEntity<ApiResponse<PermissionVersionResponse>> replaceFieldPolicies(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long rolePermissionId,
            @Valid @RequestBody ReplaceFieldPoliciesRequest request) {
        String traceId = UUID.randomUUID().toString();
        var result = replaceFieldPoliciesUseCase.replace(
                tenantId, rolePermissionId, request.fieldPolicyIds(), request.bindingVersion());
        var response = new PermissionVersionResponse(result.bindingVersion(), result.permissionVersion());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    private DataScopeBindingInput toInput(DataScopeBinding binding) {
        return new DataScopeBindingInput(
                binding.dataScopeId(),
                binding.mergeMode(),
                binding.teamIds(),
                binding.includeChildren());
    }
}
