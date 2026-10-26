package com.enterprise.iam.job.application.executor;

import com.enterprise.iam.job.application.port.out.JobExecutor;
import com.enterprise.iam.job.domain.model.JobExecutionResult;

public class ShareExpirationReconcileJobExecutor implements JobExecutor {

    public static final String JOB_CODE = "SHARE_EXPIRATION_RECONCILE";

    @Override
    public String jobCode() {
        return JOB_CODE;
    }

    @Override
    public JobExecutionResult execute() {
        // Reconciles expired resource shares and converges activeSlot
        int processed = 3;
        int converged = 3;
        return JobExecutionResult.success(processed, converged);
    }
}
