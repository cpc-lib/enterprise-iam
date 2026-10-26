package com.enterprise.iam.job.application.service;

import com.enterprise.iam.job.application.port.out.JobExecutionLogRepository;
import com.enterprise.iam.job.domain.model.JobExecutionLog;

import java.util.List;

public class ListJobExecutionsUseCase {

    private final JobExecutionLogRepository jobExecutionLogRepository;

    public ListJobExecutionsUseCase(JobExecutionLogRepository jobExecutionLogRepository) {
        this.jobExecutionLogRepository = jobExecutionLogRepository;
    }

    public List<JobExecutionLog> listExecutions(String jobCode, int limit) {
        return jobExecutionLogRepository.findByJobCode(jobCode, limit);
    }
}
