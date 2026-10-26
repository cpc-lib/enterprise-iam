package com.enterprise.iam.file.infrastructure.config;

import com.enterprise.iam.file.application.service.FileScanningUseCase;
import com.enterprise.iam.file.application.service.PurgeReconcileUseCase;
import com.enterprise.iam.file.application.service.QuotaReconcileUseCase;
import com.enterprise.iam.file.application.service.UploadSessionReaperUseCase;
import com.enterprise.iam.file.infrastructure.worker.ScheduledFileScanPoller;
import com.enterprise.iam.file.infrastructure.worker.ScheduledPurgeReconciler;
import com.enterprise.iam.file.infrastructure.worker.ScheduledQuotaReconciler;
import com.enterprise.iam.file.infrastructure.worker.ScheduledUploadSessionReaper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers the in-process scheduled workers, following the outbox starter precedent.
 * Scheduling itself is enabled whenever the file module is enabled; each poller bean is
 * independently toggleable, so no @Scheduled method exists for a disabled worker.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "iam.file", name = "enabled", havingValue = "true")
public class FileWorkerSchedulingConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "iam.file.scan", name = "enabled", havingValue = "true")
    public ScheduledFileScanPoller scheduledFileScanPoller(
            FileScanningUseCase fileScanningUseCase, FileWorkerProperties properties) {
        return new ScheduledFileScanPoller(
                fileScanningUseCase, properties.getScan().getClaimBatchSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "iam.file.reaper", name = "enabled", havingValue = "true")
    public ScheduledUploadSessionReaper scheduledUploadSessionReaper(
            UploadSessionReaperUseCase uploadSessionReaperUseCase, FileWorkerProperties properties) {
        return new ScheduledUploadSessionReaper(
                uploadSessionReaperUseCase, properties.getReaper().getBatchSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "iam.file.quota-reconcile", name = "enabled", havingValue = "true")
    public ScheduledQuotaReconciler scheduledQuotaReconciler(
            QuotaReconcileUseCase quotaReconcileUseCase) {
        return new ScheduledQuotaReconciler(quotaReconcileUseCase);
    }

    @Bean
    @ConditionalOnProperty(prefix = "iam.file.purge", name = "enabled", havingValue = "true")
    public ScheduledPurgeReconciler scheduledPurgeReconciler(
            PurgeReconcileUseCase purgeReconcileUseCase, FileWorkerProperties properties) {
        return new ScheduledPurgeReconciler(
                purgeReconcileUseCase, properties.getPurge().getBatchSize());
    }
}
