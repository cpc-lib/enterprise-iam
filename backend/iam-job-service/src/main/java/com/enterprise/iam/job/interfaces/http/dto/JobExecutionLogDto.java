package com.enterprise.iam.job.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobExecutionLogDto(
        String id,
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
