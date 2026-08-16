package com.enterprise.iam.authorization.domain.model;

public enum AuthorizationReason {
    DEPENDENCY_UNAVAILABLE,
    TENANT_MISMATCH,
    RESOURCE_CONTEXT_MISMATCH,
    STALE_PERMISSION_VERSION,
    PERMISSION_VERSION_MISMATCH,
    RESOURCE_OPERATION_DISABLED,
    EXPLICIT_DENY,
    GRANT_ALLOW,
    NO_MATCHING_GRANT;

    public String code() {
        return "IAM_AUTHZ_" + name();
    }
}
