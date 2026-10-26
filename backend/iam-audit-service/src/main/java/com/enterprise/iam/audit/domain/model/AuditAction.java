package com.enterprise.iam.audit.domain.model;

/**
 * Catalog of standard audit action codes. The audit service accepts arbitrary
 * action strings from upstream services; this enum documents the well-known
 * codes used across the IAM platform.
 */
public enum AuditAction {

    LOGIN_SUCCESS("LOGIN_SUCCESS"),
    LOGIN_ORPHAN_RECONCILED("LOGIN_ORPHAN_RECONCILED"),
    LOGIN_FAILURE("LOGIN_FAILURE"),
    LOGOUT("LOGOUT"),
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    GRANT("GRANT"),
    REVOKE("REVOKE"),
    ACCESS("ACCESS"),
    ACCESS_DENIED("ACCESS_DENIED"),
    TENANT_BOOTSTRAP("TENANT_BOOTSTRAP"),
    PASSWORD_ROTATE("PASSWORD_ROTATE");

    private final String code;

    AuditAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
