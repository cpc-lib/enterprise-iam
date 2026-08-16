package com.enterprise.iam.security.delegation;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface RequestTraceIdResolver {

    String resolve(HttpServletRequest request);
}
