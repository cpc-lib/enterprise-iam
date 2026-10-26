package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.CreateUserCommand;
import com.enterprise.iam.identity.application.model.CreateUserResult;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.IdentityType;
import com.enterprise.iam.identity.domain.model.TenantStatus;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class CreateUserUseCase {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateUserUseCase(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateUserResult create(CreateUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("tenant is not active");
        }

        Instant now = clock.instant();
        long userId = idGenerator.nextId();
        String username = command.identityType() == IdentityType.USERNAME ? command.identityValue() : null;
        String email = command.email() != null ? command.email()
                : (command.identityType() == IdentityType.EMAIL ? command.identityValue() : null);
        String phone = command.phone() != null ? command.phone()
                : (command.identityType() == IdentityType.PHONE ? command.identityValue() : null);

        User user = new User(
                userId,
                command.tenantId(),
                username,
                command.displayName(),
                email,
                phone,
                command.departmentId(),
                command.positionId(),
                UserStatus.ACTIVE,
                0,
                now,
                now);
        userRepository.save(user);

        // 1. Save primary login identity
        long identityId = idGenerator.nextId();
        userRepository.saveUserIdentity(
                identityId,
                command.tenantId(),
                userId,
                command.identityType().name(),
                command.identityValue(),
                userId,
                "ACTIVE");

        // 2. Save auxiliary identities (email/phone) if provided and distinct
        if (email != null && !email.isBlank() && command.identityType() != IdentityType.EMAIL) {
            userRepository.saveUserIdentity(
                    idGenerator.nextId(),
                    command.tenantId(),
                    userId,
                    IdentityType.EMAIL.name(),
                    email,
                    userId,
                    "ACTIVE");
        }
        if (phone != null && !phone.isBlank() && command.identityType() != IdentityType.PHONE) {
            userRepository.saveUserIdentity(
                    idGenerator.nextId(),
                    command.tenantId(),
                    userId,
                    IdentityType.PHONE.name(),
                    phone,
                    userId,
                    "ACTIVE");
        }

        // 3. Initialize password credential and user security state
        if (command.initialPassword() != null && !command.initialPassword().isBlank()) {
            userRepository.initUserPassword(command.tenantId(), userId, command.initialPassword());
        }

        return new CreateUserResult(userId, UserStatus.ACTIVE, 0);
    }
}
