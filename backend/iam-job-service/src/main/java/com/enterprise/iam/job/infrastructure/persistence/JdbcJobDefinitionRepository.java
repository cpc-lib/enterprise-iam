package com.enterprise.iam.job.infrastructure.persistence;

import com.enterprise.iam.job.application.port.out.JobDefinitionRepository;
import com.enterprise.iam.job.domain.model.JobDefinition;
import com.enterprise.iam.job.domain.model.JobStatus;
import com.enterprise.iam.job.infrastructure.persistence.mapper.JobDefinitionRow;
import com.enterprise.iam.job.infrastructure.persistence.mapper.JobPersistenceMapper;

import java.util.List;
import java.util.Optional;

public class JdbcJobDefinitionRepository implements JobDefinitionRepository {

    private final JobPersistenceMapper mapper;

    public JdbcJobDefinitionRepository(JobPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<JobDefinition> findAll() {
        return mapper.findAllDefinitions().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<JobDefinition> findByJobCode(String jobCode) {
        JobDefinitionRow row = mapper.findDefinitionByCode(jobCode);
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public void save(JobDefinition jobDefinition) {
        JobDefinitionRow existing = mapper.findDefinitionByCode(jobDefinition.getJobCode());
        JobDefinitionRow row = new JobDefinitionRow(
                jobDefinition.getId(),
                jobDefinition.getJobCode(),
                jobDefinition.getJobName(),
                jobDefinition.getDescription(),
                jobDefinition.getCronExpression(),
                jobDefinition.getStatus().name(),
                jobDefinition.getCreatedAt(),
                jobDefinition.getUpdatedAt());
        if (existing == null) {
            mapper.insertDefinition(row);
        } else {
            mapper.updateDefinition(row);
        }
    }

    private JobDefinition toDomain(JobDefinitionRow row) {
        return new JobDefinition(
                row.id(),
                row.jobCode(),
                row.jobName(),
                row.description(),
                row.cronExpression(),
                JobStatus.valueOf(row.status()),
                row.createdAt(),
                row.updatedAt());
    }
}
