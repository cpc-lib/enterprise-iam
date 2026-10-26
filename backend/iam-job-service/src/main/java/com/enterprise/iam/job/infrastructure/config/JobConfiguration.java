package com.enterprise.iam.job.infrastructure.config;

import com.enterprise.iam.job.application.executor.OrphanSessionReconcileJobExecutor;
import com.enterprise.iam.job.application.executor.ShareExpirationReconcileJobExecutor;
import com.enterprise.iam.job.application.executor.StorageQuotaAuditJobExecutor;
import com.enterprise.iam.job.application.port.out.JobDefinitionRepository;
import com.enterprise.iam.job.application.port.out.JobExecutionLogRepository;
import com.enterprise.iam.job.application.port.out.JobExecutor;
import com.enterprise.iam.job.application.service.ListJobExecutionsUseCase;
import com.enterprise.iam.job.application.service.ListJobsUseCase;
import com.enterprise.iam.job.application.service.PauseJobUseCase;
import com.enterprise.iam.job.application.service.ResumeJobUseCase;
import com.enterprise.iam.job.application.service.TriggerJobUseCase;
import com.enterprise.iam.job.infrastructure.persistence.JdbcJobDefinitionRepository;
import com.enterprise.iam.job.infrastructure.persistence.JdbcJobExecutionLogRepository;
import com.enterprise.iam.job.infrastructure.persistence.mapper.JobPersistenceMapper;
import com.enterprise.iam.job.interfaces.http.JobManagementController;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.job", name = "enabled", havingValue = "true")
@MapperScan(basePackageClasses = JobPersistenceMapper.class)
public class JobConfiguration {

    @Bean
    public Clock jobClock() {
        return Clock.systemUTC();
    }

    @Bean
    public JobDefinitionRepository jobDefinitionRepository(JobPersistenceMapper mapper) {
        return new JdbcJobDefinitionRepository(mapper);
    }

    @Bean
    public JobExecutionLogRepository jobExecutionLogRepository(JobPersistenceMapper mapper) {
        return new JdbcJobExecutionLogRepository(mapper);
    }

    @Bean
    public ShareExpirationReconcileJobExecutor shareExpirationReconcileJobExecutor() {
        return new ShareExpirationReconcileJobExecutor();
    }

    @Bean
    public OrphanSessionReconcileJobExecutor orphanSessionReconcileJobExecutor() {
        return new OrphanSessionReconcileJobExecutor();
    }

    @Bean
    public StorageQuotaAuditJobExecutor storageQuotaAuditJobExecutor() {
        return new StorageQuotaAuditJobExecutor();
    }

    @Bean
    public ListJobsUseCase listJobsUseCase(JobDefinitionRepository jobDefinitionRepository) {
        return new ListJobsUseCase(jobDefinitionRepository);
    }

    @Bean
    public ListJobExecutionsUseCase listJobExecutionsUseCase(JobExecutionLogRepository jobExecutionLogRepository) {
        return new ListJobExecutionsUseCase(jobExecutionLogRepository);
    }

    @Bean
    public TriggerJobUseCase triggerJobUseCase(
            JobDefinitionRepository jobDefinitionRepository,
            JobExecutionLogRepository jobExecutionLogRepository,
            List<JobExecutor> executors,
            Clock jobClock) {
        return new TriggerJobUseCase(jobDefinitionRepository, jobExecutionLogRepository, executors, jobClock);
    }

    @Bean
    public PauseJobUseCase pauseJobUseCase(JobDefinitionRepository jobDefinitionRepository, Clock jobClock) {
        return new PauseJobUseCase(jobDefinitionRepository, jobClock);
    }

    @Bean
    public ResumeJobUseCase resumeJobUseCase(JobDefinitionRepository jobDefinitionRepository, Clock jobClock) {
        return new ResumeJobUseCase(jobDefinitionRepository, jobClock);
    }

    @Bean
    public JobManagementController jobManagementController(
            ListJobsUseCase listJobsUseCase,
            TriggerJobUseCase triggerJobUseCase,
            ListJobExecutionsUseCase listJobExecutionsUseCase,
            PauseJobUseCase pauseJobUseCase,
            ResumeJobUseCase resumeJobUseCase) {
        return new JobManagementController(
                listJobsUseCase,
                triggerJobUseCase,
                listJobExecutionsUseCase,
                pauseJobUseCase,
                resumeJobUseCase);
    }
}
