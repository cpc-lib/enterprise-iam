package com.enterprise.iam.auth.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

public record LoginResponse(
        String accessToken,
        String tokenType,
        @NumericLong long expiresIn,
        long sessionId,
        long userId,
        long tenantId) {

    public LoginResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (expiresIn <= 0 || expiresIn > 300) {
            throw new IllegalArgumentException("expiresIn must be between 1 and 300");
        }
    }

    public static LoginResponse fromSession(
            String accessToken, long expiresIn, long sessionId, long userId, long tenantId) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, sessionId, userId, tenantId);
    }
}
