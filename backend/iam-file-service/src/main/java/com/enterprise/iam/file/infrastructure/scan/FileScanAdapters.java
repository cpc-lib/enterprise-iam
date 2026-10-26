package com.enterprise.iam.file.infrastructure.scan;

import com.enterprise.iam.file.application.port.out.FileScanPort;

import java.util.List;
import java.util.Objects;

/**
 * Selects the {@link FileScanPort} implementation for {@code iam.file.scan.adapter}
 * ({@code noop|local}, default {@code local}) and enforces SPEC 35 §10: a Noop scanner
 * must never be wired while scanning is required — bean startup fails fast.
 */
public final class FileScanAdapters {

    public static final String ADAPTER_NOOP = "noop";
    public static final String ADAPTER_LOCAL = "local";

    private FileScanAdapters() {
    }

    public static FileScanPort create(String adapter, boolean scanRequired, List<String> blockedExtensions) {
        String selected = Objects.requireNonNullElse(adapter, ADAPTER_LOCAL).strip().toLowerCase();
        return switch (selected) {
            case ADAPTER_NOOP -> {
                if (scanRequired) {
                    throw new IllegalStateException(
                            "iam.file.scan.adapter=noop is forbidden when iam.file.scan.scan-required=true "
                                    + "(SPEC 35 §10: Noop scanner must not serve a scan-required deployment)");
                }
                yield new NoopFileScanAdapter();
            }
            case ADAPTER_LOCAL -> new LocalDeterministicScanAdapter(blockedExtensions);
            default -> throw new IllegalStateException(
                    "unsupported iam.file.scan.adapter: " + adapter + " (supported: noop, local)");
        };
    }
}
