package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.DisableUserCommand;
import com.enterprise.iam.identity.application.model.CreateUserResult;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.util.Objects;

public final class DisableUserUseCase {

    private final UserRepository userRepository;

    public DisableUserUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public CreateUserResult disable(DisableUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var existingUser = userRepository.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在 (ID: " + command.userId() + ")"));
        if ((command.tenantId() == 10001L && command.userId() == 100001L)
                || "superadmin".equalsIgnoreCase(existingUser.username())) {
            throw new IllegalArgumentException("超级系统管理员账号受系统核心安全保护，不可进行禁用操作");
        }
        if (existingUser.status() == UserStatus.DISABLED) {
            return new CreateUserResult(existingUser.id(), existingUser.status(), existingUser.version());
        }
        long version = (command.version() > 0) ? command.version() : existingUser.version();
        userRepository.updateStatus(
                command.tenantId(), command.userId(), UserStatus.DISABLED, version);
        var user = userRepository.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new IllegalStateException("user not found after disable"));
        return new CreateUserResult(user.id(), user.status(), user.version());
    }
}
