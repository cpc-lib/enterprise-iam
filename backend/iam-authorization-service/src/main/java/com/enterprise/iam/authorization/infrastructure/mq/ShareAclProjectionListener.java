package com.enterprise.iam.authorization.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Consumes share ACL events on the authorization projection queue. Bodies
 * are parsed as plain JSON (type headers from the publisher are deliberately
 * ignored); malformed messages are rejected without requeue so a poison
 * message cannot block the queue, while SQL failures remain retryable.
 */
public final class ShareAclProjectionListener {

    public static final String ROUTING_KEY_GRANTED = "share.acl.granted";
    public static final String ROUTING_KEY_REVOKED = "share.acl.revoked";

    private static final Logger log = LoggerFactory.getLogger(ShareAclProjectionListener.class);

    private final ShareAclProjectionApplier applier;
    private final ObjectMapper objectMapper;

    public ShareAclProjectionListener(
            ShareAclProjectionApplier applier,
            ObjectMapper objectMapper) {
        this.applier = Objects.requireNonNull(applier);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @RabbitListener(queues = "${iam.authorization.share-acl.queue:iam.authz.share-acl}")
    public void onMessage(
            @Payload String body,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        Map<String, Object> payload = parse(body);
        try {
            if (ROUTING_KEY_GRANTED.equals(routingKey)) {
                applier.applyGranted(toGranted(payload));
            } else if (ROUTING_KEY_REVOKED.equals(routingKey)) {
                applier.applyRevoked(toRevoked(payload));
            } else {
                throw new IllegalArgumentException("unsupported routing key: " + routingKey);
            }
        } catch (IllegalArgumentException e) {
            log.warn("rejecting invalid share ACL event: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("INVALID_SHARE_ACL_EVENT", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            log.warn("rejecting unparseable share ACL event: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("INVALID_SHARE_ACL_EVENT", e);
        }
    }

    private ShareAclProjectionApplier.GrantedMessage toGranted(Map<String, Object> payload) {
        String scopeType = optionalString(payload, "scopeType");
        String folderPath = optionalString(payload, "folderPath");
        String collectionJson = optionalCollectionJson(payload.get("collectionResourceIds"));
        return new ShareAclProjectionApplier.GrantedMessage(
                requiredLong(payload, "tenantId"),
                requiredLong(payload, "resourceId"),
                requiredLong(payload, "shareId"),
                requiredString(payload, "resourceInstanceKey"),
                requiredString(payload, "targetType"),
                requiredLong(payload, "targetId"),
                requiredLongList(payload, "operationIds"),
                requiredInstant(payload, "startTime"),
                requiredInstant(payload, "expireTime"),
                requiredLong(payload, "epoch"),
                parseOptionalFieldGrants(payload.get("fieldGrants")),
                scopeType,
                folderPath,
                collectionJson);
    }

    private ShareAclProjectionApplier.RevokedMessage toRevoked(Map<String, Object> payload) {
        return new ShareAclProjectionApplier.RevokedMessage(
                requiredLong(payload, "tenantId"),
                requiredLong(payload, "resourceId"),
                requiredLong(payload, "shareId"),
                requiredString(payload, "targetType"),
                requiredLong(payload, "targetId"),
                requiredLongList(payload, "operationIds"),
                requiredLong(payload, "epoch"));
    }

    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private String optionalCollectionJson(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return text.isBlank() ? null : text;
        }
        if (raw instanceof List<?> list) {
            try {
                return objectMapper.writeValueAsString(list);
            } catch (Exception e) {
                throw new IllegalArgumentException("collectionResourceIds could not be serialized");
            }
        }
        throw new IllegalArgumentException("collectionResourceIds must be an array or string");
    }

    private static long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            long result = number.longValue();
            if (result > 0 && number.doubleValue() == result) {
                return result;
            }
        }
        if (value instanceof String text) {
            try {
                long result = Long.parseLong(text.trim());
                if (result > 0) {
                    return result;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException(key + " must be a positive integer");
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-empty string");
        }
        return text;
    }

    private static Instant requiredInstant(Map<String, Object> payload, String key) {
        try {
            return Instant.parse(requiredString(payload, key));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(key + " must be an ISO-8601 instant");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ShareAclProjectionApplier.FieldGrantMessage> parseOptionalFieldGrants(
            Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("fieldGrants must be an array");
        }
        List<ShareAclProjectionApplier.FieldGrantMessage> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("fieldGrants entries must be objects");
            }
            Map<String, Object> map = (Map<String, Object>) entry;
            result.add(new ShareAclProjectionApplier.FieldGrantMessage(
                    requiredLong(map, "operationId"),
                    requiredLong(map, "fieldId"),
                    requiredBoolean(map, "readable"),
                    requiredBoolean(map, "writable"),
                    requiredBoolean(map, "hidden"),
                    optionalNullableLong(map, "maskStrategyId")));
        }
        return List.copyOf(result);
    }

    private static boolean requiredBoolean(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return bool;
    }

    private static Long optionalNullableLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long result = number.longValue();
            if (result > 0 && number.doubleValue() == result) {
                return result;
            }
        }
        throw new IllegalArgumentException(key + " must be a positive integer or null");
    }

    private static List<Long> requiredLongList(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(key + " must be a non-empty array");
        }
        List<Long> result = new ArrayList<>();
        for (Object element : list) {
            long operationId;
            if (element instanceof Number number && number.doubleValue() == number.longValue()) {
                operationId = number.longValue();
            } else if (element instanceof String text) {
                try {
                    operationId = Long.parseLong(text.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(key + " contains non-integer values");
                }
            } else {
                throw new IllegalArgumentException(key + " contains non-integer values");
            }
            if (operationId <= 0) {
                throw new IllegalArgumentException(key + " must contain positive integers");
            }
            if (!result.contains(operationId)) {
                result.add(operationId);
            }
        }
        return List.copyOf(result);
    }
}
