package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.text.Normalizer;
import java.util.Objects;
import java.util.UUID;

public class ResetUserPasswordUseCase {

    private final UserRepository userRepository;
    private final Argon2PasswordEncoder passwordEncoder;

    public ResetUserPasswordUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    public record ResetPasswordResult(long userId, long tenantId, String newPassword, boolean isTemporary) {}

    public ResetPasswordResult resetPassword(
            long callerTenantId,
            Long targetTenantId,
            long targetUserId,
            String proposedPassword,
            String reason) {
        long effectiveTenantId = (targetTenantId != null && targetTenantId > 0)
                ? targetTenantId
                : callerTenantId;

        User targetUser = userRepository.findById(effectiveTenantId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在 (ID: " + targetUserId + ")"));

        boolean isSuperAdmin = (callerTenantId == 10001L);
        boolean isTargetAdmin = userRepository.isTenantAdmin(effectiveTenantId, targetUserId)
                || "tenant_admin".equalsIgnoreCase(targetUser.username());
        boolean isTargetSuperAdmin = (targetUserId == 100001L)
                || "superadmin".equalsIgnoreCase(targetUser.username())
                || effectiveTenantId == 10001L;

        if (isTargetSuperAdmin) {
            // 系统超级管理员账号：仅允许系统管理员重置
            if (!isSuperAdmin) {
                throw new SecurityException("无权重置系统超级管理员密码");
            }
        } else if (isTargetAdmin) {
            // 租户管理员的密码只能由系统管理员重置
            if (!isSuperAdmin) {
                throw new SecurityException("租户管理员的密码只能由系统管理员重置");
            }
        } else {
            // 租户下的普通用户：
            // 1) 系统管理员不能重置租户下的普通用户（须由该租户管理员重置）
            if (isSuperAdmin) {
                throw new SecurityException("系统管理员不能重置租户下的普通用户，须由该租户管理员进行重置");
            }
            // 2) 租户管理员可以重置该租户用户的密码（严禁跨租户）
            if (callerTenantId != effectiveTenantId) {
                throw new SecurityException("无权跨租户重置用户密码");
            }
        }

        String finalPassword;
        boolean isTemporary;
        if (proposedPassword != null && !proposedPassword.trim().isEmpty()) {
            finalPassword = proposedPassword.trim();
            if (finalPassword.length() < 8 || finalPassword.length() > 128) {
                throw new IllegalArgumentException("新密码长度须为 8–128 个字符");
            }
            isTemporary = false;
        } else {
            finalPassword = "Admin@" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "!";
            isTemporary = true;
        }

        String normalized = Normalizer.normalize(finalPassword, Normalizer.Form.NFC);
        String passwordPhc = passwordEncoder.encode(normalized);

        userRepository.resetPassword(effectiveTenantId, targetUserId, passwordPhc);

        return new ResetPasswordResult(targetUserId, effectiveTenantId, finalPassword, isTemporary);
    }
}
