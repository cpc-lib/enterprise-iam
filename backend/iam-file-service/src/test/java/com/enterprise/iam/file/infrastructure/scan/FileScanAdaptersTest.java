package com.enterprise.iam.file.infrastructure.scan;

import com.enterprise.iam.file.application.model.FileScanTarget;
import com.enterprise.iam.file.application.port.out.FileScanPort;
import com.enterprise.iam.file.domain.model.ScanVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileScanAdaptersTest {

    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    void noopAdapterFailsStartupWhenScanRequired() {
        assertThatThrownBy(() -> FileScanAdapters.create("noop", true, List.of("exe")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("noop");
    }

    @Test
    void noopAdapterAllowedWhenScanNotRequiredAndReturnsClean() {
        FileScanPort port = FileScanAdapters.create("noop", false, List.of("exe"));
        ScanVerdict verdict = port.scan(
                new FileScanTarget(1L, 7L, "anything.exe", "application/x-msdownload", 10L, SHA));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.CLEAN);
    }

    @Test
    void localAdapterEnforcesPolicyEvenWhenScanRequired() {
        FileScanPort port = FileScanAdapters.create("local", true, List.of("exe"));
        ScanVerdict verdict = port.scan(
                new FileScanTarget(1L, 7L, "payload.exe", "application/x-msdownload", 10L, SHA));
        assertThat(verdict.result()).isEqualTo(ScanVerdict.Result.INFECTED);
    }

    @Test
    void unknownAdapterRejected() {
        assertThatThrownBy(() -> FileScanAdapters.create("clamav", true, List.of("exe")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported");
    }
}
