package com.enterprise.iam.file.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;

public record SignPartResponse(
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders) {
}
