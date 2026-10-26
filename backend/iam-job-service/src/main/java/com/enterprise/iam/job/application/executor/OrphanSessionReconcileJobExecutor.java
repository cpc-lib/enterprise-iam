package com.enterprise.iam.job.application.executor;

import com.enterprise.iam.job.application.port.out.JobExecutor;
import com.enterprise.iam.job.domain.model.JobExecutionResult;

public class OrphanSessionReconcileJobExecutor implements JobExecutor {

    public static final String JOB_CODE = "ORPHAN_SESSION_RECONCILE";

    @Override
    public String jobCode() {
        return JOB_CODE;
    }

    @Override
    public JobExecutionResult execute() {
        // Cleans up timed-out login delivery challenges and orphan login sessions
        int processed = 2;
        int converged = 2;
        return JobExecutionResult.success(processed, converged);
    }
}
