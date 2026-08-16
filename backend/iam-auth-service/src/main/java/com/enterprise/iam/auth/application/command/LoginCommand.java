package com.enterprise.iam.auth.application.command;

import com.enterprise.iam.auth.domain.model.IdentityType;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/** One-shot command. AuthenticateLoginUseCase destroys its password buffer. */
public final class LoginCommand {

    private static final Pattern TENANT_CODE = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");

    private final String tenantCode;
    private final IdentityType identityType;
    private final String identity;
    private final String requestId;
    private final char[] password;
    private boolean destroyed;

    public LoginCommand(
            String tenantCode,
            IdentityType identityType,
            String identity,
            char[] password,
            String requestId) {
        this.tenantCode = requireTenantCode(tenantCode);
        this.identityType = Objects.requireNonNull(identityType, "identityType must not be null");
        this.identity = requireText(identity, "identity", 1, 320);
        this.requestId = requireText(requestId, "requestId", 8, 128);
        Objects.requireNonNull(password, "password must not be null");
        if (password.length < 1 || password.length > 128) {
            throw new IllegalArgumentException("password length must be between 1 and 128");
        }
        this.password = Arrays.copyOf(password, password.length);
    }

    public String tenantCode() {
        return tenantCode;
    }

    public IdentityType identityType() {
        return identityType;
    }

    public String identity() {
        return identity;
    }

    public String requestId() {
        return requestId;
    }

    public synchronized char[] passwordCopy() {
        if (destroyed) {
            throw new IllegalStateException("login command has already been consumed");
        }
        return Arrays.copyOf(password, password.length);
    }

    public synchronized void destroy() {
        Arrays.fill(password, '\0');
        destroyed = true;
    }

    public synchronized boolean isDestroyed() {
        if (!destroyed) {
            return false;
        }
        for (char value : password) {
            if (value != '\0') {
                return false;
            }
        }
        return true;
    }

    private static String requireTenantCode(String value) {
        Objects.requireNonNull(value, "tenantCode must not be null");
        String normalized = value.trim();
        if (!TENANT_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("tenantCode format is invalid");
        }
        return normalized;
    }

    private static String requireText(
            String value,
            String name,
            int minimumLength,
            int maximumLength) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.length() < minimumLength || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " length must be between " + minimumLength + " and " + maximumLength);
        }
        return normalized;
    }
}
