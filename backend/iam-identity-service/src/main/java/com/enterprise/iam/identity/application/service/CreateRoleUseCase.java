package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.CreateRoleCommand;
import com.enterprise.iam.identity.application.model.CreateRoleResult;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.domain.model.Role;
import com.enterprise.iam.identity.domain.model.RoleStatus;
import com.enterprise.iam.identity.domain.model.TenantStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class CreateRoleUseCase {

    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateRoleUseCase(
            RoleRepository roleRepository,
            TenantRepository tenantRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateRoleResult create(CreateRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("tenant is not active");
        }
        if (roleRepository.findByCode(command.tenantId(), command.roleCode()).isPresent()) {
            throw new IllegalStateException("role code already exists");
        }

        Instant now = clock.instant();
        long roleId = idGenerator.nextId();
        Role role = new Role(
                roleId,
                command.tenantId(),
                command.roleCode(),
                command.roleName(),
                command.description(),
                0,
                RoleStatus.ACTIVE,
                0,
                now,
                now);
        roleRepository.save(role);
        return new CreateRoleResult(roleId, RoleStatus.ACTIVE, 0);
    }
}
