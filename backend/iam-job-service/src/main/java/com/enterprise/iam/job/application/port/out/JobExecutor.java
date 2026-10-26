package com.enterprise.iam.job.application.port.out;

import com.enterprise.iam.job.domain.model.JobExecutionResult;

public interface JobExecutor {

    String jobCode();

    JobExecutionResult execute();
}
