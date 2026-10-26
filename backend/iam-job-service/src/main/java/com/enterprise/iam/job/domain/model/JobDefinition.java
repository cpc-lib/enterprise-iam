package com.enterprise.iam.job.domain.model;

import java.time.Instant;

public class JobDefinition {
    private final long id;
    private final String jobCode;
    private final String jobName;
    private final String description;
    private final String cronExpression;
    private JobStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public JobDefinition(
            long id,
            String jobCode,
            String jobName,
            String description,
            String cronExpression,
            JobStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.jobCode = jobCode;
        this.jobName = jobName;
        this.description = description;
        this.cronExpression = cronExpression;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getJobCode() {
        return jobCode;
    }

    public String getJobName() {
        return jobName;
    }

    public String getDescription() {
        return description;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void pause(Instant now) {
        this.status = JobStatus.PAUSED;
        this.updatedAt = now;
    }

    public void resume(Instant now) {
        this.status = JobStatus.ACTIVE;
        this.updatedAt = now;
    }
}
