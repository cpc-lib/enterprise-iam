package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuPublishStatus;

import java.util.Objects;

public class PublishMenuUseCase {

    private final MenuRepository menuRepository;

    public PublishMenuUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public Menu publish(long tenantId, long menuId, long expectedVersion) {
        Menu existing = menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalArgumentException("menu not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: menu version mismatch");
        }

        boolean ok = menuRepository.updatePublishStatus(tenantId, menuId, MenuPublishStatus.PUBLISHED, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to publish menu");
        }

        return menuRepository.findById(tenantId, menuId)
                .orElseThrow(() -> new IllegalStateException("published menu could not be reloaded"));
    }

    public int publishAll(long tenantId) {
        return menuRepository.publishAllDrafts(tenantId);
    }
}
