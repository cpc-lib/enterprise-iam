package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuPublishStatus;
import com.enterprise.iam.identity.domain.model.MenuStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class CreateMenuUseCase {

    private final MenuRepository menuRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateMenuUseCase(
            MenuRepository menuRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public Menu create(
            long tenantId,
            Long parentId,
            String menuKey,
            String title,
            String path,
            String icon,
            int sortOrder,
            String permissionCode,
            String component,
            boolean visible,
            boolean publishImmediately) {
        if (menuKey == null || menuKey.isBlank()) {
            throw new IllegalArgumentException("menuKey must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }

        if (menuRepository.findByKey(tenantId, menuKey).isPresent()) {
            throw new IllegalStateException("menu key already exists");
        }

        if (parentId != null) {
            menuRepository.findById(tenantId, parentId)
                    .orElseThrow(() -> new IllegalArgumentException("parent menu not found"));
        }

        Instant now = clock.instant();
        long id = idGenerator.nextId();
        Menu menu = new Menu(
                id,
                tenantId,
                parentId,
                menuKey.trim(),
                title.trim(),
                path != null ? path.trim() : null,
                icon != null ? icon.trim() : null,
                sortOrder,
                MenuStatus.ACTIVE,
                publishImmediately ? MenuPublishStatus.PUBLISHED : MenuPublishStatus.DRAFT,
                permissionCode,
                component,
                visible,
                0L,
                now,
                now);
        return menuRepository.save(menu);
    }
}
