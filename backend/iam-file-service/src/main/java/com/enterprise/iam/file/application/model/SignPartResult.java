package com.enterprise.iam.file.application.model;

import java.time.Instant;
import java.util.Map;

public record SignPartResult(
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders) {
}
