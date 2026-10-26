package com.enterprise.iam.auth.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LoginAuditEventCodec {

    private final ObjectMapper objectMapper;

    public LoginAuditEventCodec(ObjectMapper applicationMapper) {
        Objects.requireNonNull(applicationMapper, "applicationMapper must not be null");
        JsonFactory strictFactory = (JsonFactory) applicationMapper.getFactory()
                .rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        ObjectMapper strictMapper = applicationMapper.copyWith(strictFactory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig()
                .without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        strictMapper.setConfig(strictMapper.getSerializationConfig()
                .without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.objectMapper = strictMapper;
    }

    String encode(LoginAuditOutboxPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new LoginAuditEventFormatException("login audit event could not be encoded", exception);
        }
    }

    LoginAuditOutboxPayload decode(String payload) {
        try {
            return objectMapper.readValue(payload, LoginAuditOutboxPayload.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new LoginAuditEventFormatException("login audit event is invalid", exception);
        }
    }

    Map<String, Object> toMap(LoginAuditOutboxPayload payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("tenantId", payload.tenantId());
        event.put("userId", payload.userId());
        event.put("sessionId", payload.sessionId());
        event.put("requestId", payload.requestId());
        event.put("occurredAt", payload.occurredAt());
        if (payload.reason() != null) {
            event.put("reason", payload.reason());
        }
        return Map.copyOf(event);
    }
}
