package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserStatus;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class JdbcUserRepository implements UserRepository {

    private final IdentityPersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;
    private final Argon2PasswordEncoder passwordEncoder;

    public JdbcUserRepository(IdentityPersistenceMapper mapper) {
        this(mapper, null);
    }

    public JdbcUserRepository(IdentityPersistenceMapper mapper, PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.idGenerator = idGenerator;
        this.passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Override
    public User save(User user) {
        int updated = mapper.insertUser(
                user.id(),
                user.tenantId(),
                user.username(),
                user.displayName(),
                user.email(),
                user.phone(),
                user.departmentId(),
                user.positionId(),
                user.status().name(),
                user.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("user insert did not affect exactly one row");
        }
        return user;
    }

    @Override
    public Optional<User> findById(long tenantId, long userId) {
        return Optional.ofNullable(mapper.selectUserById(tenantId, userId));
    }

    @Override
    public void updateStatus(long tenantId, long userId, UserStatus status, long expectedVersion) {
        int updated = mapper.updateUserStatus(
                tenantId,
                userId,
                status.name(),
                expectedVersion,
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("user status update conflict");
        }
    }

    @Override
    public boolean updateUser(
            long tenantId,
            long userId,
            String displayName,
            String email,
            String phone,
            Long departmentId,
            Long positionId,
            long expectedVersion) {
        int updated = mapper.updateUser(
                tenantId,
                userId,
                displayName,
                email,
                phone,
                departmentId,
                positionId,
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public List<User> listUsers(
            Long tenantId,
            String keyword,
            Long departmentId,
            Long positionId,
            UserStatus status,
            int offset,
            int limit) {
        String statusStr = status == null ? null : status.name();
        return mapper.listUsers(tenantId, keyword, departmentId, positionId, statusStr, offset, limit);
    }

    @Override
    public long countUsers(
            Long tenantId,
            String keyword,
            Long departmentId,
            Long positionId,
            UserStatus status) {
        String statusStr = status == null ? null : status.name();
        return mapper.countUsers(tenantId, keyword, departmentId, positionId, statusStr);
    }

    @Override
    public List<User> listUsersByRoleId(Long tenantId, long roleId) {
        return mapper.selectUsersByRoleId(tenantId, roleId);
    }

    @Override
    public boolean isTenantAdmin(long tenantId, long userId) {
        User user = mapper.selectUserById(tenantId, userId);
        if (user != null && "tenant_admin".equalsIgnoreCase(user.username())) {
            return true;
        }
        if (mapper.countTenantAdminRolesByUser(tenantId, userId) > 0) {
            return true;
        }
        Long minUserId = mapper.selectMinUserIdByTenantId(tenantId);
        return minUserId != null && minUserId == userId;
    }

    @Override
    public void resetPassword(long tenantId, long userId, String passwordPhc) {
        Instant now = Instant.now();
        long credentialId = nextId();
        long securityStateId = nextId();
        mapper.upsertPasswordCredential(credentialId, tenantId, userId, passwordPhc, now);
        mapper.upsertUserSecurityState(securityStateId, tenantId, userId, now);
        mapper.revokeAllUserSessions(tenantId, userId, now);
        mapper.revokeAllUserRefreshTokens(tenantId, userId, now);
    }

    private long nextId() {
        if (idGenerator != null) {
            return idGenerator.nextId();
        }
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    @Override
    public void saveUserIdentity(
            long id,
            long tenantId,
            long userId,
            String identityType,
            String identityKey,
            Long credentialRef,
            String status) {
        mapper.insertUserIdentity(
                id,
                tenantId,
                userId,
                identityType,
                identityKey,
                credentialRef,
                status != null ? status : "ACTIVE",
                Instant.now());
    }

    @Override
    public void initUserPassword(long tenantId, long userId, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        String normalized = Normalizer.normalize(rawPassword.trim(), Normalizer.Form.NFC);
        String passwordPhc = passwordEncoder.encode(normalized);
        resetPassword(tenantId, userId, passwordPhc);
    }

    @Override
    public Long findTenantIdByUserId(long userId) {
        return mapper.selectTenantIdByUserId(userId);
    }
}
