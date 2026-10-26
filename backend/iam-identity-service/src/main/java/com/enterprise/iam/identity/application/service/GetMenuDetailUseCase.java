package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;

import java.util.Objects;
import java.util.Optional;

public class GetMenuDetailUseCase {

    private final MenuRepository menuRepository;

    public GetMenuDetailUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public Optional<Menu> getDetail(long tenantId, long menuId) {
        return menuRepository.findById(tenantId, menuId);
    }
}
