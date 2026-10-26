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
    TEAM_ROLE_GRANT,
    ABAC_POLICY_ALLOW,
    ABAC_POLICY_DENY,
    SHARE_GRANT,
    SHARE_PROJECTION_STALE,
    NO_MATCHING_GRANT;

    public String code() {
        return "IAM_AUTHZ_" + name();
    }
}
