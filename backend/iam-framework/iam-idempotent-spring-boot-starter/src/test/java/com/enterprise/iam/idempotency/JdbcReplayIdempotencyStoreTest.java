package com.enterprise.iam.idempotency;

import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper;
import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper.RecordRow;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcReplayIdempotencyStoreTest {

    @Test
    void retriesWhenAnotherTransactionWinsTheFirstInsert() {
        IdempotencyPersistenceMapper mapper = mock(IdempotencyPersistenceMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenAnswer(ignored -> new SimpleTransactionStatus());

        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        when(mapper.lockRecord(1L, "key-a"))
                .thenReturn(List.of())
                .thenReturn(List.of(new RecordRow(
                        "hash-a", "IN_FLIGHT", null, null, null, now.plusSeconds(60))));
        when(mapper.insertClaim(1L, "key-a", "hash-a", "IN_FLIGHT", now.plusSeconds(3600)))
                .thenThrow(new DuplicateKeyException("concurrent winner"));

        JdbcReplayIdempotencyStore store = new JdbcReplayIdempotencyStore(
                mapper, transactionManager, Duration.ofHours(1));

        assertThat(store.claim(1L, "key-a", "hash-a", now))
                .isInstanceOf(ReplayIdempotencyStore.ClaimOutcome.InFlight.class);
        verify(mapper, times(2)).lockRecord(1L, "key-a");
    }
}
