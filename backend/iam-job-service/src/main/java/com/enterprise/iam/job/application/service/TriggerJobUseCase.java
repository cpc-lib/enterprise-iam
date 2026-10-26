package com.enterprise.iam.job.application.service;

import com.enterprise.iam.job.application.port.out.JobDefinitionRepository;
import com.enterprise.iam.job.application.port.out.JobExecutionLogRepository;
import com.enterprise.iam.job.application.port.out.JobExecutor;
import com.enterprise.iam.job.domain.model.JobDefinition;
import com.enterprise.iam.job.domain.model.JobExecutionLog;
import com.enterprise.iam.job.domain.model.JobExecutionResult;
import com.enterprise.iam.job.domain.model.JobExecutionStatus;
import com.enterprise.iam.job.domain.model.JobStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TriggerJobUseCase {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final Map<String, JobExecutor> executors;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());

    public TriggerJobUseCase(
            JobDefinitionRepository jobDefinitionRepository,
            JobExecutionLogRepository jobExecutionLogRepository,
            List<JobExecutor> executorList,
            Clock clock) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobExecutionLogRepository = jobExecutionLogRepository;
        this.executors = executorList.stream()
                .collect(Collectors.toMap(JobExecutor::jobCode, Function.identity()));
        this.clock = clock;
    }

    public JobExecutionLog trigger(String jobCode) {
        JobDefinition definition = jobDefinitionRepository.findByJobCode(jobCode)
                .orElseThrow(() -> new NoSuchElementException("Job definition not found for code: " + jobCode));

        if (definition.getStatus() == JobStatus.PAUSED) {
            throw new IllegalStateException("Job is currently PAUSED: " + jobCode);
        }

        JobExecutor executor = executors.get(jobCode);
        if (executor == null) {
            throw new IllegalStateException("No executor registered for job: " + jobCode);
        }

        Instant startTime = clock.instant();
        String executionId = UUID.randomUUID().toString();
        long logId = sequence.incrementAndGet();

        JobExecutionLog executionLog = new JobExecutionLog(
                logId,
                jobCode,
                executionId,
                JobExecutionStatus.RUNNING,
                startTime,
                null,
                0,
                0,
                null,
                startTime);

        jobExecutionLogRepository.insert(executionLog);

        try {
            JobExecutionResult result = executor.execute();
            Instant endTime = clock.instant();
            if (result.errorMessage() == null) {
                executionLog.complete(result.itemsProcessed(), result.itemsConverged(), endTime);
            } else {
                executionLog.fail(result.errorMessage(), endTime);
            }
        } catch (Exception ex) {
            Instant endTime = clock.instant();
            executionLog.fail(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(), endTime);
        }

        jobExecutionLogRepository.update(executionLog);
        return executionLog;
    }
}
