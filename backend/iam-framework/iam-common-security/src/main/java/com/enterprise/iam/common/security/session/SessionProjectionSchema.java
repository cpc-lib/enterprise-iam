package com.enterprise.iam.common.security.session;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Frozen Redis hash key and field codec shared by the writer and reader. */
public final class SessionProjectionSchema {

    public static final String SCHEMA_VERSION = "1";
    public static final String KEY_PREFIX = "iam:session-security:";

    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_SUBJECT_ID = "subjectId";
    public static final String FIELD_SESSION_ID = "sessionId";
    public static final String FIELD_TOKEN_VERSION = "tokenVersion";
    public static final String FIELD_SESSION_VERSION = "sessionVersion";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_IDLE_EXPIRES_AT = "idleExpiresAtEpochMs";
    public static final String FIELD_ABSOLUTE_EXPIRES_AT = "absoluteExpiresAtEpochMs";

    public static final List<String> FIELDS = List.of(
            FIELD_SCHEMA_VERSION,
            FIELD_TENANT_ID,
            FIELD_SUBJECT_ID,
            FIELD_SESSION_ID,
            FIELD_TOKEN_VERSION,
            FIELD_SESSION_VERSION,
            FIELD_STATUS,
            FIELD_IDLE_EXPIRES_AT,
            FIELD_ABSOLUTE_EXPIRES_AT);

    private static final Pattern POSITIVE_DECIMAL =
            Pattern.compile("^[1-9][0-9]{0,18}$");
    private static final int MAX_FIELD_LENGTH = 128;

    private SessionProjectionSchema() {
    }

    /** Uses one Redis Cluster hash tag without accepting any untrusted key text. */
    public static String redisKey(long tenantId, long sessionId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(sessionId, "sessionId");
        return KEY_PREFIX + "{" + tenantId + ":" + sessionId + "}";
    }

    public static Map<String, String> encode(SessionSecurityProjection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        encoded.put(FIELD_TENANT_ID, Long.toString(projection.tenantId()));
        encoded.put(FIELD_SUBJECT_ID, Long.toString(projection.subjectId()));
        encoded.put(FIELD_SESSION_ID, Long.toString(projection.sessionId()));
        encoded.put(FIELD_TOKEN_VERSION, Long.toString(projection.tokenVersion()));
        encoded.put(FIELD_SESSION_VERSION, Long.toString(projection.sessionVersion()));
        encoded.put(FIELD_STATUS, projection.status().name());
        encoded.put(FIELD_IDLE_EXPIRES_AT,
                Long.toString(projection.idleExpiresAt().toEpochMilli()));
        encoded.put(FIELD_ABSOLUTE_EXPIRES_AT,
                Long.toString(projection.absoluteExpiresAt().toEpochMilli()));
        return Map.copyOf(encoded);
    }

    /** Decodes exactly the values returned by HMGET in {@link #FIELDS} order. */
    public static SessionSecurityProjection decode(List<String> values) {
        if (values == null || values.size() != FIELDS.size()) {
            throw new SessionProjectionFormatException(
                    "session projection field count is invalid");
        }
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > MAX_FIELD_LENGTH) {
                throw new SessionProjectionFormatException(
                        "session projection contains a missing or oversized field");
            }
        }
        if (!SCHEMA_VERSION.equals(values.get(0))) {
            throw new SessionProjectionFormatException(
                    "session projection schema version is unsupported");
        }
        try {
            return new SessionSecurityProjection(
                    positiveLong(values.get(1), FIELD_TENANT_ID),
                    positiveLong(values.get(2), FIELD_SUBJECT_ID),
                    positiveLong(values.get(3), FIELD_SESSION_ID),
                    positiveLong(values.get(4), FIELD_TOKEN_VERSION),
                    positiveLong(values.get(5), FIELD_SESSION_VERSION),
                    SessionProjectionStatus.valueOf(values.get(6)),
                    epochMillis(values.get(7), FIELD_IDLE_EXPIRES_AT),
                    epochMillis(values.get(8), FIELD_ABSOLUTE_EXPIRES_AT));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new SessionProjectionFormatException(
                    "session projection value is invalid", exception);
        }
    }

    public static boolean isCompletelyAbsent(List<String> values) {
        return values == null
                || values.isEmpty()
                || values.stream().allMatch(Objects::isNull);
    }

    private static long positiveLong(String value, String field) {
        if (!POSITIVE_DECIMAL.matcher(value).matches()) {
            throw new SessionProjectionFormatException(field + " is not a positive integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new SessionProjectionFormatException(field + " is out of range", exception);
        }
    }

    private static Instant epochMillis(String value, String field) {
        long epochMillis = positiveLong(value, field);
        return Instant.ofEpochMilli(epochMillis);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
