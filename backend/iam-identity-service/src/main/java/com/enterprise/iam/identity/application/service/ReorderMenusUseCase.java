package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;

import java.util.Map;
import java.util.Objects;

public class ReorderMenusUseCase {

    private final MenuRepository menuRepository;

    public ReorderMenusUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public void reorder(long tenantId, Map<Long, Integer> menuOrderMap) {
        Objects.requireNonNull(menuOrderMap, "menuOrderMap must not be null");
        for (var entry : menuOrderMap.entrySet()) {
            menuRepository.updateSortOrder(tenantId, entry.getKey(), entry.getValue());
        }
    }
}
