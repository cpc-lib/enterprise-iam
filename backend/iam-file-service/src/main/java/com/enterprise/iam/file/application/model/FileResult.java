package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;

public record FileResult(
        long fileId,
        FileState status,
        FileScanStatus scanStatus,
        long version) {
}
