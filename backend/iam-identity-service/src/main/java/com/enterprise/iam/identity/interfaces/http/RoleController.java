package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.command.BindUserRoleCommand;
import com.enterprise.iam.identity.application.command.CreateRoleCommand;
import com.enterprise.iam.identity.application.service.BindUserRoleUseCase;
import com.enterprise.iam.identity.application.service.CreateRoleUseCase;
import com.enterprise.iam.identity.application.service.DeleteRoleUseCase;
import com.enterprise.iam.identity.application.service.DisableRoleUseCase;
import com.enterprise.iam.identity.application.service.EnableRoleUseCase;
import com.enterprise.iam.identity.application.service.GetRoleMenusUseCase;
import com.enterprise.iam.identity.application.service.GetRoleUsersUseCase;
import com.enterprise.iam.identity.application.service.ListRolesUseCase;
import com.enterprise.iam.identity.application.service.UnbindUserRoleUseCase;
import com.enterprise.iam.identity.application.service.UpdateRoleMenusUseCase;
import com.enterprise.iam.identity.domain.model.Role;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.CreateRoleRequest;
import com.enterprise.iam.identity.interfaces.http.dto.RoleDto;
import com.enterprise.iam.identity.interfaces.http.dto.RoleResponse;
import com.enterprise.iam.identity.interfaces.http.dto.UpdateRoleMenusRequest;
import com.enterprise.iam.identity.interfaces.http.dto.UserDetailDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class RoleController {

    private final CreateRoleUseCase createRoleUseCase;
    private final BindUserRoleUseCase bindUserRoleUseCase;
    private final ListRolesUseCase listRolesUseCase;
    private final GetRoleMenusUseCase getRoleMenusUseCase;
    private final UpdateRoleMenusUseCase updateRoleMenusUseCase;
    private final EnableRoleUseCase enableRoleUseCase;
    private final DisableRoleUseCase disableRoleUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;
    private final GetRoleUsersUseCase getRoleUsersUseCase;
    private final UnbindUserRoleUseCase unbindUserRoleUseCase;

    public RoleController(
            CreateRoleUseCase createRoleUseCase,
            BindUserRoleUseCase bindUserRoleUseCase) {
        this(createRoleUseCase, bindUserRoleUseCase, null, null, null, null, null, null, null, null);
    }

    public RoleController(
            CreateRoleUseCase createRoleUseCase,
            BindUserRoleUseCase bindUserRoleUseCase,
            ListRolesUseCase listRolesUseCase) {
        this(createRoleUseCase, bindUserRoleUseCase, listRolesUseCase, null, null, null, null, null, null, null);
    }

    public RoleController(
            CreateRoleUseCase createRoleUseCase,
            BindUserRoleUseCase bindUserRoleUseCase,
            ListRolesUseCase listRolesUseCase,
            GetRoleMenusUseCase getRoleMenusUseCase,
            UpdateRoleMenusUseCase updateRoleMenusUseCase) {
        this(createRoleUseCase, bindUserRoleUseCase, listRolesUseCase, getRoleMenusUseCase, updateRoleMenusUseCase, null, null, null, null, null);
    }

    public RoleController(
            CreateRoleUseCase createRoleUseCase,
            BindUserRoleUseCase bindUserRoleUseCase,
            ListRolesUseCase listRolesUseCase,
            GetRoleMenusUseCase getRoleMenusUseCase,
            UpdateRoleMenusUseCase updateRoleMenusUseCase,
            EnableRoleUseCase enableRoleUseCase,
            DisableRoleUseCase disableRoleUseCase,
            DeleteRoleUseCase deleteRoleUseCase) {
        this(createRoleUseCase, bindUserRoleUseCase, listRolesUseCase, getRoleMenusUseCase, updateRoleMenusUseCase, enableRoleUseCase, disableRoleUseCase, deleteRoleUseCase, null, null);
    }

    @Autowired
    public RoleController(
            CreateRoleUseCase createRoleUseCase,
            BindUserRoleUseCase bindUserRoleUseCase,
            ListRolesUseCase listRolesUseCase,
            GetRoleMenusUseCase getRoleMenusUseCase,
            UpdateRoleMenusUseCase updateRoleMenusUseCase,
            EnableRoleUseCase enableRoleUseCase,
            DisableRoleUseCase disableRoleUseCase,
            DeleteRoleUseCase deleteRoleUseCase,
            GetRoleUsersUseCase getRoleUsersUseCase,
            UnbindUserRoleUseCase unbindUserRoleUseCase) {
        this.createRoleUseCase = createRoleUseCase;
        this.bindUserRoleUseCase = bindUserRoleUseCase;
        this.listRolesUseCase = listRolesUseCase;
        this.getRoleMenusUseCase = getRoleMenusUseCase;
        this.updateRoleMenusUseCase = updateRoleMenusUseCase;
        this.enableRoleUseCase = enableRoleUseCase;
        this.disableRoleUseCase = disableRoleUseCase;
        this.deleteRoleUseCase = deleteRoleUseCase;
        this.getRoleUsersUseCase = getRoleUsersUseCase;
        this.unbindUserRoleUseCase = unbindUserRoleUseCase;
    }

    private long resolveTenantId(Long headerTenantId, Long queryTenantId) {
        return resolveTenantIdForWrite(headerTenantId, queryTenantId);
    }

    private Long resolveTenantIdForList(Long headerTenantId, Long queryTenantId) {
        if (headerTenantId != null && headerTenantId == 10001L) {
            return (queryTenantId != null && queryTenantId > 0) ? queryTenantId : null;
        }
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        return (queryTenantId != null && queryTenantId > 0) ? queryTenantId : 20001L;
    }

    private long resolveTenantIdForWrite(Long headerTenantId, Long queryTenantId) {
        if (headerTenantId != null && headerTenantId == 10001L) {
            return (queryTenantId != null && queryTenantId > 0) ? queryTenantId : 10001L;
        }
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        return (queryTenantId != null && queryTenantId > 0) ? queryTenantId : 20001L;
    }

    private long resolveCallerTenantId(Long headerTenantId, Long queryTenantId) {
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        return (queryTenantId != null && queryTenantId > 0) ? queryTenantId : 10001L;
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleDto>>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        String traceId = UUID.randomUUID().toString();
        if (listRolesUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), traceId));
        }
        boolean isSuperAdmin = (headerTenantId != null && headerTenantId == 10001L);
        List<Role> roles;
        if (isSuperAdmin) {
            Long targetTenantId = (queryTenantId != null && queryTenantId > 0) ? queryTenantId : null;
            roles = listRolesUseCase.listRoles(targetTenantId);
        } else {
            Long targetTenantId = (headerTenantId != null && headerTenantId > 0) ? headerTenantId : queryTenantId;
            roles = listRolesUseCase.listRoles(targetTenantId, headerUserId);
        }
        List<RoleDto> dtos = roles.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleResponse>> create(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @Valid @RequestBody CreateRoleRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        var command = new CreateRoleCommand(
                tenantId,
                request.roleCode(),
                request.roleName(),
                request.description(),
                traceId);
        var result = createRoleUseCase.create(command);
        var response = new RoleResponse(result.roleId(), result.status(), result.version());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, traceId));
    }

    @PutMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<Void> bindUserRole(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId,
            @PathVariable long roleId,
            @Valid @RequestBody(required = false) VersionRequest request) {
        long callerTenantId = resolveCallerTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        long version = (request != null && request.version() != null) ? request.version() : 0L;
        var command = new BindUserRoleCommand(callerTenantId, userId, roleId, version, traceId);
        bindUserRoleUseCase.bind(command);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getUserRoles(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId) {
        Long tenantId = resolveTenantIdForList(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (listRolesUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), traceId));
        }
        List<Role> roles = listRolesUseCase.listRolesByUser(tenantId, userId);
        List<RoleDto> dtos = roles.stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @GetMapping("/roles/{roleId}/menus")
    public ResponseEntity<ApiResponse<List<String>>> getRoleMenus(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId) {
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (getRoleMenusUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), traceId));
        }
        List<Long> menuIds = getRoleMenusUseCase.getRoleMenus(tenantId, roleId);
        List<String> stringIds = menuIds.stream().map(String::valueOf).toList();
        return ResponseEntity.ok(ApiResponse.ok(stringIds, traceId));
    }

    @PutMapping("/roles/{roleId}/menus")
    public ResponseEntity<ApiResponse<Void>> updateRoleMenus(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId,
            @RequestBody UpdateRoleMenusRequest request) {
        long tenantId = resolveCallerTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (updateRoleMenusUseCase != null) {
            List<Long> menuIds = (request != null && request.menuIds() != null) ? request.menuIds() : List.of();
            updateRoleMenusUseCase.updateRoleMenus(tenantId, roleId, menuIds);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PostMapping("/roles/{roleId}/enable")
    public ResponseEntity<ApiResponse<RoleDto>> enable(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId,
            @RequestBody(required = false) VersionRequest request) {
        long tenantId = resolveCallerTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (enableRoleUseCase == null) {
            throw new UnsupportedOperationException("enable role not supported");
        }
        Long v = request != null ? request.version() : null;
        Role role = enableRoleUseCase.enable(tenantId, roleId, v);
        return ResponseEntity.ok(ApiResponse.ok(toDto(role), traceId));
    }

    @PostMapping("/roles/{roleId}/disable")
    public ResponseEntity<ApiResponse<RoleDto>> disable(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId,
            @RequestBody(required = false) VersionRequest request) {
        long tenantId = resolveCallerTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (disableRoleUseCase == null) {
            throw new UnsupportedOperationException("disable role not supported");
        }
        Long v = request != null ? request.version() : null;
        Role role = disableRoleUseCase.disable(tenantId, roleId, v);
        return ResponseEntity.ok(ApiResponse.ok(toDto(role), traceId));
    }

    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId,
            @RequestParam(value = "version", required = false) Long version) {
        long tenantId = resolveCallerTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (deleteRoleUseCase == null) {
            throw new UnsupportedOperationException("delete role not supported");
        }
        deleteRoleUseCase.delete(tenantId, roleId, version);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @GetMapping("/roles/{roleId}/users")
    public ResponseEntity<ApiResponse<List<UserDetailDto>>> getRoleUsers(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId) {
        Long tenantId;
        if (headerTenantId != null && headerTenantId == 10001L) {
            tenantId = (queryTenantId == null || queryTenantId == 10001L) ? null : queryTenantId;
        } else {
            tenantId = (headerTenantId != null && headerTenantId > 0) ? headerTenantId : queryTenantId;
        }
        String traceId = UUID.randomUUID().toString();
        if (getRoleUsersUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), traceId));
        }
        List<User> users = getRoleUsersUseCase.getUsersByRoleId(tenantId, roleId);
        List<UserDetailDto> dtos = users.stream().map(this::toUserDetailDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @DeleteMapping("/roles/{roleId}/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> unbindUserRole(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long roleId,
            @PathVariable long userId) {
        long callerTenantId = resolveCallerTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (unbindUserRoleUseCase != null) {
            unbindUserRoleUseCase.unbind(callerTenantId, userId, roleId);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<Void>> unbindUserRoleAlias(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId,
            @PathVariable long roleId) {
        return unbindUserRole(headerTenantId, queryTenantId, roleId, userId);
    }

    private UserDetailDto toUserDetailDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserDetailDto(
                user.id(),
                user.id(),
                user.tenantId(),
                user.username(),
                user.displayName(),
                user.displayName(),
                user.email(),
                user.phone(),
                user.departmentId(),
                user.positionId(),
                user.status().name(),
                user.version(),
                user.createdAt(),
                user.updatedAt());
    }

    private RoleDto toDto(Role r) {
        if (r == null) {
            return null;
        }
        return new RoleDto(
                String.valueOf(r.id()),
                String.valueOf(r.tenantId()),
                r.roleCode(),
                r.roleName(),
                r.description(),
                r.status().name(),
                r.version(),
                r.priority(),
                r.createdAt());
    }

    public record VersionRequest(@NotNull Long version) {
    }
}
