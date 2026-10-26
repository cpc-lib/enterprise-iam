package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.EnableUserCommand;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.util.Objects;

public class EnableUserUseCase {

    private final UserRepository userRepository;

    public EnableUserUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public User enable(EnableUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        User existing = userRepository.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (existing.status() == UserStatus.ACTIVE) {
            return existing;
        }

        if (command.expectedVersion() > 0 && existing.version() != command.expectedVersion()) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: user version mismatch");
        }

        long targetVersion = (command.expectedVersion() > 0) ? command.expectedVersion() : existing.version();
        userRepository.updateStatus(command.tenantId(), command.userId(), UserStatus.ACTIVE, targetVersion);
        return userRepository.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new IllegalStateException("enabled user could not be reloaded"));
    }
}
