package com.enterprise.iam.authorization.domain.model;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record AuthorizationRequest(
        long tenantId,
        long subjectId,
        long sessionId,
        long resourceId,
        long operationId,
        String resourceInstanceId,
        long permissionVersion,
        String requestId,
        Map<String, Object> context) {

    public static final int MAX_CONTEXT_PROPERTIES = 32;
    public static final int MAX_INSTANCE_ID_LENGTH = 128;
    public static final int MAX_REQUEST_ID_LENGTH = 128;

    public AuthorizationRequest {
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        requirePositive(resourceId, "resourceId");
        requirePositive(operationId, "operationId");
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("permissionVersion must not be negative");
        }
        requestId = normalizeRequired(requestId, "requestId", 8, MAX_REQUEST_ID_LENGTH);
        resourceInstanceId = normalizeOptional(resourceInstanceId, MAX_INSTANCE_ID_LENGTH);
        context = immutableContext(context);
    }

    private static Map<String, Object> immutableContext(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > MAX_CONTEXT_PROPERTIES) {
            throw new IllegalArgumentException("context must not exceed 32 properties");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = normalizeRequired(key, "context key", 1, 64);
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Number)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("context values must be scalar JSON values");
            }
            copy.put(normalizedKey, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String normalizeOptional(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("resourceInstanceId exceeds maximum length");
        }
        return normalized;
    }

    private static String normalizeRequired(String value, String name, int minimumLength, int maximumLength) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.length() < minimumLength || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " length must be between " + minimumLength + " and " + maximumLength);
        }
        return normalized;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
