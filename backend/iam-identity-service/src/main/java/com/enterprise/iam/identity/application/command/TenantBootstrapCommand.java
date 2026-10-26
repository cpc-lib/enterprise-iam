package com.enterprise.iam.identity.application.command;

import com.enterprise.iam.identity.domain.model.IdentityType;

import java.util.Objects;

public record TenantBootstrapCommand(
        String tenantCode,
        String tenantName,
        IdentityType identityType,
        String identity,
        String displayName,
        String initialPassword,
        String requestId) {

    public TenantBootstrapCommand {
        Objects.requireNonNull(tenantCode, "tenantCode must not be null");
        Objects.requireNonNull(tenantName, "tenantName must not be null");
        Objects.requireNonNull(identityType, "identityType must not be null");
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(initialPassword, "initialPassword must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
