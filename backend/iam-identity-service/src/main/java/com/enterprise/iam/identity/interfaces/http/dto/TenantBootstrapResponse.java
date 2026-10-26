package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.identity.domain.model.TenantStatus;

public record TenantBootstrapResponse(long tenantId, TenantStatus status, long operationId) {
}
