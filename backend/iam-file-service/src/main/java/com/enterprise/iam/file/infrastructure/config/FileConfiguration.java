package com.enterprise.iam.file.infrastructure.config;

import com.enterprise.iam.file.application.port.out.FileGovernanceRepository;
import com.enterprise.iam.file.application.port.out.FileObjectRepository;
import com.enterprise.iam.file.application.port.out.FileObjectStoragePort;
import com.enterprise.iam.file.application.port.out.FilePurgeRepository;
import com.enterprise.iam.file.application.port.out.FileReferenceRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.FileScanPort;
import com.enterprise.iam.file.application.port.out.FileScanRepository;
import com.enterprise.iam.file.application.port.out.FileUploadPartRepository;
import com.enterprise.iam.file.application.port.out.FileUploadSessionRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.application.port.out.QuotaReconcileRepository;
import com.enterprise.iam.file.application.port.out.UploadSessionReaperRepository;
import com.enterprise.iam.file.application.service.AbortFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.AuthorizeFileDownloadUseCase;
import com.enterprise.iam.file.application.service.AuthorizeFilePreviewUseCase;
import com.enterprise.iam.file.application.service.CompleteFileUploadUseCase;
import com.enterprise.iam.file.application.service.CreateFileReferenceUseCase;
import com.enterprise.iam.file.application.service.CreateFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.DeleteLogicalFileUseCase;
import com.enterprise.iam.file.application.service.FileScanningUseCase;
import com.enterprise.iam.file.application.service.FileWatermarkAndDlpUseCase;
import com.enterprise.iam.file.application.service.GetFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.PurgeReconcileUseCase;
import com.enterprise.iam.file.application.service.QuotaReconcileUseCase;
import com.enterprise.iam.file.application.service.ReportFileUploadPartUseCase;
import com.enterprise.iam.file.application.service.ScanRetryPolicy;
import com.enterprise.iam.file.application.service.SignPartUploadUseCase;
import com.enterprise.iam.file.application.service.UploadSessionReaperUseCase;
import com.enterprise.iam.file.domain.model.ScanPolicy;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileGovernanceRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileObjectRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFilePurgeRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileReferenceRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileScanRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileUploadPartRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcFileUploadSessionRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcQuotaReconcileRepository;
import com.enterprise.iam.file.infrastructure.persistence.JdbcUploadSessionReaperRepository;
import com.enterprise.iam.file.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;
import com.enterprise.iam.file.infrastructure.scan.FileScanAdapters;
import com.enterprise.iam.file.infrastructure.storage.LoggingNoopStorageAdapter;
import com.enterprise.iam.file.infrastructure.storage.MinioStorageAdapter;
import com.enterprise.iam.file.interfaces.rest.FileExceptionHandler;
import com.enterprise.iam.file.interfaces.rest.FileReferenceController;
import com.enterprise.iam.file.interfaces.rest.FileUploadController;
import com.enterprise.iam.file.interfaces.rest.FileV2GovernanceController;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.file", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FileWorkerProperties.class)
public class FileConfiguration {

    @Bean
    public Clock fileClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PositiveIdGenerator positiveIdGenerator(
            Clock fileClock,
            @Value("${iam.file.node-id:3}") int nodeId) {
        return new TimeOrderedPositiveIdGenerator(fileClock, nodeId);
    }

    @Bean
    public FileUploadSessionRepository fileUploadSessionRepository(FilePersistenceMapper mapper) {
        return new JdbcFileUploadSessionRepository(mapper);
    }

    @Bean
    public FileUploadPartRepository fileUploadPartRepository(FilePersistenceMapper mapper) {
        return new JdbcFileUploadPartRepository(mapper);
    }

    @Bean
    public FileObjectRepository fileObjectRepository(FilePersistenceMapper mapper) {
        return new JdbcFileObjectRepository(mapper);
    }

    @Bean
    public FileRepository fileRepository(FilePersistenceMapper mapper) {
        return new JdbcFileRepository(mapper);
    }

    @Bean
    public FileReferenceRepository fileReferenceRepository(FilePersistenceMapper mapper) {
        return new JdbcFileReferenceRepository(mapper);
    }

    @Bean
    public CreateFileUploadSessionUseCase createFileUploadSessionUseCase(
            FileUploadSessionRepository sessionRepository,
            FileObjectRepository fileObjectRepository,
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator,
            Clock fileClock) {
        return new CreateFileUploadSessionUseCase(
                sessionRepository, fileObjectRepository, fileRepository, idGenerator, fileClock);
    }

    @Bean
    public SignPartUploadUseCase signPartUploadUseCase(
            FileUploadSessionRepository sessionRepository, Clock fileClock) {
        return new SignPartUploadUseCase(sessionRepository, fileClock);
    }

    @Bean
    public ReportFileUploadPartUseCase reportFileUploadPartUseCase(
            FileUploadPartRepository partRepository,
            FileUploadSessionRepository sessionRepository,
            PositiveIdGenerator idGenerator,
            Clock fileClock) {
        return new ReportFileUploadPartUseCase(partRepository, sessionRepository, idGenerator, fileClock);
    }

    @Bean
    public CompleteFileUploadUseCase completeFileUploadUseCase(
            FileUploadSessionRepository sessionRepository,
            FileObjectRepository fileObjectRepository,
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator) {
        return new CompleteFileUploadUseCase(sessionRepository, fileObjectRepository, fileRepository, idGenerator);
    }

    @Bean
    public GetFileUploadSessionUseCase getFileUploadSessionUseCase(
            FileUploadSessionRepository sessionRepository,
            FileUploadPartRepository partRepository) {
        return new GetFileUploadSessionUseCase(sessionRepository, partRepository);
    }

    @Bean
    public AbortFileUploadSessionUseCase abortFileUploadSessionUseCase(
            FileUploadSessionRepository sessionRepository) {
        return new AbortFileUploadSessionUseCase(sessionRepository);
    }

    @Bean
    public CreateFileReferenceUseCase createFileReferenceUseCase(
            FileReferenceRepository referenceRepository,
            FileRepository fileRepository,
            FileObjectRepository fileObjectRepository,
            PositiveIdGenerator idGenerator) {
        return new CreateFileReferenceUseCase(referenceRepository, fileRepository, fileObjectRepository, idGenerator);
    }

    @Bean
    public AuthorizeFileDownloadUseCase authorizeFileDownloadUseCase(
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator,
            Clock fileClock) {
        return new AuthorizeFileDownloadUseCase(fileRepository, idGenerator, fileClock);
    }

    @Bean
    public AuthorizeFilePreviewUseCase authorizeFilePreviewUseCase(
            FileRepository fileRepository,
            PositiveIdGenerator idGenerator,
            Clock fileClock) {
        return new AuthorizeFilePreviewUseCase(fileRepository, idGenerator, fileClock);
    }

    @Bean
    public DeleteLogicalFileUseCase deleteLogicalFileUseCase(
            FileRepository fileRepository,
            FileObjectRepository fileObjectRepository,
            PositiveIdGenerator idGenerator) {
        return new DeleteLogicalFileUseCase(fileRepository, fileObjectRepository, idGenerator);
    }

    // ---- Scan & lifecycle workers (SPEC 35) ----

    @Bean
    public FileScanRepository fileScanRepository(
            FilePersistenceMapper mapper, PositiveIdGenerator idGenerator) {
        return new JdbcFileScanRepository(mapper, idGenerator);
    }

    @Bean
    public UploadSessionReaperRepository uploadSessionReaperRepository(FilePersistenceMapper mapper) {
        return new JdbcUploadSessionReaperRepository(mapper);
    }

    @Bean
    public QuotaReconcileRepository quotaReconcileRepository(FilePersistenceMapper mapper) {
        return new JdbcQuotaReconcileRepository(mapper);
    }

    @Bean
    public FilePurgeRepository filePurgeRepository(FilePersistenceMapper mapper) {
        return new JdbcFilePurgeRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileObjectStoragePort fileObjectStoragePort(FileWorkerProperties properties) {
        String storage = properties.getPurge().getStorage();
        if ("noop".equalsIgnoreCase(storage)) {
            // DEV: logs "would delete objectKey"; no real remote delete happens.
            return new LoggingNoopStorageAdapter();
        }
        if ("minio".equalsIgnoreCase(storage)) {
            FileWorkerProperties.Minio minio = properties.getPurge().getMinio();
            return new MinioStorageAdapter(MinioClient.builder()
                    .endpoint(minio.getEndpoint())
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .build());
        }
        throw new IllegalStateException(
                "unsupported iam.file.purge.storage: " + storage + " (V1 supports: noop, minio)");
    }

    @Bean
    @ConditionalOnMissingBean
    public FileScanPort fileScanPort(FileWorkerProperties properties) {
        FileWorkerProperties.Scan scan = properties.getScan();
        // Fails bean startup with IllegalStateException when noop is selected while
        // scan-required=true (SPEC 35 §10).
        return FileScanAdapters.create(
                scan.getAdapter(), scan.isScanRequired(), scan.getBlockedExtensions());
    }

    @Bean
    public ScanPolicy scanPolicy(FileWorkerProperties properties) {
        return new ScanPolicy(
                properties.getScan().isScanRequired(),
                true,
                properties.getScan().getMaxAttempts());
    }

    @Bean
    public ScanRetryPolicy scanRetryPolicy(FileWorkerProperties properties) {
        FileWorkerProperties.Scan scan = properties.getScan();
        return new ScanRetryPolicy(scan.getBackoffBase(), scan.getBackoffCap());
    }

    @Bean
    public FileScanningUseCase fileScanningUseCase(
            FileScanRepository fileScanRepository,
            FileScanPort fileScanPort,
            ScanPolicy scanPolicy,
            ScanRetryPolicy scanRetryPolicy,
            FileWorkerProperties properties,
            Clock fileClock) {
        return new FileScanningUseCase(
                fileScanRepository,
                fileScanPort,
                scanPolicy,
                scanRetryPolicy,
                properties.getScan().getAdapter(),
                fileClock);
    }

    @Bean
    public UploadSessionReaperUseCase uploadSessionReaperUseCase(
            UploadSessionReaperRepository uploadSessionReaperRepository, Clock fileClock) {
        return new UploadSessionReaperUseCase(uploadSessionReaperRepository, fileClock);
    }

    @Bean
    public QuotaReconcileUseCase quotaReconcileUseCase(
            QuotaReconcileRepository quotaReconcileRepository) {
        return new QuotaReconcileUseCase(quotaReconcileRepository);
    }

    @Bean
    public PurgeReconcileUseCase purgeReconcileUseCase(
            FilePurgeRepository filePurgeRepository,
            FileObjectStoragePort fileObjectStoragePort,
            FileWorkerProperties properties,
            Clock fileClock) {
        Duration defaultRetention = Duration.ofDays(properties.getPurge().getDefaultRetentionDays());
        return new PurgeReconcileUseCase(
                filePurgeRepository, fileObjectStoragePort, fileClock, defaultRetention);
    }

    @Bean
    public FileUploadController fileUploadController(
            CreateFileUploadSessionUseCase createUseCase,
            SignPartUploadUseCase signUseCase,
            ReportFileUploadPartUseCase reportUseCase,
            CompleteFileUploadUseCase completeUseCase,
            GetFileUploadSessionUseCase getUseCase,
            AbortFileUploadSessionUseCase abortUseCase) {
        return new FileUploadController(
                createUseCase, signUseCase, reportUseCase, completeUseCase, getUseCase, abortUseCase);
    }

    @Bean
    public FileReferenceController fileReferenceController(
            CreateFileReferenceUseCase createReferenceUseCase,
            @Qualifier("authorizeFileDownloadUseCase") AuthorizeFileDownloadUseCase downloadUseCase,
            AuthorizeFilePreviewUseCase previewUseCase,
            DeleteLogicalFileUseCase deleteUseCase) {
        return new FileReferenceController(
                createReferenceUseCase, downloadUseCase, previewUseCase, deleteUseCase);
    }

    @Bean
    public FileExceptionHandler fileExceptionHandler() {
        return new FileExceptionHandler();
    }

    @Bean
    public FileGovernanceRepository fileGovernanceRepository(FilePersistenceMapper mapper) {
        return new JdbcFileGovernanceRepository(mapper);
    }

    @Bean
    public FileWatermarkAndDlpUseCase fileWatermarkAndDlpUseCase(
            FileRepository fileRepository,
            FileGovernanceRepository governanceRepository,
            PositiveIdGenerator idGenerator,
            Clock fileClock) {
        return new FileWatermarkAndDlpUseCase(fileRepository, governanceRepository, idGenerator, fileClock);
    }

    @Bean
    public FileV2GovernanceController fileV2GovernanceController(
            FileWatermarkAndDlpUseCase fileWatermarkAndDlpUseCase) {
        return new FileV2GovernanceController(fileWatermarkAndDlpUseCase);
    }
}
