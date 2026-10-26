package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Menu {

    private final long id;
    private final long tenantId;
    private final Long parentId;
    private final String menuKey;
    private final String title;
    private final String path;
    private final String icon;
    private final int sortOrder;
    private final MenuStatus status;
    private final MenuPublishStatus publishStatus;
    private final String permissionCode;
    private final String component;
    private final boolean visible;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Menu(
            long id,
            long tenantId,
            Long parentId,
            String menuKey,
            String title,
            String path,
            String icon,
            int sortOrder,
            MenuStatus status,
            MenuPublishStatus publishStatus,
            String permissionCode,
            String component,
            boolean visible,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        if (parentId != null && parentId <= 0) {
            throw new IllegalArgumentException("parentId must be positive when present");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.menuKey = Objects.requireNonNull(menuKey, "menuKey must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.path = path;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.publishStatus = Objects.requireNonNull(publishStatus, "publishStatus must not be null");
        this.permissionCode = permissionCode;
        this.component = component;
        this.visible = visible;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public Long parentId() {
        return parentId;
    }

    public String menuKey() {
        return menuKey;
    }

    public String title() {
        return title;
    }

    public String path() {
        return path;
    }

    public String icon() {
        return icon;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public MenuStatus status() {
        return status;
    }

    public MenuPublishStatus publishStatus() {
        return publishStatus;
    }

    public String permissionCode() {
        return permissionCode;
    }

    public String component() {
        return component;
    }

    public boolean visible() {
        return visible;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
