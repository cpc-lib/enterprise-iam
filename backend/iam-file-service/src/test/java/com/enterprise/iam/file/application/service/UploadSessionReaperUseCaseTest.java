package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.ExpiredSession;
import com.enterprise.iam.file.application.port.out.UploadSessionReaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UploadSessionReaperUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private StubReaperRepository repository;
    private UploadSessionReaperUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new StubReaperRepository();
        useCase = new UploadSessionReaperUseCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void expiresSessionAndReleasesReservation() {
        repository.expired.add(new ExpiredSession(7L, 100L, 500L, 0L));
        repository.reservedByTenant.put(7L, 800L);

        int reaped = useCase.reapOnce(50);

        assertThat(reaped).isOne();
        assertThat(repository.markedExpired).containsExactly(100L);
        assertThat(repository.reservedByTenant.get(7L)).isEqualTo(300L);
    }

    @Test
    void reservationReleaseNeverGoesNegative() {
        repository.expired.add(new ExpiredSession(7L, 101L, 500L, 0L));
        repository.reservedByTenant.put(7L, 100L);

        useCase.reapOnce(50);

        assertThat(repository.reservedByTenant.get(7L)).isZero();
    }

    @Test
    void versionConflictSkipsReservationRelease() {
        repository.expired.add(new ExpiredSession(7L, 102L, 500L, 3L));
        repository.markRows = 0;
        repository.reservedByTenant.put(7L, 800L);

        int reaped = useCase.reapOnce(50);

        assertThat(reaped).isZero();
        assertThat(repository.reservedByTenant.get(7L)).isEqualTo(800L);
    }

    private static final class StubReaperRepository implements UploadSessionReaperRepository {

        private final List<ExpiredSession> expired = new ArrayList<>();
        private final List<Long> markedExpired = new ArrayList<>();
        private final Map<Long, Long> reservedByTenant = new HashMap<>();
        private int markRows = 1;

        @Override
        public List<ExpiredSession> findExpiredActiveSessions(Instant now, int limit) {
            return List.copyOf(expired);
        }

        @Override
        public int markExpired(long tenantId, long sessionId, long expectedVersion) {
            if (markRows == 1) {
                markedExpired.add(sessionId);
            }
            return markRows;
        }

        @Override
        public int releaseReservation(long tenantId, long bytes) {
            long current = reservedByTenant.getOrDefault(tenantId, 0L);
            // Mirrors the SQL CASE clamp in JdbcUploadSessionReaperRepository.
            reservedByTenant.put(tenantId, current >= bytes ? current - bytes : 0L);
            return 1;
        }
    }
}
