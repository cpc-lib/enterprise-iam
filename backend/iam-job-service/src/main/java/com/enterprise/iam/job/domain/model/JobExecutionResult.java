package com.enterprise.iam.job.domain.model;

public record JobExecutionResult(int itemsProcessed, int itemsConverged, String errorMessage) {
    public static JobExecutionResult success(int itemsProcessed, int itemsConverged) {
        return new JobExecutionResult(itemsProcessed, itemsConverged, null);
    }

    public static JobExecutionResult failure(String errorMessage) {
        return new JobExecutionResult(0, 0, errorMessage);
    }
}
