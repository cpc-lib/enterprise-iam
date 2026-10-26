package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MenuNode {

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
    private final List<MenuNode> children;

    public MenuNode(
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
        this.id = id;
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.menuKey = menuKey;
        this.title = title;
        this.path = path;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.status = status;
        this.publishStatus = publishStatus;
        this.permissionCode = permissionCode;
        this.component = component;
        this.visible = visible;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.children = new ArrayList<>();
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

    public List<MenuNode> children() {
        return children;
    }

    public void addChild(MenuNode child) {
        this.children.add(child);
    }
}
