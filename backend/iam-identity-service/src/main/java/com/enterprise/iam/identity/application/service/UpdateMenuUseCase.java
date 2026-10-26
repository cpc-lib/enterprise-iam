package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;

import java.util.Objects;

public class UpdateMenuUseCase {

    private final MenuRepository menuRepository;

    public UpdateMenuUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public Menu update(
            long tenantId,
            long menuId,
            Long parentId,
            String menuKey,
            String title,
            String path,
            String icon,
            int sortOrder,
            String permissionCode,
            String component,
            boolean visible,
            long expectedVersion) {
        Menu existing = menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalArgumentException("menu not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: menu version mismatch");
        }

        if (parentId != null && parentId == menuId) {
            throw new IllegalArgumentException("menu cannot be its own parent");
        }

        if (!existing.menuKey().equals(menuKey)) {
            menuRepository.findByKey(tenantId, menuKey).ifPresent(other -> {
                if (other.id() != menuId) {
                    throw new IllegalStateException("menu key already exists");
                }
            });
        }

        Menu updated = new Menu(
                menuId,
                tenantId,
                parentId,
                menuKey.trim(),
                title.trim(),
                path != null ? path.trim() : null,
                icon != null ? icon.trim() : null,
                sortOrder,
                existing.status(),
                existing.publishStatus(),
                permissionCode,
                component,
                visible,
                expectedVersion,
                existing.createdAt(),
                existing.updatedAt());

        boolean ok = menuRepository.update(updated, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to update menu");
        }

        return menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalStateException("updated menu could not be reloaded"));
    }
}
