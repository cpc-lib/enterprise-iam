package com.enterprise.iam.authorization.infrastructure.mq;

import com.enterprise.iam.authorization.infrastructure.mq.ShareAclProjectionApplier.GrantedMessage;
import com.enterprise.iam.authorization.infrastructure.mq.ShareAclProjectionApplier.RevokedMessage;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.ShareAclProjectionMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareFieldGrantWriteRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareProjectionWriteRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShareAclProjectionApplierTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:30:00Z");

    private ShareAclProjectionMapper mapper;
    private ShareAclProjectionApplier applier;

    @BeforeEach
    void setUp() {
        mapper = mock(ShareAclProjectionMapper.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);
        AtomicLong ids = new AtomicLong(5000);
        applier = new ShareAclProjectionApplier(
                mapper,
                transactionTemplate,
                ids::incrementAndGet,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void grantedUpsertsEveryOperationAndConvergesEpochInOneTransaction() {
        applier.applyGranted(new GrantedMessage(
                7L, 9L, 100L, "invoice-42", "USER", 11L,
                List.of(21L, 22L), NOW.minusSeconds(60), NOW.plusSeconds(3600), 1L,
                List.of()));

        ArgumentCaptor<ShareProjectionWriteRow> rows =
                ArgumentCaptor.forClass(ShareProjectionWriteRow.class);
        verify(mapper, times(2)).upsertGranted(rows.capture());
        assertThat(rows.getAllValues()).extracting(ShareProjectionWriteRow::operationId)
                .containsExactly(21L, 22L);
        assertThat(rows.getAllValues()).extracting(ShareProjectionWriteRow::projectionEpoch)
                .containsOnly(1L);
        verify(mapper).advanceExpectedEpoch(7L, 9L, 1L, NOW);
        verify(mapper).advanceAppliedEpoch(7L, 9L, 1L, NOW);
    }

    @Test
    void duplicateGrantedRedeliveryReRunsIdempotentUpsertWithEpochGuard() {
        GrantedMessage message = new GrantedMessage(
                7L, 9L, 100L, "invoice-42", "TEAM", 33L,
                List.of(21L), NOW.minusSeconds(60), NOW.plusSeconds(3600), 1L,
                List.of());

        applier.applyGranted(message);
        applier.applyGranted(message);

        verify(mapper, times(2)).upsertGranted(any(ShareProjectionWriteRow.class));
        verify(mapper, times(2)).advanceExpectedEpoch(7L, 9L, 1L, NOW);
        verify(mapper, times(2)).advanceAppliedEpoch(7L, 9L, 1L, NOW);
    }

    @Test
    void grantedWithFieldGrantsWritesFieldProjectionInSameTransaction() {
        applier.applyGranted(new GrantedMessage(
                7L, 9L, 100L, "invoice-42", "USER", 11L,
                List.of(21L), NOW.minusSeconds(60), NOW.plusSeconds(3600), 1L,
                List.of(new ShareAclProjectionApplier.FieldGrantMessage(
                        21L, 301L, true, false, false, 800L))));

        verify(mapper).deleteFieldGrantsBeforeEpoch(7L, 100L, 1L);
        ArgumentCaptor<ShareFieldGrantWriteRow> fieldRow =
                ArgumentCaptor.forClass(ShareFieldGrantWriteRow.class);
        verify(mapper).upsertFieldGrant(fieldRow.capture());
        assertThat(fieldRow.getValue().operationId()).isEqualTo(21L);
        assertThat(fieldRow.getValue().fieldId()).isEqualTo(301L);
        assertThat(fieldRow.getValue().projectionEpoch()).isEqualTo(1L);
    }

    @Test
    void grantRevokeRegrantSequenceAdvancesFenceMonotonicallyToThree() {
        applier.applyGranted(new GrantedMessage(
                7L, 9L, 100L, "invoice-42", "USER", 11L,
                List.of(21L), NOW.minusSeconds(60), NOW.plusSeconds(3600), 1L,
                List.of()));
        applier.applyRevoked(new RevokedMessage(
                7L, 9L, 100L, "USER", 11L, List.of(21L), 2L));
        applier.applyGranted(new GrantedMessage(
                7L, 9L, 101L, "invoice-42", "USER", 11L,
                List.of(21L), NOW.minusSeconds(30), NOW.plusSeconds(7200), 3L,
                List.of()));

        ArgumentCaptor<ShareProjectionWriteRow> rows =
                ArgumentCaptor.forClass(ShareProjectionWriteRow.class);
        verify(mapper, times(2)).upsertGranted(rows.capture());
        assertThat(rows.getAllValues()).extracting(ShareProjectionWriteRow::projectionEpoch)
                .containsExactly(1L, 3L);
        verify(mapper).advanceExpectedEpoch(7L, 9L, 1L, NOW);
        verify(mapper).advanceExpectedEpoch(7L, 9L, 2L, NOW);
        verify(mapper).advanceExpectedEpoch(7L, 9L, 3L, NOW);
    }

    @Test
    void revokedMarksRowsRevokedAndAdvancesFenceEpoch() {
        applier.applyRevoked(new RevokedMessage(
                7L, 9L, 100L, "USER", 11L, List.of(21L, 22L), 3L));

        verify(mapper).revoke(3L, NOW, 7L, 100L, "USER", 11L, List.of(21L, 22L));
        verify(mapper).deleteFieldGrantsBeforeEpoch(7L, 100L, 3L);
        verify(mapper).advanceExpectedEpoch(7L, 9L, 3L, NOW);
        verify(mapper).advanceAppliedEpoch(7L, 9L, 3L, NOW);
    }

    @Test
    void rejectsInvalidTargetAndEmptyOperationsBeforeAnyPersistenceCall() {
        assertThatThrownBy(() -> applier.applyGranted(new GrantedMessage(
                7L, 9L, 100L, "k", "INVALID", 11L, List.of(21L),
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1L,
                List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applier.applyRevoked(new RevokedMessage(
                7L, 9L, 100L, "USER", 11L, List.of(), 3L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }
}
