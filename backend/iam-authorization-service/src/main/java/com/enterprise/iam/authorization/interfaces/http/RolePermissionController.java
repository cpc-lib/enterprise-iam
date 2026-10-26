package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.ReplaceRolePermissionsUseCase;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/admin/v1/roles")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class RolePermissionController {

    private final ReplaceRolePermissionsUseCase replaceRolePermissionsUseCase;

    public RolePermissionController(ReplaceRolePermissionsUseCase replaceRolePermissionsUseCase) {
        this.replaceRolePermissionsUseCase = replaceRolePermissionsUseCase;
    }

    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<ReplaceRolePermissionsUseCase.Result>> replacePermissions(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long roleId,
            @Valid @RequestBody ReplacePermissionsRequest request) {
        String traceId = UUID.randomUUID().toString();
        var result = replaceRolePermissionsUseCase.replace(
                tenantId, roleId, request.permissionIds(), request.roleVersion());
        return ResponseEntity.ok(ApiResponse.ok(result, traceId));
    }

    public record ReplacePermissionsRequest(
            @NotNull Long roleVersion,
            @NotNull List<Long> permissionIds) {
    }
}
