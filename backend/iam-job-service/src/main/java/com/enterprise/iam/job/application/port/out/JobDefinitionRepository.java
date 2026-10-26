package com.enterprise.iam.job.application.port.out;

import com.enterprise.iam.job.domain.model.JobDefinition;

import java.util.List;
import java.util.Optional;

public interface JobDefinitionRepository {

    List<JobDefinition> findAll();

    Optional<JobDefinition> findByJobCode(String jobCode);

    void save(JobDefinition jobDefinition);
}
