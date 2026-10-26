package com.enterprise.iam.auth.interfaces.http.dto;

public record AuthResponse<T>(boolean success, String code, T data, String traceId) {

    public static <T> AuthResponse<T> ok(T data, String traceId) {
        return new AuthResponse<>(true, "OK", data, traceId);
    }
}
