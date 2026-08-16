package com.enterprise.iam.auth.application.command;

import java.util.Arrays;
import java.util.Objects;

/**
 * One-shot refresh rotation command. The rotator destroys the presented token
 * buffer when {@code rotate} completes.
 */
public final class RefreshRotationCommand {

    private static final int MINIMUM_TOKEN_LENGTH = 32;
    private static final int MAXIMUM_TOKEN_LENGTH = 256;

    private final String requestId;
    private final char[] presentedToken;
    private boolean destroyed;

    public RefreshRotationCommand(char[] presentedToken, String requestId) {
        Objects.requireNonNull(presentedToken, "presentedToken must not be null");
        if (presentedToken.length < MINIMUM_TOKEN_LENGTH
                || presentedToken.length > MAXIMUM_TOKEN_LENGTH) {
            throw new IllegalArgumentException(
                    "presented token length must be between "
                            + MINIMUM_TOKEN_LENGTH + " and " + MAXIMUM_TOKEN_LENGTH);
        }
        this.requestId = requireRequestId(requestId);
        this.presentedToken = Arrays.copyOf(presentedToken, presentedToken.length);
    }

    public String requestId() {
        return requestId;
    }

    public synchronized char[] presentedTokenCopy() {
        if (destroyed) {
            throw new IllegalStateException(
                    "refresh rotation command has already been consumed");
        }
        return Arrays.copyOf(presentedToken, presentedToken.length);
    }

    public synchronized void destroy() {
        Arrays.fill(presentedToken, '\0');
        destroyed = true;
    }

    public synchronized boolean isDestroyed() {
        if (!destroyed) {
            return false;
        }
        for (char value : presentedToken) {
            if (value != '\0') {
                return false;
            }
        }
        return true;
    }

    private static String requireRequestId(String value) {
        Objects.requireNonNull(value, "requestId must not be null");
        String normalized = value.trim();
        if (normalized.length() < 8 || normalized.length() > 128) {
            throw new IllegalArgumentException(
                    "requestId length must be between 8 and 128");
        }
        return normalized;
    }
}
