package com.enterprise.iam.job.infrastructure.persistence.mapper;

import java.time.Instant;

public record JobDefinitionRow(
        long id,
        String jobCode,
        String jobName,
        String description,
        String cronExpression,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
