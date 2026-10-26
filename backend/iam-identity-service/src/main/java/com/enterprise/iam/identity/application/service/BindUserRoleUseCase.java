package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.BindUserRoleCommand;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.application.port.out.UserRoleRepository;
import com.enterprise.iam.identity.domain.model.SodConflictCheckResult;
import com.enterprise.iam.identity.domain.model.UserRole;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class BindUserRoleUseCase {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;
    private final SodPolicyUseCase sodPolicyUseCase;

    public BindUserRoleUseCase(
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this(userRoleRepository, userRepository, roleRepository, idGenerator, clock, null);
    }

    public BindUserRoleUseCase(
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PositiveIdGenerator idGenerator,
            Clock clock,
            SodPolicyUseCase sodPolicyUseCase) {
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
        this.sodPolicyUseCase = sodPolicyUseCase;
    }

    public void bind(BindUserRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        long tenantId = command.tenantId();
        var userOpt = userRepository.findById(tenantId, command.userId());
        if (userOpt.isEmpty()) {
            Long userTenantId = userRepository.findTenantIdByUserId(command.userId());
            if (userTenantId != null && userTenantId > 0) {
                tenantId = userTenantId;
                userOpt = userRepository.findById(tenantId, command.userId());
            }
        }
        var user = userOpt.orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (user.status() == UserStatus.DISABLED) {
            throw new IllegalStateException("cannot bind role to disabled user");
        }
        long resolvedTenantId = user.tenantId();
        var roleOpt = roleRepository.findById(resolvedTenantId, command.roleId());
        if (roleOpt.isEmpty()) {
            Long roleTenantId = roleRepository.findTenantIdByRoleId(command.roleId());
            if (roleTenantId != null && roleTenantId > 0) {
                roleOpt = roleRepository.findById(roleTenantId, command.roleId());
            }
        }
        var role = roleOpt.orElseThrow(() -> new IllegalArgumentException("role not found"));
        if (role.tenantId() != command.tenantId()) {
            throw new SecurityException("无权分配未由本租户管理的角色 (角色所属租户: " + role.tenantId() + ")");
        }

        if (command.tenantId() == 10001L && resolvedTenantId != 10001L) {
            boolean isTenantAdmin = userRepository.isTenantAdmin(resolvedTenantId, command.userId());
            if (!isTenantAdmin) {
                throw new SecurityException("超级系统管理员只能管理租户管理员，不能为租户下的其他普通用户分配角色");
            }
        }

        if (userRoleRepository.findActive(resolvedTenantId, command.userId(), command.roleId())
                .isPresent()) {
            return;
        }

        if (sodPolicyUseCase != null) {
            Set<Long> candidateRoles = new LinkedHashSet<>();
            for (UserRole activeRole : userRoleRepository.findActiveByUser(resolvedTenantId, command.userId())) {
                candidateRoles.add(activeRole.roleId());
            }
            candidateRoles.add(command.roleId());
            SodConflictCheckResult sodCheck = sodPolicyUseCase.checkRoleConflict(resolvedTenantId, candidateRoles);
            if (sodCheck.violated()) {
                throw new IllegalStateException(
                        "IAM_SOD_CONFLICT_VIOLATION: Role assignment violates SoD policy ["
                                + sodCheck.violatedPolicyCode() + " - " + sodCheck.violatedPolicyName() + "]");
            }
        }

        Instant now = clock.instant();
        long id = idGenerator.nextId();
        UserRole binding = new UserRole(
                id, resolvedTenantId, command.userId(), command.roleId(),
                UserRole.Status.ACTIVE, now, null, 0);
        userRoleRepository.save(binding);
    }
}
