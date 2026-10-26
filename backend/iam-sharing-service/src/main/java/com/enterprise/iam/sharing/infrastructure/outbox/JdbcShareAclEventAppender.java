package com.enterprise.iam.sharing.infrastructure.outbox;

import com.enterprise.iam.outbox.OutboxEventToAppend;
import com.enterprise.iam.outbox.OutboxWriter;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclEventAppender;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Appends share ACL events through the transaction-enforcing outbox writer.
 * Never opens a transaction itself; the caller's create/revoke transaction
 * owns atomicity.
 */
public final class JdbcShareAclEventAppender implements ShareAclEventAppender {

    public static final String EVENT_TYPE_GRANTED = "share.acl.granted";
    public static final String EVENT_TYPE_REVOKED = "share.acl.revoked";
    public static final String AGGREGATE_TYPE = "RESOURCE_SHARE";

    private final Supplier<OutboxWriter> writer;
    private final ShareAclEventCodec codec;
    private final PositiveIdGenerator idGenerator;

    public JdbcShareAclEventAppender(
            OutboxWriter writer,
            ShareAclEventCodec codec,
            PositiveIdGenerator idGenerator) {
        this(requireWriter(writer), codec, idGenerator);
    }

    JdbcShareAclEventAppender(
            Supplier<OutboxWriter> writer,
            ShareAclEventCodec codec,
            PositiveIdGenerator idGenerator) {
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public void appendGranted(Share share, List<ShareAcl> acls, long epoch, Instant occurredAt) {
        Objects.requireNonNull(share, "share must not be null");
        Objects.requireNonNull(acls, "acls must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        long id = idGenerator.nextId();
        long eventId = idGenerator.nextId();
        writer.get().append(new OutboxEventToAppend(
                id,
                eventId,
                share.tenantId(),
                AGGREGATE_TYPE,
                share.id(),
                epoch,
                EVENT_TYPE_GRANTED,
                ShareAclEventCodec.SCHEMA_VERSION,
                codec.encodeGranted(share, acls, epoch),
                occurredAt));
    }

    @Override
    public void appendRevoked(Share share, List<Long> operationIds, long epoch, Instant occurredAt) {
        Objects.requireNonNull(share, "share must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        long id = idGenerator.nextId();
        long eventId = idGenerator.nextId();
        writer.get().append(new OutboxEventToAppend(
                id,
                eventId,
                share.tenantId(),
                AGGREGATE_TYPE,
                share.id(),
                epoch,
                EVENT_TYPE_REVOKED,
                ShareAclEventCodec.SCHEMA_VERSION,
                codec.encodeRevoked(share, operationIds, epoch),
                occurredAt));
    }

    private static Supplier<OutboxWriter> requireWriter(OutboxWriter writer) {
        OutboxWriter required = Objects.requireNonNull(writer, "writer must not be null");
        return () -> required;
    }
}
