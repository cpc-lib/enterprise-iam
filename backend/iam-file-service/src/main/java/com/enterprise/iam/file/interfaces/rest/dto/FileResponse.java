package com.enterprise.iam.file.interfaces.rest.dto;

import com.enterprise.iam.common.web.json.NumericLong;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;

/**
 * Frozen OpenAPI {@code FileResponse}. {@code fileId} is an identifier
 * (decimal string); {@code version} is a numeric quantity.
 */
public record FileResponse(
        long fileId,
        FileState status,
        FileScanStatus scanStatus,
        @NumericLong long version) {
}
