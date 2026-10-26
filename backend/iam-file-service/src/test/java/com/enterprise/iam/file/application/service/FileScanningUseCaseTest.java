package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.model.ClaimedScan;
import com.enterprise.iam.file.application.model.FileScanTarget;
import com.enterprise.iam.file.application.model.RetryClaimResult;
import com.enterprise.iam.file.application.port.out.FileScanPort;
import com.enterprise.iam.file.application.port.out.FileScanRepository;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.ScanPolicy;
import com.enterprise.iam.file.domain.model.ScanVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FileScanningUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private StubScanRepository repository;
    private StubScanner scanner;
    private Clock clock;
    private FileScanningUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new StubScanRepository();
        scanner = new StubScanner();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new FileScanningUseCase(
                repository,
                scanner,
                new ScanPolicy(true, true, 3),
                new ScanRetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10)),
                "local",
                clock);
    }

    @Test
    void cleanVerdictMakesFileAvailable() {
        repository.pending.add(claimed(101L, 1, 1L));
        scanner.verdict = ScanVerdict.clean("local-deterministic-1");

        int processed = useCase.processPending(20);

        assertThat(processed).isOne();
        assertThat(repository.outcomes).hasSize(1);
        assertThat(repository.outcomes.get(0).verdict().result()).isEqualTo(ScanVerdict.Result.CLEAN);
        assertThat(repository.terminals).hasSize(1);
        assertThat(repository.terminals.get(0).status()).isEqualTo(FileState.AVAILABLE);
        assertThat(repository.terminals.get(0).scanStatus()).isEqualTo(FileScanStatus.CLEAN);
    }

    @Test
    void infectedVerdictQuarantinesFile() {
        repository.pending.add(claimed(102L, 1, 1L));
        scanner.verdict = ScanVerdict.infected("EICAR", "sig-1");

        useCase.processPending(20);

        assertThat(repository.terminals).hasSize(1);
        assertThat(repository.terminals.get(0).status()).isEqualTo(FileState.QUARANTINED);
        assertThat(repository.terminals.get(0).scanStatus()).isEqualTo(FileScanStatus.INFECTED);
    }

    @Test
    void failedVerdictStaysScanningForRetry() {
        repository.pending.add(claimed(103L, 1, 1L));
        scanner.verdict = ScanVerdict.failed("CLAMD_UNAVAILABLE");

        useCase.processPending(20);

        assertThat(repository.terminals).isEmpty();
        assertThat(repository.outcomes.get(0).verdict().result()).isEqualTo(ScanVerdict.Result.FAILED);
    }

    @Test
    void scannerExceptionBecomesFailedOutcome() {
        repository.pending.add(claimed(104L, 1, 1L));
        scanner.throwOnScan = true;

        useCase.processPending(20);

        assertThat(repository.terminals).isEmpty();
        assertThat(repository.outcomes).hasSize(1);
        assertThat(repository.outcomes.get(0).verdict().result()).isEqualTo(ScanVerdict.Result.FAILED);
        assertThat(repository.outcomes.get(0).verdict().errorCode())
                .isEqualTo(FileScanningUseCase.ERROR_SCANNER_FAILURE);
    }

    @Test
    void skippedVerdictIsDeniedWhenScanRequired() {
        repository.pending.add(claimed(105L, 1, 1L));
        scanner.verdict = ScanVerdict.skipped();

        useCase.processPending(20);

        assertThat(repository.terminals).isEmpty();
        assertThat(repository.outcomes.get(0).verdict().result()).isEqualTo(ScanVerdict.Result.FAILED);
        assertThat(repository.outcomes.get(0).verdict().errorCode())
                .isEqualTo(FileScanningUseCase.ERROR_SKIP_DENIED);
    }

    @Test
    void skippedVerdictAllowedWhenScanNotRequired() {
        var relaxed = new FileScanningUseCase(
                repository, scanner, new ScanPolicy(false, false, 3),
                new ScanRetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10)),
                "noop", clock);
        repository.pending.add(claimed(106L, 1, 1L));
        scanner.verdict = ScanVerdict.skipped();

        relaxed.processPending(20);

        assertThat(repository.terminals).hasSize(1);
        assertThat(repository.terminals.get(0).status()).isEqualTo(FileState.AVAILABLE);
        assertThat(repository.terminals.get(0).scanStatus()).isEqualTo(FileScanStatus.NOT_REQUIRED);
    }

    @Test
    void terminalFilesAreNeverScannedAgain() {
        // Nothing is claimable: AVAILABLE/QUARANTINED files are excluded by the claim condition.
        scanner.verdict = ScanVerdict.clean("sig");

        int firstTick = useCase.processPending(20);
        int secondTick = useCase.processPending(20);

        assertThat(firstTick).isZero();
        assertThat(secondTick).isZero();
        assertThat(scanner.targets).isEmpty();
        assertThat(repository.starts).isEmpty();
        assertThat(repository.terminals).isEmpty();
    }

    @Test
    void losingTerminalTransitionDoesNotThrow() {
        repository.pending.add(claimed(107L, 1, 1L));
        repository.transitionRows = 0;
        scanner.verdict = ScanVerdict.clean("sig");

        assertThatCode(() -> useCase.processPending(20)).doesNotThrowAnyException();
        assertThat(repository.outcomes).hasSize(1);
    }

    @Test
    void retryBackoffDueFileIsClaimedAndScanned() {
        repository.retryFiles.add(new RetryFile(108L, 1, NOW.minusSeconds(60)));
        scanner.verdict = ScanVerdict.clean("sig");

        RetryClaimResult result = useCase.processRetries(20);

        assertThat(result.claimed()).hasSize(1);
        assertThat(result.claimed().get(0).attempt()).isEqualTo(2);
        assertThat(result.exhaustedCount()).isZero();
        assertThat(scanner.targets).hasSize(1);
        assertThat(repository.starts.get(0).attempt()).isEqualTo(2);
    }

    @Test
    void retryNotYetDueStaysUnclaimed() {
        repository.retryFiles.add(new RetryFile(109L, 1, NOW.minusSeconds(10)));

        RetryClaimResult result = useCase.processRetries(20);

        assertThat(result.claimed()).isEmpty();
        assertThat(scanner.targets).isEmpty();
    }

    @Test
    void exhaustedAttemptsAreSurfacedButNeverClaimed() {
        repository.retryFiles.add(new RetryFile(110L, 3, NOW.minusSeconds(3600)));

        RetryClaimResult result = useCase.processRetries(20);

        assertThat(result.claimed()).isEmpty();
        assertThat(result.exhaustedCount()).isOne();
        assertThat(scanner.targets).isEmpty();
    }

    private static ClaimedScan claimed(long fileId, int attempt, long version) {
        return new ClaimedScan(7L, fileId, attempt, "doc.txt", "text/plain", 1024L, SHA, version);
    }

    private static final class StubScanner implements FileScanPort {

        private ScanVerdict verdict = ScanVerdict.clean("sig");
        private boolean throwOnScan;
        private final List<FileScanTarget> targets = new ArrayList<>();

        @Override
        public ScanVerdict scan(FileScanTarget target) {
            targets.add(target);
            if (throwOnScan) {
                throw new IllegalStateException("scanner down");
            }
            return verdict;
        }
    }

    private record RetryFile(long fileId, int lastAttempt, Instant lastCompletedAt) {
    }

    private record ScanStart(long fileId, int attempt) {
    }

    private record Outcome(long fileId, int attempt, ScanVerdict verdict) {
    }

    private record Terminal(long fileId, FileState status, FileScanStatus scanStatus, long version) {
    }

    private static final class StubScanRepository implements FileScanRepository {

        private final List<ClaimedScan> pending = new ArrayList<>();
        private final List<RetryFile> retryFiles = new ArrayList<>();
        private final List<ScanStart> starts = new ArrayList<>();
        private final List<Outcome> outcomes = new ArrayList<>();
        private final List<Terminal> terminals = new ArrayList<>();
        private int transitionRows = 1;

        @Override
        public List<ClaimedScan> claimPendingBatch(int limit) {
            List<ClaimedScan> claimed = List.copyOf(pending);
            pending.clear();
            return claimed;
        }

        @Override
        public RetryClaimResult claimRetryableFailures(
                Instant now, int maxAttempts, IntFunction<Duration> backoffByAttempt, int limit) {
            int exhausted = 0;
            List<ClaimedScan> claimed = new ArrayList<>();
            for (RetryFile file : retryFiles) {
                if (file.lastAttempt() >= maxAttempts) {
                    exhausted++;
                    continue;
                }
                Duration wait = backoffByAttempt.apply(file.lastAttempt());
                if (file.lastCompletedAt().plus(wait).isAfter(now)) {
                    continue;
                }
                claimed.add(new ClaimedScan(7L, file.fileId(), file.lastAttempt() + 1,
                        "doc.txt", "text/plain", 1024L, SHA, 2L));
            }
            return new RetryClaimResult(claimed, exhausted);
        }

        @Override
        public void recordScanStart(long tenantId, long fileId, int attempt, String scannerName,
                                    Instant startedAt) {
            starts.add(new ScanStart(fileId, attempt));
        }

        @Override
        public void recordScanOutcome(long tenantId, long fileId, int attempt, ScanVerdict verdict,
                                      Instant completedAt) {
            outcomes.add(new Outcome(fileId, attempt, verdict));
        }

        @Override
        public int transitionTerminal(long tenantId, long fileId, FileState status,
                                      FileScanStatus scanStatus, long expectedVersion) {
            if (transitionRows == 1) {
                terminals.add(new Terminal(fileId, status, scanStatus, expectedVersion));
            }
            return transitionRows;
        }
    }
}
