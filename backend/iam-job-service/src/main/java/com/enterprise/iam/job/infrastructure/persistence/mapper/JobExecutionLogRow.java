package com.enterprise.iam.job.infrastructure.persistence.mapper;

import java.time.Instant;

public record JobExecutionLogRow(
        long id,
        String jobCode,
        String executionId,
        String status,
        Instant startTime,
        Instant endTime,
        int itemsProcessed,
        int itemsConverged,
        String errorMessage,
        Instant createdAt) {
}
