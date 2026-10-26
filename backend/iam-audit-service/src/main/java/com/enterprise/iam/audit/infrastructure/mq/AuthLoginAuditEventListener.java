package com.enterprise.iam.audit.infrastructure.mq;

import com.enterprise.iam.audit.application.command.RecordSourcedAuditLogCommand;
import com.enterprise.iam.audit.application.service.RecordSourcedAuditLogUseCase;
import com.enterprise.iam.audit.domain.model.AuditAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Turns authentication login lifecycle events into durable audit records.
 * Invalid envelopes are poison messages and are rejected without requeue;
 * persistence failures are left retryable.
 */
public final class AuthLoginAuditEventListener {

    public static final String EVENT_LOGIN_SUCCEEDED = "auth.login.succeeded";
    public static final String EVENT_ORPHAN_RECONCILED = "auth.login.orphan-reconciled";
    public static final String EVENT_PASSWORD_CHANGED = "auth.password.changed";
    public static final String ORPHAN_REASON = "LOGIN_DELIVERY_UNCONFIRMED";
    public static final String PASSWORD_CHANGED_REASON = "PASSWORD_CHANGED";

    private static final String SOURCE_SERVICE = "iam-auth-service";
    private static final String AGGREGATE_TYPE = "LOGIN_SESSION";
    private static final int SCHEMA_VERSION = 1;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Logger log = LoggerFactory.getLogger(AuthLoginAuditEventListener.class);

    private final RecordSourcedAuditLogUseCase useCase;
    private final ObjectMapper objectMapper;

    public AuthLoginAuditEventListener(
            RecordSourcedAuditLogUseCase useCase,
            ObjectMapper objectMapper) {
        this.useCase = Objects.requireNonNull(useCase);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @RabbitListener(queues = "${iam.audit.auth-login.queue:iam.audit.auth-login}")
    public void onMessage(Message message) {
        RecordSourcedAuditLogCommand command;
        try {
            command = decode(message);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            log.warn("rejecting invalid auth login audit event: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("INVALID_AUTH_LOGIN_AUDIT_EVENT", e);
        }

        boolean inserted = useCase.record(command);
        if (!inserted) {
            log.debug("ignored duplicate auth audit event {}:{}",
                    command.sourceService(), command.sourceEventId());
        }
    }

    private RecordSourcedAuditLogCommand decode(Message message) throws JsonProcessingException {
        Objects.requireNonNull(message, "message must not be null");
        String routingKey = requiredText(message.getMessageProperties().getReceivedRoutingKey(), "routingKey");
        String eventType = requiredHeaderText(message, "iam-event-type");
        if (!eventType.equals(routingKey)) {
            throw new IllegalArgumentException("iam-event-type must match routingKey");
        }

        String action = switch (eventType) {
            case EVENT_LOGIN_SUCCEEDED -> AuditAction.LOGIN_SUCCESS.code();
            case EVENT_ORPHAN_RECONCILED -> AuditAction.LOGIN_ORPHAN_RECONCILED.code();
            case EVENT_PASSWORD_CHANGED -> AuditAction.PASSWORD_ROTATE.code();
            default -> throw new IllegalArgumentException("unsupported event type: " + eventType);
        };

        long eventId = requiredPositiveLong(header(message, "iam-event-id"), "iam-event-id");
        long schemaVersion = requiredPositiveLong(header(message, "iam-schema-version"), "iam-schema-version");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported iam-schema-version: " + schemaVersion);
        }
        long headerTenantId = requiredPositiveLong(header(message, "iam-tenant-id"), "iam-tenant-id");
        String aggregateType = requiredHeaderText(message, "iam-aggregate-type");
        if (!AGGREGATE_TYPE.equals(aggregateType)) {
            throw new IllegalArgumentException("iam-aggregate-type must be " + AGGREGATE_TYPE);
        }
        long headerAggregateId = requiredPositiveLong(header(message, "iam-aggregate-id"), "iam-aggregate-id");

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
        long tenantId = requiredPositiveLong(payload.get("tenantId"), "tenantId");
        long userId = requiredPositiveLong(payload.get("userId"), "userId");
        long sessionId = requiredPositiveLong(payload.get("sessionId"), "sessionId");
        String requestId = requiredText(payload.get("requestId"), "requestId");
        Instant occurredAt = requiredInstant(payload.get("occurredAt"), "occurredAt");
        String reason = optionalText(payload.get("reason"), "reason");

        if (EVENT_LOGIN_SUCCEEDED.equals(eventType) && reason != null) {
            throw new IllegalArgumentException("reason is forbidden for a successful login");
        }
        if (EVENT_ORPHAN_RECONCILED.equals(eventType) && !ORPHAN_REASON.equals(reason)) {
            throw new IllegalArgumentException(
                    "reason must be " + ORPHAN_REASON + " for orphan reconciliation");
        }
        if (EVENT_PASSWORD_CHANGED.equals(eventType) && !PASSWORD_CHANGED_REASON.equals(reason)) {
            throw new IllegalArgumentException(
                    "reason must be " + PASSWORD_CHANGED_REASON + " for password change");
        }

        if (headerTenantId != tenantId) {
            throw new IllegalArgumentException("iam-tenant-id must match payload tenantId");
        }
        if (headerAggregateId != sessionId) {
            throw new IllegalArgumentException("iam-aggregate-id must match payload sessionId");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requestId", requestId);
        if (reason != null) {
            detail.put("reason", reason);
        }

        return new RecordSourcedAuditLogCommand(
                SOURCE_SERVICE,
                eventId,
                tenantId,
                userId,
                action,
                "SESSION",
                sessionId,
                objectMapper.writeValueAsString(detail),
                occurredAt);
    }

    private static Object header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requiredHeaderText(Message message, String name) {
        return requiredText(header(message, name), name);
    }

    private static String requiredText(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string");
        }
        return text;
    }

    private static String optionalText(Object value, String name) {
        if (value == null) {
            return null;
        }
        return requiredText(value, name);
    }

    private static long requiredPositiveLong(Object value, String name) {
        if (value instanceof Number number) {
            long result = number.longValue();
            if (result > 0 && number.doubleValue() == (double) result) {
                return result;
            }
        } else if (value instanceof String text) {
            try {
                long result = Long.parseLong(text.trim());
                if (result > 0) {
                    return result;
                }
            } catch (NumberFormatException ignored) {
                // Report the common validation message below.
            }
        }
        throw new IllegalArgumentException(name + " must be a positive integer");
    }

    private static Instant requiredInstant(Object value, String name) {
        try {
            return Instant.parse(requiredText(value, name));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 instant");
        }
    }
}
