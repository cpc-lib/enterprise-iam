package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/** Strict, versioned JSON codec for the durable event payload. */
public final class SessionProjectionEventCodec {

    private final ObjectMapper mapper;

    public SessionProjectionEventCodec(ObjectMapper applicationMapper) {
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
        mapper = strictMapper;
    }

    public String encode(SessionSecurityProjection projection) {
        try {
            return mapper.writeValueAsString(SessionProjectionOutboxPayload.from(projection));
        } catch (JsonProcessingException exception) {
            throw new SessionProjectionEventFormatException(
                    "session projection event could not be encoded", exception);
        }
    }

    public SessionSecurityProjection decode(String payload) {
        try {
            return mapper.readValue(payload, SessionProjectionOutboxPayload.class).toProjection();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SessionProjectionEventFormatException(
                    "session projection event payload is invalid", exception);
        }
    }
}
