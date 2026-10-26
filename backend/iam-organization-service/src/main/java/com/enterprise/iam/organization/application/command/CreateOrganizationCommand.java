package com.enterprise.iam.organization.application.command;

import java.util.Objects;

public record CreateOrganizationCommand(
        long tenantId,
        String orgCode,
        String orgName,
        Long parentId,
        String requestId) {

    public CreateOrganizationCommand {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (parentId != null && parentId <= 0) {
            throw new IllegalArgumentException("parentId must be positive when present");
        }
        Objects.requireNonNull(orgCode, "orgCode must not be null");
        Objects.requireNonNull(orgName, "orgName must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
