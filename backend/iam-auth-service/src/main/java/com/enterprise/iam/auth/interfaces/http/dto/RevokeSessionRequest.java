package com.enterprise.iam.auth.interfaces.http.dto;

public record RevokeSessionRequest(
        String reason
) {}
