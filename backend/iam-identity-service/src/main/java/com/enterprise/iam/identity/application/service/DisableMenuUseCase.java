package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuStatus;

import java.util.Objects;

public class DisableMenuUseCase {

    private final MenuRepository menuRepository;

    public DisableMenuUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public Menu disable(long tenantId, long menuId, long expectedVersion) {
        Menu existing = menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalArgumentException("menu not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: menu version mismatch");
        }

        boolean ok = menuRepository.updateStatus(tenantId, menuId, MenuStatus.DISABLED, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to disable menu");
        }

        return menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalStateException("disabled menu could not be reloaded"));
    }
}
