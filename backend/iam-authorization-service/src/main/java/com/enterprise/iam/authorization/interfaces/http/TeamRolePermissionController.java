package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.ReplaceTeamRolePermissionsUseCase;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.PermissionVersionResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.ReplacePermissionSetRequest;
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
@RequestMapping("/admin/v1/team-roles")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class TeamRolePermissionController {

    private final ReplaceTeamRolePermissionsUseCase replaceTeamRolePermissionsUseCase;

    public TeamRolePermissionController(ReplaceTeamRolePermissionsUseCase replaceTeamRolePermissionsUseCase) {
        this.replaceTeamRolePermissionsUseCase = replaceTeamRolePermissionsUseCase;
    }

    @PutMapping("/{teamRoleId}/permissions")
    public ResponseEntity<ApiResponse<PermissionVersionResponse>> replacePermissions(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long teamRoleId,
            @Valid @RequestBody ReplacePermissionSetRequest request) {
        String traceId = UUID.randomUUID().toString();
        var result = replaceTeamRolePermissionsUseCase.replace(
                tenantId, teamRoleId, request.permissionIds(), request.version());
        var response = new PermissionVersionResponse(result.bindingVersion(), result.permissionVersion());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
