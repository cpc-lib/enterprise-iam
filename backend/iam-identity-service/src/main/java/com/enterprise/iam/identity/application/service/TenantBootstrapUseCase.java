package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.TenantBootstrapCommand;
import com.enterprise.iam.identity.application.model.TenantBootstrapResult;
import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.application.port.out.UserRoleRepository;
import com.enterprise.iam.identity.domain.model.IdentityType;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.Role;
import com.enterprise.iam.identity.domain.model.RoleStatus;
import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.domain.model.TenantStatus;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserRole;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tenant bootstrap creates the tenant, initial administrator user, bootstrap
 * role and binds the admin to it. The full organization/team bootstrap is
 * coordinated with the organization service in later phases.
 */
public final class TenantBootstrapUseCase {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final MenuRepository menuRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public TenantBootstrapUseCase(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this(tenantRepository, userRepository, roleRepository, userRoleRepository, null, idGenerator, clock);
    }

    public TenantBootstrapUseCase(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            MenuRepository menuRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.menuRepository = menuRepository;
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public TenantBootstrapResult bootstrap(TenantBootstrapCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (tenantRepository.findByCode(command.tenantCode()).isPresent()) {
            throw new IllegalStateException("tenant already bootstrapped: " + command.tenantCode());
        }

        Instant now = clock.instant();
        long tenantId = idGenerator.nextId();
        long userId = idGenerator.nextId();

        Tenant tenant = new Tenant(
                tenantId, command.tenantCode(), command.tenantName(),
                TenantStatus.INITIALIZING, 0, now, now);
        tenantRepository.save(tenant);

        User admin = new User(
                userId, tenantId,
                command.identityType() == IdentityType.USERNAME ? command.identity() : null,
                command.displayName(),
                command.identityType() == IdentityType.EMAIL ? command.identity() : null,
                command.identityType() == IdentityType.PHONE ? command.identity() : null,
                UserStatus.ACTIVE, 0, now, now);
        userRepository.save(admin);

        // 1. Save user login identity
        long identityId = idGenerator.nextId();
        userRepository.saveUserIdentity(
                identityId,
                tenantId,
                userId,
                command.identityType().name(),
                command.identity(),
                userId,
                "ACTIVE");

        // 2. Initialize password credential and user security state
        userRepository.initUserPassword(tenantId, userId, command.initialPassword());

        tenantRepository.updateStatus(tenantId, TenantStatus.ACTIVE, 0L);

        return new TenantBootstrapResult(tenantId, TenantStatus.ACTIVE, idGenerator.nextId());
    }
}
