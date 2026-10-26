package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.TenantQuotaRow;
import com.enterprise.iam.file.application.port.out.QuotaReconcileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class QuotaReconcileUseCaseTest {

    private StubQuotaRepository repository;
    private QuotaReconcileUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new StubQuotaRepository();
        useCase = new QuotaReconcileUseCase(repository);
    }

    @Test
    void driftTriggersOptimisticCorrection() {
        repository.rows.add(new TenantQuotaRow(7L, 10_000L, 100L, 0L, 4L));
        repository.logicalUsage = 300L;
        repository.reservedBytes = 20L;

        int adjusted = useCase.reconcileOnce();

        assertThat(adjusted).isOne();
        assertThat(repository.adjustments).hasSize(1);
        assertThat(repository.adjustments.get(0).usedLogicalBytes()).isEqualTo(300L);
        assertThat(repository.adjustments.get(0).reservedBytes()).isEqualTo(20L);
        assertThat(repository.adjustments.get(0).expectedVersion()).isEqualTo(4L);
    }

    @Test
    void noDriftWritesNothing() {
        repository.rows.add(new TenantQuotaRow(7L, 10_000L, 300L, 20L, 4L));
        repository.logicalUsage = 300L;
        repository.reservedBytes = 20L;

        int adjusted = useCase.reconcileOnce();

        assertThat(adjusted).isZero();
        assertThat(repository.adjustments).isEmpty();
    }

    @Test
    void optimisticVersionConflictIsTolerated() {
        repository.rows.add(new TenantQuotaRow(7L, 10_000L, 100L, 0L, 4L));
        repository.logicalUsage = 300L;
        repository.adjustRows = 0;

        assertThatCode(() -> assertThat(useCase.reconcileOnce()).isZero())
                .doesNotThrowAnyException();
    }

    @Test
    void driftBeyondQuotaIsNotForceWritten() {
        repository.rows.add(new TenantQuotaRow(7L, 200L, 100L, 0L, 4L));
        repository.logicalUsage = 250L;

        int adjusted = useCase.reconcileOnce();

        assertThat(adjusted).isZero();
        assertThat(repository.adjustments).isEmpty();
    }

    private record Adjustment(long tenantId, long usedLogicalBytes, long reservedBytes, long expectedVersion) {
    }

    private static final class StubQuotaRepository implements QuotaReconcileRepository {

        private final List<TenantQuotaRow> rows = new ArrayList<>();
        private final List<Adjustment> adjustments = new ArrayList<>();
        private long logicalUsage;
        private long reservedBytes;
        private int adjustRows = 1;

        @Override
        public List<TenantQuotaRow> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public long sumLogicalUsage(long tenantId) {
            return logicalUsage;
        }

        @Override
        public long sumReservedBytes(long tenantId) {
            return reservedBytes;
        }

        @Override
        public int adjustQuota(long tenantId, long usedLogicalBytes, long reservedBytes,
                               long expectedVersion) {
            adjustments.add(new Adjustment(tenantId, usedLogicalBytes, reservedBytes, expectedVersion));
            return adjustRows;
        }
    }
}
