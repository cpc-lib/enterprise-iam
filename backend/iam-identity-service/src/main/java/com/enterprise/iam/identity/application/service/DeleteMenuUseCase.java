package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;

import java.util.Objects;

public class DeleteMenuUseCase {

    private final MenuRepository menuRepository;

    public DeleteMenuUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public void delete(long tenantId, long menuId, long expectedVersion) {
        Menu existing = menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalArgumentException("menu not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: menu version mismatch");
        }

        boolean ok = menuRepository.delete(tenantId, menuId, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to delete menu");
        }
    }
}
