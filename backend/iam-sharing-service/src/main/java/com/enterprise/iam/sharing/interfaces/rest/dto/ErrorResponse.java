package com.enterprise.iam.sharing.interfaces.rest.dto;

/** Frozen OpenAPI {@code ErrorResponse} envelope. */
public record ErrorResponse(boolean success, String code, String message, String traceId) {

    public static ErrorResponse error(String code, String message, String traceId) {
        return new ErrorResponse(false, code, message, traceId);
    }
}
