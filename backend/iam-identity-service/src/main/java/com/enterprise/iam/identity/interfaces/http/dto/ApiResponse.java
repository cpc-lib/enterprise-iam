package com.enterprise.iam.identity.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data, String traceId) {

    public ApiResponse(boolean success, String code, T data, String traceId) {
        this(success, code, null, data, traceId);
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, "OK", null, data, traceId);
    }

    public static <T> ApiResponse<T> created(T data, String traceId) {
        return new ApiResponse<>(true, "CREATED", null, data, traceId);
    }

    public static <T> ApiResponse<T> accepted(T data, String traceId) {
        return new ApiResponse<>(true, "ACCEPTED", null, data, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String traceId) {
        return new ApiResponse<>(false, code, null, null, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(false, code, message, null, traceId);
    }
}
