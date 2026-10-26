package com.enterprise.iam.identity.application.model;

import com.enterprise.iam.identity.domain.model.TenantStatus;

public record TenantBootstrapResult(
        long tenantId,
        TenantStatus status,
        long operationId) {
}
