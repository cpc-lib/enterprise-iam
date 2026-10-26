package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DepartmentNode {

    private final long id;
    private final long tenantId;
    private final String orgCode;
    private final String orgName;
    private final Long parentId;
    private final String materializedPath;
    private final int depth;
    private final OrganizationStatus status;
    private final long version;
    private final int memberCount;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<DepartmentNode> children;

    public DepartmentNode(
            long id,
            long tenantId,
            String orgCode,
            String orgName,
            Long parentId,
            String materializedPath,
            int depth,
            OrganizationStatus status,
            long version,
            int memberCount,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.orgCode = orgCode;
        this.orgName = orgName;
        this.parentId = parentId;
        this.materializedPath = materializedPath;
        this.depth = depth;
        this.status = status;
        this.version = version;
        this.memberCount = memberCount;
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

    public String orgCode() {
        return orgCode;
    }

    public String orgName() {
        return orgName;
    }

    public Long parentId() {
        return parentId;
    }

    public String materializedPath() {
        return materializedPath;
    }

    public int depth() {
        return depth;
    }

    public OrganizationStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public int memberCount() {
        return memberCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<DepartmentNode> children() {
        return children;
    }

    public void addChild(DepartmentNode child) {
        this.children.add(child);
    }
}
