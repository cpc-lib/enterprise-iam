package com.enterprise.iam.auth.application.command;

import java.util.Arrays;
import java.util.Objects;

/** One-shot password-change command whose credential buffers are destroyed after use. */
public final class ChangePasswordCommand implements AutoCloseable {

    private static final int MINIMUM_REFRESH_TOKEN_LENGTH = 32;
    private static final int MAXIMUM_REFRESH_TOKEN_LENGTH = 256;
    private static final int MAXIMUM_PASSWORD_BUFFER_LENGTH = 512;

    private final char[] presentedRefreshToken;
    private final char[] currentPassword;
    private final char[] newPassword;
    private final long expectedTenantId;
    private final long expectedUserId;
    private final long expectedSessionId;
    private final String requestId;
    private boolean destroyed;

    public ChangePasswordCommand(
            char[] presentedRefreshToken,
            char[] currentPassword,
            char[] newPassword,
            long expectedTenantId,
            long expectedUserId,
            long expectedSessionId,
            String requestId) {
        this.presentedRefreshToken = copyRefreshToken(presentedRefreshToken);
        this.currentPassword = copyPassword(currentPassword, "currentPassword");
        this.newPassword = copyPassword(newPassword, "newPassword");
        this.expectedTenantId = requirePositive(expectedTenantId, "expectedTenantId");
        this.expectedUserId = requirePositive(expectedUserId, "expectedUserId");
        this.expectedSessionId = requirePositive(expectedSessionId, "expectedSessionId");
        this.requestId = requireRequestId(requestId);
    }

    public long expectedTenantId() {
        return expectedTenantId;
    }

    public long expectedUserId() {
        return expectedUserId;
    }

    public long expectedSessionId() {
        return expectedSessionId;
    }

    public String requestId() {
        return requestId;
    }

    public synchronized char[] presentedRefreshTokenCopy() {
        return copyAvailable(presentedRefreshToken);
    }

    public synchronized char[] currentPasswordCopy() {
        return copyAvailable(currentPassword);
    }

    public synchronized char[] newPasswordCopy() {
        return copyAvailable(newPassword);
    }

    @Override
    public synchronized void close() {
        destroy();
    }

    public synchronized void destroy() {
        Arrays.fill(presentedRefreshToken, '\0');
        Arrays.fill(currentPassword, '\0');
        Arrays.fill(newPassword, '\0');
        destroyed = true;
    }

    public synchronized boolean isDestroyed() {
        return destroyed
                && cleared(presentedRefreshToken)
                && cleared(currentPassword)
                && cleared(newPassword);
    }

    private char[] copyAvailable(char[] source) {
        if (destroyed) {
            throw new IllegalStateException("password change command has already been consumed");
        }
        return Arrays.copyOf(source, source.length);
    }

    private static char[] copyRefreshToken(char[] value) {
        Objects.requireNonNull(value, "presentedRefreshToken must not be null");
        if (value.length < MINIMUM_REFRESH_TOKEN_LENGTH
                || value.length > MAXIMUM_REFRESH_TOKEN_LENGTH) {
            throw new IllegalArgumentException(
                    "presentedRefreshToken length must be between 32 and 256");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static char[] copyPassword(char[] value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.length == 0 || value.length > MAXIMUM_PASSWORD_BUFFER_LENGTH) {
            throw new IllegalArgumentException(
                    name + " length must be between 1 and " + MAXIMUM_PASSWORD_BUFFER_LENGTH);
        }
        return Arrays.copyOf(value, value.length);
    }

    private static String requireRequestId(String value) {
        Objects.requireNonNull(value, "requestId must not be null");
        String normalized = value.trim();
        if (normalized.length() < 8 || normalized.length() > 128) {
            throw new IllegalArgumentException("requestId length must be between 8 and 128");
        }
        return normalized;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static boolean cleared(char[] value) {
        for (char character : value) {
            if (character != '\0') {
                return false;
            }
        }
        return true;
    }
}
