package com.enterprise.iam.job.domain.model;

import java.time.Instant;

public class JobExecutionLog {
    private final long id;
    private final String jobCode;
    private final String executionId;
    private JobExecutionStatus status;
    private final Instant startTime;
    private Instant endTime;
    private int itemsProcessed;
    private int itemsConverged;
    private String errorMessage;
    private final Instant createdAt;

    public JobExecutionLog(
            long id,
            String jobCode,
            String executionId,
            JobExecutionStatus status,
            Instant startTime,
            Instant endTime,
            int itemsProcessed,
            int itemsConverged,
            String errorMessage,
            Instant createdAt) {
        this.id = id;
        this.jobCode = jobCode;
        this.executionId = executionId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.itemsProcessed = itemsProcessed;
        this.itemsConverged = itemsConverged;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getJobCode() {
        return jobCode;
    }

    public String getExecutionId() {
        return executionId;
    }

    public JobExecutionStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public int getItemsProcessed() {
        return itemsProcessed;
    }

    public int getItemsConverged() {
        return itemsConverged;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void complete(int itemsProcessed, int itemsConverged, Instant endTime) {
        this.status = JobExecutionStatus.SUCCESS;
        this.itemsProcessed = itemsProcessed;
        this.itemsConverged = itemsConverged;
        this.endTime = endTime;
    }

    public void fail(String errorMessage, Instant endTime) {
        this.status = JobExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.endTime = endTime;
    }
}
