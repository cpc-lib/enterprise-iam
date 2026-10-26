package com.enterprise.iam.job.application.service;

import com.enterprise.iam.job.application.port.out.JobDefinitionRepository;
import com.enterprise.iam.job.domain.model.JobDefinition;

import java.time.Clock;
import java.util.NoSuchElementException;

public class PauseJobUseCase {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final Clock clock;

    public PauseJobUseCase(JobDefinitionRepository jobDefinitionRepository, Clock clock) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.clock = clock;
    }

    public JobDefinition pause(String jobCode) {
        JobDefinition definition = jobDefinitionRepository.findByJobCode(jobCode)
                .orElseThrow(() -> new NoSuchElementException("Job definition not found for code: " + jobCode));
        definition.pause(clock.instant());
        jobDefinitionRepository.save(definition);
        return definition;
    }
}
