package com.enterprise.iam.common.security.headers;

import java.util.Set;

public final class UntrustedIdentityHeaders {

    public static final Set<String> NAMES = Set.of(
            "X-Tenant-Id",
            "X-User-Id",
            "X-Subject-Id",
            "X-Session-Id",
            "X-Resource-Id",
            "X-Forwarded-User",
            "X-Forwarded-Tenant",
            "X-IAM-Trusted-Context",
            "X-IAM-Delegation",
            "X-Service-Token");

    private UntrustedIdentityHeaders() {
    }
}
