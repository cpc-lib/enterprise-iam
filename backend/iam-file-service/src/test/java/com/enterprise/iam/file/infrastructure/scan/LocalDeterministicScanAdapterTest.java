package com.enterprise.iam.file.infrastructure.scan;

import com.enterprise.iam.file.application.model.FileScanTarget;
import com.enterprise.iam.file.domain.model.ScanVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDeterministicScanAdapterTest {

    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private final LocalDeterministicScanAdapter adapter =
            new LocalDeterministicScanAdapter(LocalDeterministicScanAdapter.DEFAULT_BLOCKED_EXTENSIONS);

    @Test
    void blockedExecutableExtensionIsInfected() {
        ScanVerdict verdict = adapter.scan(target("tools/evil.exe"));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.INFECTED);
        assertThat(verdict.threatCode()).isEqualTo(LocalDeterministicScanAdapter.THREAT_BLOCKED_EXTENSION);
    }

    @Test
    void doubleExtensionSpoofIsInfected() {
        ScanVerdict verdict = adapter.scan(target("invoice.exe.pdf"));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.INFECTED);
        assertThat(verdict.threatCode()).isEqualTo(LocalDeterministicScanAdapter.THREAT_BLOCKED_EXTENSION);
    }

    @Test
    void normalDocumentIsClean() {
        ScanVerdict verdict = adapter.scan(target("report.txt"));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.CLEAN);
        assertThat(verdict.signatureVersion())
                .isEqualTo(LocalDeterministicScanAdapter.SIGNATURE_VERSION);
    }

    @Test
    void invalidMetadataFailsScan() {
        ScanVerdict verdict = adapter.scan(
                new FileScanTarget(1L, 7L, "empty.bin", "application/octet-stream", 0L, SHA));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.FAILED);
        assertThat(verdict.errorCode()).isEqualTo(LocalDeterministicScanAdapter.ERROR_INVALID_METADATA);
    }

    @Test
    void customBlocklistIsApplied() {
        var custom = new LocalDeterministicScanAdapter(List.of("pdf"));
        ScanVerdict verdict = custom.scan(target("slip.pdf"));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.INFECTED);
    }

    private static FileScanTarget target(String fileName) {
        return new FileScanTarget(1L, 7L, fileName, "application/octet-stream", 1024L, SHA);
    }
}
