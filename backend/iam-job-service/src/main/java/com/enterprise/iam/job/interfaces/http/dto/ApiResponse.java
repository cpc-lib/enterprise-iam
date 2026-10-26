package com.enterprise.iam.job.interfaces.http.dto;

public record ApiResponse<T>(boolean success, String code, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, "OK", data, traceId);
    }
}
