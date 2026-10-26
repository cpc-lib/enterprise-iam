package com.enterprise.iam.job.application.port.out;

import com.enterprise.iam.job.domain.model.JobExecutionLog;

import java.util.List;
import java.util.Optional;

public interface JobExecutionLogRepository {

    void insert(JobExecutionLog log);

    void update(JobExecutionLog log);

    List<JobExecutionLog> findByJobCode(String jobCode, int limit);

    Optional<JobExecutionLog> findById(long id);
}
