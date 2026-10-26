package com.enterprise.iam.audit.infrastructure.config;

import com.enterprise.iam.audit.application.port.out.AuditAnalyticsPort;
import com.enterprise.iam.audit.application.port.out.AuditLogRepository;
import com.enterprise.iam.audit.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.audit.application.service.AuditAnalyticsUseCase;
import com.enterprise.iam.audit.application.service.QueryAuditLogUseCase;
import com.enterprise.iam.audit.application.service.RecordSourcedAuditLogUseCase;
import com.enterprise.iam.audit.application.service.RecordAuditLogUseCase;
import com.enterprise.iam.audit.infrastructure.persistence.ConfigurableAuditAnalyticsAdapter;
import com.enterprise.iam.audit.infrastructure.persistence.JdbcAuditLogRepository;
import com.enterprise.iam.audit.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.audit.infrastructure.persistence.mapper.AuditLogPersistenceMapper;
import com.enterprise.iam.audit.interfaces.rest.AuditAnalyticsController;
import com.enterprise.iam.audit.interfaces.rest.AuditLogController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.audit", name = "enabled", havingValue = "true")
public class AuditConfiguration {

    @Bean
    public Clock auditClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PositiveIdGenerator positiveIdGenerator(
            Clock auditClock,
            @Value("${iam.audit.node-id:1}") int nodeId) {
        return new TimeOrderedPositiveIdGenerator(auditClock, nodeId);
    }

    @Bean
    public AuditLogRepository auditLogRepository(AuditLogPersistenceMapper mapper) {
        return new JdbcAuditLogRepository(mapper);
    }

    @Bean
    public RecordAuditLogUseCase recordAuditLogUseCase(
            AuditLogRepository auditLogRepository,
            PositiveIdGenerator idGenerator,
            Clock auditClock) {
        return new RecordAuditLogUseCase(auditLogRepository, idGenerator, auditClock);
    }

    @Bean
    public QueryAuditLogUseCase queryAuditLogUseCase(AuditLogRepository auditLogRepository) {
        return new QueryAuditLogUseCase(auditLogRepository);
    }

    @Bean
    public RecordSourcedAuditLogUseCase recordSourcedAuditLogUseCase(
            AuditLogRepository auditLogRepository,
            PositiveIdGenerator idGenerator) {
        return new RecordSourcedAuditLogUseCase(auditLogRepository, idGenerator);
    }

    @Bean
    public AuditLogController auditLogController(
            RecordAuditLogUseCase recordAuditLogUseCase,
            QueryAuditLogUseCase queryAuditLogUseCase) {
        return new AuditLogController(recordAuditLogUseCase, queryAuditLogUseCase);
    }

    @Bean
    public AuditAnalyticsPort auditAnalyticsPort(
            AuditLogPersistenceMapper mapper,
            @Value("${iam.audit.olap.engine:relational}") String olapEngine,
            @Value("${iam.audit.olap.archive-target:s3://iam-audit-cold-archive/v2/parquet}") String archiveTarget,
            Clock auditClock) {
        return new ConfigurableAuditAnalyticsAdapter(mapper, olapEngine, archiveTarget, auditClock);
    }

    @Bean
    public AuditAnalyticsUseCase auditAnalyticsUseCase(AuditAnalyticsPort auditAnalyticsPort) {
        return new AuditAnalyticsUseCase(auditAnalyticsPort);
    }

    @Bean
    public AuditAnalyticsController auditAnalyticsController(AuditAnalyticsUseCase auditAnalyticsUseCase) {
        return new AuditAnalyticsController(auditAnalyticsUseCase);
    }
}
