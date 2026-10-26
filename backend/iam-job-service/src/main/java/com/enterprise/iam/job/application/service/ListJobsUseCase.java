package com.enterprise.iam.job.application.service;

import com.enterprise.iam.job.application.port.out.JobDefinitionRepository;
import com.enterprise.iam.job.domain.model.JobDefinition;

import java.util.List;

public class ListJobsUseCase {

    private final JobDefinitionRepository jobDefinitionRepository;

    public ListJobsUseCase(JobDefinitionRepository jobDefinitionRepository) {
        this.jobDefinitionRepository = jobDefinitionRepository;
    }

    public List<JobDefinition> listJobs() {
        return jobDefinitionRepository.findAll();
    }
}
