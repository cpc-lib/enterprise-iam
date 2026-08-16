package com.enterprise.iam.outbox;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository {

    List<OutboxEvent> claimBatch(
            String claimOwner,
            Instant now,
            Instant claimUntil,
            int batchSize);

    void markPublished(long id, String claimOwner, Instant publishedAt);

    void reschedule(
            long id,
            String claimOwner,
            int retryCount,
            Instant nextRetryAt,
            String errorCode);

    void markDead(long id, String claimOwner, int retryCount, String errorCode);
}
