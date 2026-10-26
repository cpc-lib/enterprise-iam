package com.enterprise.iam.job.interfaces.http.dto;

import java.time.Instant;

public record JobDefinitionDto(
        String id,
        String jobCode,
        String jobName,
        String cronExpression,
        String status,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
