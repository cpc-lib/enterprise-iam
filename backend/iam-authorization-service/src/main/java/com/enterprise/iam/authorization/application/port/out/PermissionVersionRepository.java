package com.enterprise.iam.authorization.application.port.out;

public interface PermissionVersionRepository {

    long getTenantPermissionVersion(long tenantId);

    long incrementTenantPermissionVersion(long tenantId, String reason);
}
