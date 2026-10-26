package com.enterprise.iam.job.infrastructure.persistence;

import com.enterprise.iam.job.application.port.out.JobExecutionLogRepository;
import com.enterprise.iam.job.domain.model.JobExecutionLog;
import com.enterprise.iam.job.domain.model.JobExecutionStatus;
import com.enterprise.iam.job.infrastructure.persistence.mapper.JobExecutionLogRow;
import com.enterprise.iam.job.infrastructure.persistence.mapper.JobPersistenceMapper;

import java.util.List;
import java.util.Optional;

public class JdbcJobExecutionLogRepository implements JobExecutionLogRepository {

    private final JobPersistenceMapper mapper;

    public JdbcJobExecutionLogRepository(JobPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(JobExecutionLog log) {
        JobExecutionLogRow row = new JobExecutionLogRow(
                log.getId(),
                log.getJobCode(),
                log.getExecutionId(),
                log.getStatus().name(),
                log.getStartTime(),
                log.getEndTime(),
                log.getItemsProcessed(),
                log.getItemsConverged(),
                log.getErrorMessage(),
                log.getCreatedAt());
        mapper.insertExecutionLog(row);
    }

    @Override
    public void update(JobExecutionLog log) {
        JobExecutionLogRow row = new JobExecutionLogRow(
                log.getId(),
                log.getJobCode(),
                log.getExecutionId(),
                log.getStatus().name(),
                log.getStartTime(),
                log.getEndTime(),
                log.getItemsProcessed(),
                log.getItemsConverged(),
                log.getErrorMessage(),
                log.getCreatedAt());
        mapper.updateExecutionLog(row);
    }

    @Override
    public List<JobExecutionLog> findByJobCode(String jobCode, int limit) {
        return mapper.findExecutionLogsByCode(jobCode, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<JobExecutionLog> findById(long id) {
        JobExecutionLogRow row = mapper.findExecutionLogById(id);
        return Optional.ofNullable(row).map(this::toDomain);
    }

    private JobExecutionLog toDomain(JobExecutionLogRow row) {
        return new JobExecutionLog(
                row.id(),
                row.jobCode(),
                row.executionId(),
                JobExecutionStatus.valueOf(row.status()),
                row.startTime(),
                row.endTime(),
                row.itemsProcessed(),
                row.itemsConverged(),
                row.errorMessage(),
                row.createdAt());
    }
}
