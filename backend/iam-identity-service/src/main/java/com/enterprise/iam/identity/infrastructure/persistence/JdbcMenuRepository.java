package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuPublishStatus;
import com.enterprise.iam.identity.domain.model.MenuStatus;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper.MenuRow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcMenuRepository implements MenuRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcMenuRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Menu save(Menu menu) {
        int updated = mapper.insertMenu(
                menu.id(),
                menu.tenantId(),
                menu.parentId(),
                menu.menuKey(),
                menu.title(),
                menu.path(),
                menu.icon(),
                menu.sortOrder(),
                menu.status().name(),
                menu.publishStatus().name(),
                menu.permissionCode(),
                menu.component(),
                menu.visible(),
                menu.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("menu insert did not affect exactly one row");
        }
        return menu;
    }

    @Override
    public Optional<Menu> findById(long tenantId, long menuId) {
        return Optional.ofNullable(mapper.selectMenuById(tenantId, menuId))
                .map(JdbcMenuRepository::toDomain);
    }

    @Override
    public Optional<Menu> findByKey(long tenantId, String menuKey) {
        return Optional.ofNullable(mapper.selectMenuByKey(tenantId, menuKey))
                .map(JdbcMenuRepository::toDomain);
    }

    @Override
    public List<Menu> findAll(long tenantId) {
        return mapper.selectAllMenus(tenantId).stream()
                .map(JdbcMenuRepository::toDomain)
                .toList();
    }

    @Override
    public List<Menu> findPublishedActive(long tenantId) {
        return mapper.selectPublishedActiveMenus(tenantId).stream()
                .map(JdbcMenuRepository::toDomain)
                .toList();
    }

    @Override
    public List<Menu> findPublishedActiveByUser(long tenantId, long userId) {
        return mapper.selectPublishedMenusByUserRoles(tenantId, userId).stream()
                .map(JdbcMenuRepository::toDomain)
                .toList();
    }

    @Override
    public boolean update(Menu menu, long expectedVersion) {
        int updated = mapper.updateMenu(
                menu.id(),
                menu.tenantId(),
                menu.parentId(),
                menu.menuKey(),
                menu.title(),
                menu.path(),
                menu.icon(),
                menu.sortOrder(),
                menu.permissionCode(),
                menu.component(),
                menu.visible(),
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public boolean updateStatus(long tenantId, long menuId, MenuStatus status, long expectedVersion) {
        int updated = mapper.updateMenuStatus(
                menuId,
                tenantId,
                status.name(),
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public boolean updatePublishStatus(long tenantId, long menuId, MenuPublishStatus publishStatus, long expectedVersion) {
        int updated = mapper.updateMenuPublishStatus(
                menuId,
                tenantId,
                publishStatus.name(),
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public int publishAllDrafts(long tenantId) {
        return mapper.publishAllDraftMenus(tenantId, Instant.now());
    }

    @Override
    public boolean updateSortOrder(long tenantId, long menuId, int sortOrder) {
        int updated = mapper.updateMenuSortOrder(menuId, tenantId, sortOrder, Instant.now());
        return updated == 1;
    }

    @Override
    public boolean delete(long tenantId, long menuId, long expectedVersion) {
        int updated = mapper.softDeleteMenu(menuId, tenantId, expectedVersion, Instant.now());
        return updated == 1;
    }

    private static Menu toDomain(MenuRow row) {
        return new Menu(
                row.id(),
                row.tenantId(),
                row.parentId(),
                row.menuKey(),
                row.title(),
                row.path(),
                row.icon(),
                row.sortOrder(),
                MenuStatus.valueOf(row.status()),
                MenuPublishStatus.valueOf(row.publishStatus()),
                row.permissionCode(),
                row.component(),
                row.visible(),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}
