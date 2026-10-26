package com.enterprise.iam.authorization.infrastructure.mq;

import com.enterprise.iam.authorization.infrastructure.mq.ShareAclProjectionApplier.GrantedMessage;
import com.enterprise.iam.authorization.infrastructure.mq.ShareAclProjectionApplier.RevokedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShareAclProjectionListenerTest {

    private ShareAclProjectionApplier applier;
    private ShareAclProjectionListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        applier = mock(ShareAclProjectionApplier.class);
        listener = new ShareAclProjectionListener(applier, objectMapper);
    }

    @Test
    void delegatesGrantedEventToApplier() {
        String body = """
                {
                  "eventType": "share.acl.granted",
                  "shareId": 100, "tenantId": 7, "resourceId": 9,
                  "resourceInstanceKey": "invoice-42",
                  "targetType": "USER", "targetId": 11,
                  "operationIds": [21, 22], "epoch": 1,
                  "startTime": "2026-09-24T09:29:00Z",
                  "expireTime": "2026-09-24T10:29:00Z",
                  "canReshare": false
                }
                """;

        listener.onMessage(body, "share.acl.granted");

        verify(applier).applyGranted(new GrantedMessage(
                7L, 9L, 100L, "invoice-42", "USER", 11L,
                java.util.List.of(21L, 22L),
                java.time.Instant.parse("2026-09-24T09:29:00Z"),
                java.time.Instant.parse("2026-09-24T10:29:00Z"),
                1L,
                java.util.List.of()));
    }

    @Test
    void delegatesRevokedEventToApplier() {
        String body = """
                {
                  "shareId": 100, "tenantId": 7, "resourceId": 9,
                  "targetType": "TEAM", "targetId": 55,
                  "operationIds": [30], "epoch": 4
                }
                """;

        listener.onMessage(body, "share.acl.revoked");

        verify(applier).applyRevoked(new RevokedMessage(
                7L, 9L, 100L, "TEAM", 55L, java.util.List.of(30L), 4L));
    }

    @Test
    void parsesFieldGrantsWhenPresent() {
        String body = """
                {
                  "shareId": 100, "tenantId": 7, "resourceId": 9,
                  "resourceInstanceKey": "invoice-42",
                  "targetType": "USER", "targetId": 11,
                  "operationIds": [21], "epoch": 1,
                  "startTime": "2026-09-24T09:29:00Z",
                  "expireTime": "2026-09-24T10:29:00Z",
                  "canReshare": false,
                  "fieldGrants": [
                    {"operationId": 21, "fieldId": 301, "readable": true, "writable": false, "hidden": false, "maskStrategyId": 800},
                    {"operationId": 21, "fieldId": 302, "readable": false, "writable": false, "hidden": true, "maskStrategyId": null}
                  ]
                }
                """;

        listener.onMessage(body, "share.acl.granted");

        verify(applier).applyGranted(new GrantedMessage(
                7L, 9L, 100L, "invoice-42", "USER", 11L,
                java.util.List.of(21L),
                java.time.Instant.parse("2026-09-24T09:29:00Z"),
                java.time.Instant.parse("2026-09-24T10:29:00Z"),
                1L,
                java.util.List.of(
                        new ShareAclProjectionApplier.FieldGrantMessage(
                                21L, 301L, true, false, false, 800L),
                        new ShareAclProjectionApplier.FieldGrantMessage(
                                21L, 302L, false, false, true, null))));
    }

    @Test
    void rejectsUnparseableBodyWithoutRequeue() {
        assertThatThrownBy(() -> listener.onMessage("{not-json", "share.acl.granted"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(applier);
    }

    @Test
    void rejectsInvalidPayloadWithoutRequeue() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "shareId", 100, "tenantId", 7, "resourceId", 9,
                "targetType", "ROLE", "targetId", 11,
                "operationIds", java.util.List.of(), "epoch", 1));

        assertThatThrownBy(() -> listener.onMessage(body, "share.acl.granted"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(applier, org.mockito.Mockito.never()).applyGranted(any());
    }

    @Test
    void rejectsUnknownRoutingKeyWithoutRequeue() {
        assertThatThrownBy(() -> listener.onMessage("{}", "share.acl.unknown"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(applier);
    }
}
