package com.enterprise.iam.file.infrastructure.scan;

import com.enterprise.iam.file.application.model.FileScanTarget;
import com.enterprise.iam.file.application.port.out.FileScanPort;
import com.enterprise.iam.file.domain.model.ScanVerdict;

/**
 * DEV ONLY scanner adapter — always returns CLEAN without inspecting anything
 * (SPEC 35 §10 NoopScanner). Application startup fails fast via {@link FileScanAdapters}
 * when this adapter is selected while scan-required=true.
 */
public final class NoopFileScanAdapter implements FileScanPort {

    public static final String SCANNER_NAME = "noop";
    static final String SIGNATURE_VERSION = "noop-1";

    @Override
    public ScanVerdict scan(FileScanTarget target) {
        return ScanVerdict.clean(SIGNATURE_VERSION);
    }
}
