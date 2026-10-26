package com.enterprise.iam.sharing.infrastructure.outbox;

import com.enterprise.iam.outbox.OutboxEventToAppend;
import com.enterprise.iam.outbox.OutboxWriter;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareAclStatus;
import com.enterprise.iam.sharing.domain.model.ShareStatus;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcShareAclEventAppenderTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:30:00Z");

    private CapturingOutboxWriter writer;
    private JdbcShareAclEventAppender appender;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        writer = new CapturingOutboxWriter();
        appender = new JdbcShareAclEventAppender(
                writer,
                new ShareAclEventCodec(objectMapper),
                new SequenceIdGenerator(8000L));
    }

    @Test
    void appendsGrantedEventV1WithInitialEpoch() throws Exception {
        Share share = share(100L, 0L);
        List<ShareAcl> acls = List.of(
                new ShareAcl(8001L, 7L, 100L, 21L, ShareAclStatus.ACTIVE, 0, NOW.minusSeconds(60),
                        List.of(new ShareAcl.ShareFieldPermission(301L, true, false, false, 800L))),
                new ShareAcl(8002L, 7L, 100L, 22L, ShareAclStatus.ACTIVE, 0, NOW.minusSeconds(60),
                        List.of()));
        appender.appendGranted(share, acls, 1L, NOW);

        assertThat(writer.appended).hasSize(1);
        OutboxEventToAppend event = writer.appended.get(0);
        assertThat(event.eventType()).isEqualTo("share.acl.granted");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.aggregateType()).isEqualTo("RESOURCE_SHARE");
        assertThat(event.aggregateId()).isEqualTo(100L);
        assertThat(event.aggregateVersion()).isEqualTo(1L);
        assertThat(event.tenantId()).isEqualTo(7L);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.id()).isNotEqualTo(event.eventId());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(event.payload(), Map.class);
        assertThat(payload)
                .containsEntry("shareId", 100)
                .containsEntry("tenantId", 7)
                .containsEntry("resourceId", 9)
                .containsEntry("resourceInstanceKey", "invoice-42")
                .containsEntry("targetType", "USER")
                .containsEntry("targetId", 11)
                .containsEntry("epoch", 1)
                .containsEntry("canReshare", false);
        assertThat((List<Object>) payload.get("operationIds")).containsExactly(21, 22);
        assertThat((String) payload.get("startTime")).contains("2026-09-24T09:29:00Z");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldGrants = (List<Map<String, Object>>) payload.get("fieldGrants");
        assertThat(fieldGrants).hasSize(1);
        assertThat(fieldGrants.get(0))
                .containsEntry("operationId", 21)
                .containsEntry("fieldId", 301)
                .containsEntry("readable", true)
                .containsEntry("writable", false)
                .containsEntry("hidden", false)
                .containsEntry("maskStrategyId", 800);
    }

    @Test
    void appendsGrantedEventWithAdvancingEpochAfterRevoke() throws Exception {
        // Re-grant on a resource whose fence was already advanced to 2 by a
        // prior revoke: the granted event must carry the next monotonic epoch
        // both in the outbox aggregate version and in the payload.
        Share share = share(100L, 0L);
        List<ShareAcl> acls = List.of(
                new ShareAcl(8001L, 7L, 100L, 21L, ShareAclStatus.ACTIVE, 0, NOW.minusSeconds(60),
                        List.of()));

        appender.appendGranted(share, acls, 3L, NOW);

        OutboxEventToAppend event = writer.appended.get(0);
        assertThat(event.aggregateVersion()).isEqualTo(3L);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(event.payload(), Map.class);
        assertThat(payload).containsEntry("epoch", 3);
    }

    @Test
    void appendsRevokedEventWithFenceEpoch() throws Exception {
        appender.appendRevoked(share(100L, 4L), List.of(21L), 3L, NOW);

        OutboxEventToAppend event = writer.appended.get(0);
        assertThat(event.eventType()).isEqualTo("share.acl.revoked");
        assertThat(event.aggregateVersion()).isEqualTo(3L);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(event.payload(), Map.class);
        assertThat(payload)
                .containsEntry("shareId", 100)
                .containsEntry("tenantId", 7)
                .containsEntry("resourceId", 9)
                .containsEntry("targetType", "TEAM")
                .containsEntry("targetId", 11)
                .containsEntry("epoch", 3);
        assertThat(payload).doesNotContainKey("startTime");
        @SuppressWarnings("unchecked")
        List<Object> operations = (List<Object>) payload.get("operationIds");
        assertThat(operations).containsExactly(21);
    }

    private static Share share(long id, long version) {
        return new Share(
                id, 7L, 9L, "invoice-42",
                id == 100L && version == 0L ? ShareTargetType.USER : ShareTargetType.TEAM,
                11L, 13L, null, id, 0, ShareStatus.ACTIVE,
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                false, 1, null, null, null, version, NOW, NOW);
    }

    static final class CapturingOutboxWriter implements OutboxWriter {
        final List<OutboxEventToAppend> appended = new ArrayList<>();

        @Override
        public void append(OutboxEventToAppend event) {
            appended.add(event);
        }
    }

    static final class SequenceIdGenerator implements PositiveIdGenerator {
        private long next;

        SequenceIdGenerator(long start) {
            this.next = start;
        }

        @Override
        public long nextId() {
            return next++;
        }
    }
}
