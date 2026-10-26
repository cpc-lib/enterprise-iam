package com.enterprise.iam.sharing.interfaces.rest.dto;

public record ApiResponse<T>(boolean success, String code, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, "OK", data, traceId);
    }

    public static <T> ApiResponse<T> accepted(T data, String traceId) {
        return new ApiResponse<>(true, "ACCEPTED", data, traceId);
    }

    public static <T> ApiResponse<T> created(T data, String traceId) {
        return new ApiResponse<>(true, "CREATED", data, traceId);
    }
}
