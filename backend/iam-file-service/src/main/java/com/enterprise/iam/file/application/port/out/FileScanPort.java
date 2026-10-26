package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.application.model.FileScanTarget;
import com.enterprise.iam.file.domain.model.ScanVerdict;

/**
 * Scanner abstraction (SPEC 35 §10). Application layer owns the port; concrete
 * infrastructure adapters (cloud scanner/local deterministic/noop-dev) implement it.
 * ClamAV is deliberately excluded by project decision.
 */
public interface FileScanPort {

    ScanVerdict scan(FileScanTarget target);
}
