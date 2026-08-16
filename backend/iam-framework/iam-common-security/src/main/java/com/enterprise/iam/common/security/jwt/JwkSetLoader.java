package com.enterprise.iam.common.security.jwt;

@FunctionalInterface
public interface JwkSetLoader {

    String load();
}
