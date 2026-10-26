package com.enterprise.iam.file.interfaces.rest.dto;

import com.enterprise.iam.common.web.json.NumericLong;

/**
 * Frozen OpenAPI {@code EntityResponse}. {@code id} is an identifier serialized
 * as a decimal string; {@code version} is a numeric quantity.
 */
public record EntityResponse(
        long id,
        @NumericLong long version,
        String status) {
}
