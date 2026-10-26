package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.domain.model.Role;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcRoleRepository implements RoleRepository {

    private final IdentityPersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;

    public JdbcRoleRepository(IdentityPersistenceMapper mapper) {
        this(mapper, null);
    }

    public JdbcRoleRepository(IdentityPersistenceMapper mapper, PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = idGenerator;
    }

    @Override
    public Role save(Role role) {
        int updated = mapper.insertRole(
                role.id(),
                role.tenantId(),
                role.roleCode(),
                role.roleName(),
                role.description(),
                role.priority(),
                role.status().name(),
                role.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("role insert did not affect exactly one row");
        }
        return role;
    }

    @Override
    public Optional<Role> findById(long tenantId, long roleId) {
        return Optional.ofNullable(mapper.selectRoleById(tenantId, roleId));
    }

    @Override
    public Optional<Role> findByCode(long tenantId, String roleCode) {
        return Optional.ofNullable(mapper.selectRoleByCode(tenantId, roleCode));
    }

    @Override
    public List<Role> findAll(Long tenantId) {
        return mapper.selectRoles(tenantId, null);
    }

    @Override
    public List<Role> findAll(Long tenantId, Long userId) {
        return mapper.selectRoles(tenantId, userId);
    }

    @Override
    public List<Role> findRolesByUserId(Long tenantId, long userId) {
        return mapper.selectRolesByUserId(tenantId, userId);
    }

    @Override
    public List<Long> findRoleMenuIds(long tenantId, long roleId) {
        return mapper.selectRoleMenuIds(roleId);
    }

    @Override
    public void replaceRoleMenus(long tenantId, long roleId, List<Long> menuIds) {
        Long roleTenantId = mapper.selectTenantIdByRoleId(roleId);
        long targetTenantId = (roleTenantId != null && roleTenantId > 0) ? roleTenantId : tenantId;
        mapper.deleteRoleMenus(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            Instant now = Instant.now();
            for (Long menuId : menuIds) {
                if (menuId != null) {
                    long id = (idGenerator != null) ? idGenerator.nextId() : System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
                    mapper.insertRoleMenu(id, targetTenantId, roleId, menuId, now);
                }
            }
        }
    }

    @Override
    public void updateStatus(long tenantId, long roleId, com.enterprise.iam.identity.domain.model.RoleStatus status, Long expectedVersion) {
        int updated = mapper.updateRoleStatus(tenantId, roleId, status, expectedVersion, Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("更新角色状态失败，角色可能不存在或版本已被修改");
        }
    }

    @Override
    public void delete(long tenantId, long roleId, Long expectedVersion) {
        int deleted = mapper.softDeleteRole(tenantId, roleId, expectedVersion, Instant.now());
        if (deleted == 0) {
            throw new IllegalStateException("删除角色失败，角色可能不存在或版本已被修改");
        }
        Instant now = Instant.now();
        mapper.deleteRoleMenus(roleId);
        mapper.revokeUserRolesByRoleId(roleId, now);
    }

    @Override
    public Long findTenantIdByRoleId(long roleId) {
        return mapper.selectTenantIdByRoleId(roleId);
    }
}
