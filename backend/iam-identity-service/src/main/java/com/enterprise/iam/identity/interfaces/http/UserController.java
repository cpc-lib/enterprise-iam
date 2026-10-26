package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.command.CreateUserCommand;
import com.enterprise.iam.identity.application.command.DisableUserCommand;
import com.enterprise.iam.identity.application.command.EnableUserCommand;
import com.enterprise.iam.identity.application.command.UpdateUserCommand;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.application.service.CreateUserUseCase;
import com.enterprise.iam.identity.application.service.DisableUserUseCase;
import com.enterprise.iam.identity.application.service.EnableUserUseCase;
import com.enterprise.iam.identity.application.service.GetUserDetailUseCase;
import com.enterprise.iam.identity.application.service.KickUserSessionsUseCase;
import com.enterprise.iam.identity.application.service.ListUsersUseCase;
import com.enterprise.iam.identity.application.service.ResetUserPasswordUseCase;
import com.enterprise.iam.identity.application.service.UpdateUserUseCase;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserStatus;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.CreateUserRequest;
import com.enterprise.iam.identity.interfaces.http.dto.PageResponse;
import com.enterprise.iam.identity.interfaces.http.dto.UpdateUserRequest;
import com.enterprise.iam.identity.interfaces.http.dto.UserDetailDto;
import com.enterprise.iam.identity.interfaces.http.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
@RequestMapping("/admin/v1/users")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DisableUserUseCase disableUserUseCase;
    private final EnableUserUseCase enableUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final GetUserDetailUseCase getUserDetailUseCase;
    private final KickUserSessionsUseCase kickUserSessionsUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final UserRepository userRepository;

    public UserController(CreateUserUseCase createUserUseCase, DisableUserUseCase disableUserUseCase) {
        this(createUserUseCase, null, disableUserUseCase, null, null, null, null, null, null);
    }

    @Autowired
    public UserController(
            CreateUserUseCase createUserUseCase,
            UpdateUserUseCase updateUserUseCase,
            DisableUserUseCase disableUserUseCase,
            EnableUserUseCase enableUserUseCase,
            ListUsersUseCase listUsersUseCase,
            GetUserDetailUseCase getUserDetailUseCase,
            KickUserSessionsUseCase kickUserSessionsUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            UserRepository userRepository) {
        this.createUserUseCase = Objects.requireNonNull(createUserUseCase);
        this.updateUserUseCase = updateUserUseCase;
        this.disableUserUseCase = Objects.requireNonNull(disableUserUseCase);
        this.enableUserUseCase = enableUserUseCase;
        this.listUsersUseCase = listUsersUseCase;
        this.getUserDetailUseCase = getUserDetailUseCase;
        this.kickUserSessionsUseCase = kickUserSessionsUseCase;
        this.resetUserPasswordUseCase = resetUserPasswordUseCase;
        this.userRepository = userRepository;
    }

    private void checkSuperAdminCannotModifyTenantUser(Long headerTenantId, Long queryTenantId, long userId, String action) {
        if (headerTenantId != null && headerTenantId == 10001L) {
            Long userTenantId = (queryTenantId != null && queryTenantId > 0) ? queryTenantId : null;
            if (userTenantId == null && userRepository != null) {
                userTenantId = userRepository.findTenantIdByUserId(userId);
            }
            if (userTenantId != null && userTenantId != 10001L) {
                boolean isTenantAdmin = userRepository != null && userRepository.isTenantAdmin(userTenantId, userId);
                if (!isTenantAdmin) {
                    throw new SecurityException("超级系统管理员只能管理租户管理员，不能" + action + "租户下的其他普通用户 (用户所属租户: " + userTenantId + ")");
                }
            }
        }
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException e) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("IAM_PERMISSION_DENIED", e.getMessage(), traceId));
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
        return resolveTenantIdForWrite(headerTenantId, queryTenantId, 0L);
    }

    private long resolveTenantIdForWrite(Long headerTenantId, Long queryTenantId, long userId) {
        if (headerTenantId != null && headerTenantId == 10001L) {
            if (queryTenantId != null && queryTenantId > 0) {
                return queryTenantId;
            }
            if (userId > 0 && userRepository != null) {
                Long found = userRepository.findTenantIdByUserId(userId);
                if (found != null && found > 0) {
                    return found;
                }
            }
            return 10001L;
        }
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        return (queryTenantId != null && queryTenantId > 0) ? queryTenantId : 20001L;
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> count(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestParam(value = "status", required = false) String status) {
        Long tenantId = resolveTenantIdForList(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (listUsersUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(0L, traceId));
        }
        UserStatus userStatus = status != null && !status.isBlank() ? UserStatus.valueOf(status.trim().toUpperCase()) : null;
        long total = listUsersUseCase.countUsers(tenantId, userStatus);
        return ResponseEntity.ok(ApiResponse.ok(total, traceId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        var command = new CreateUserCommand(
                tenantId,
                request.displayName(),
                request.identity().type(),
                request.identity().value(),
                request.initialPassword(),
                request.departmentId(),
                request.positionId(),
                request.email(),
                request.phone(),
                traceId);
        var result = createUserUseCase.create(command);
        var response = new UserResponse(result.userId(), result.status(), result.version());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, traceId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserDetailDto>>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "positionId", required = false) Long positionId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long tenantId = resolveTenantIdForList(headerTenantId, queryTenantId);
        int finalPageSize = (pageSize != null && pageSize > 0) ? pageSize : ((size != null && size > 0) ? size : 20);
        String traceId = UUID.randomUUID().toString();
        if (listUsersUseCase == null) {
            return ResponseEntity.ok(ApiResponse.ok(new PageResponse<>(List.of(), 0L, page, finalPageSize), traceId));
        }
        UserStatus userStatus = status != null && !status.isBlank() ? UserStatus.valueOf(status.trim().toUpperCase()) : null;
        var result = listUsersUseCase.listUsers(tenantId, keyword, departmentId, positionId, userStatus, page, finalPageSize);
        List<UserDetailDto> dtos = result.items().stream().map(this::toDetailDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(new PageResponse<>(dtos, result.total(), result.page(), result.pageSize()), traceId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailDto>> getDetail(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId) {
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (getUserDetailUseCase == null) {
            return ResponseEntity.notFound().build();
        }
        return getUserDetailUseCase.getDetail(tenantId, userId)
                .map(u -> ResponseEntity.ok(ApiResponse.ok(toDetailDto(u), traceId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId,
            @Valid @RequestBody UpdateUserRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        checkSuperAdminCannotModifyTenantUser(headerTenantId, queryTenantId, userId, "修改");
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        if (updateUserUseCase == null) {
            throw new UnsupportedOperationException("update user not supported");
        }
        var command = new UpdateUserCommand(
                tenantId,
                userId,
                request.displayName(),
                request.email(),
                request.phone(),
                request.departmentId(),
                request.positionId(),
                request.version(),
                traceId);
        User updated = updateUserUseCase.update(command);
        var response = new UserResponse(updated.id(), updated.status(), updated.version());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/{userId}/enable")
    public ResponseEntity<ApiResponse<UserResponse>> enable(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId,
            @RequestBody(required = false) EnableRequest request) {
        checkSuperAdminCannotModifyTenantUser(headerTenantId, queryTenantId, userId, "启用");
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId, userId);
        String traceId = UUID.randomUUID().toString();
        if (enableUserUseCase == null) {
            throw new UnsupportedOperationException("enable user not supported");
        }
        Long version = (request != null && request.version() != null) ? request.version() : 0L;
        String reason = (request != null) ? request.reason() : null;
        var command = new EnableUserCommand(tenantId, userId, version, reason, traceId);
        User enabled = enableUserUseCase.enable(command);
        var response = new UserResponse(enabled.id(), enabled.status(), enabled.version());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<ApiResponse<UserResponse>> disable(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId,
            @RequestBody(required = false) DisableRequest request) {
        if (headerUserId != null && headerUserId.equals(userId)) {
            throw new SecurityException("管理员不能停用自己的账号");
        }
        checkSuperAdminCannotModifyTenantUser(headerTenantId, queryTenantId, userId, "停用");
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId, userId);
        String traceId = UUID.randomUUID().toString();
        Long version = (request != null && request.version() != null) ? request.version() : 0L;
        String reason = (request != null && request.reason() != null) ? request.reason() : "管理员停用账号";
        var command = new DisableUserCommand(tenantId, userId, version, reason, traceId);
        var result = disableUserUseCase.disable(command);
        var response = new UserResponse(result.userId(), result.status(), result.version());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/{userId}/sessions/kick")
    public ResponseEntity<ApiResponse<Void>> kickSessions(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId) {
        if (headerUserId != null && headerUserId.equals(userId)) {
            throw new SecurityException("管理员不能强退当前登录账号");
        }
        checkSuperAdminCannotModifyTenantUser(headerTenantId, queryTenantId, userId, "强退");
        long tenantId = resolveTenantIdForWrite(headerTenantId, queryTenantId, userId);
        String traceId = UUID.randomUUID().toString();
        if (kickUserSessionsUseCase != null) {
            kickUserSessionsUseCase.kick(tenantId, userId);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long userId,
            @RequestBody(required = false) ResetPasswordRequest request) {
        checkSuperAdminCannotModifyTenantUser(headerTenantId, queryTenantId, userId, "重置密码");
        long callerTenantId = (headerTenantId != null && headerTenantId > 0) ? headerTenantId : 10001L;
        Long targetTenantId = (queryTenantId != null && queryTenantId > 0) ? queryTenantId : headerTenantId;
        String traceId = UUID.randomUUID().toString();
        if (resetUserPasswordUseCase == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("RESET_PASSWORD_NOT_AVAILABLE", "重置密码服务未初始化", traceId));
        }
        try {
            String proposedPassword = (request != null) ? request.newPassword() : null;
            String reason = (request != null) ? request.reason() : null;
            ResetUserPasswordUseCase.ResetPasswordResult result =
                    resetUserPasswordUseCase.resetPassword(callerTenantId, targetTenantId, userId, proposedPassword, reason);
            var response = new ResetPasswordResponse(
                    String.valueOf(result.userId()),
                    String.valueOf(result.tenantId()),
                    result.newPassword(),
                    result.isTemporary());
            return ResponseEntity.ok(ApiResponse.ok(response, traceId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("IAM_PERMISSION_DENIED", e.getMessage(), traceId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("IAM_VALIDATION_FAILED", e.getMessage(), traceId));
        }
    }

    private UserDetailDto toDetailDto(User user) {
        if (user == null) {
            return null;
        }
        boolean isTenantAdmin = (user.tenantId() == 10001L)
                || (userRepository != null && userRepository.isTenantAdmin(user.tenantId(), user.id()));
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
                user.updatedAt(),
                isTenantAdmin);
    }

    public record DisableRequest(Long version, String reason) {
    }

    public record EnableRequest(Long version, String reason) {
    }

    public record ResetPasswordRequest(String newPassword, String reason) {
    }

    public record ResetPasswordResponse(String userId, String tenantId, String newPassword, boolean isTemporary) {
    }
}
