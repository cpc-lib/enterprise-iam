package com.enterprise.iam.auth.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        @NumericLong long expiresIn,
        long sessionId) {

    public RefreshResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (expiresIn <= 0 || expiresIn > 300) {
            throw new IllegalArgumentException("expiresIn must be between 1 and 300");
        }
    }

    public static RefreshResponse fromSession(String accessToken, long expiresIn, long sessionId) {
        return new RefreshResponse(accessToken, "Bearer", expiresIn, sessionId);
    }
}
