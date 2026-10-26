package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.port.out.FileGovernanceRepository;
import com.enterprise.iam.file.domain.model.FileDrDrillRecord;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcFileGovernanceRepository implements FileGovernanceRepository {

    private final FilePersistenceMapper mapper;

    public JdbcFileGovernanceRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public int updateDlpOutcome(long tenantId, long fileId, String dlpStatus, String fileStatus, Instant now) {
        return mapper.updateFileDlpOutcome(tenantId, fileId, dlpStatus, fileStatus, now);
    }

    @Override
    public int updateStorageTierAndStatus(long tenantId, long fileId, String storageTier, String fileStatus, Instant now) {
        return mapper.updateFileStorageTier(tenantId, fileId, storageTier, fileStatus, now);
    }

    @Override
    public FileDrDrillRecord saveDrDrillRecord(FileDrDrillRecord record) {
        mapper.insertDrDrillLog(
                record.id(),
                record.tenantId(),
                record.scenarioCode(),
                record.targetRpoSeconds(),
                record.actualRpoSeconds(),
                record.targetRtoSeconds(),
                record.actualRtoSeconds(),
                record.verifiedObjects(),
                record.status(),
                record.executedAt());
        return record;
    }

    @Override
    public List<FileDrDrillRecord> findDrDrillsByTenant(long tenantId) {
        return mapper.findDrDrillsByTenant(tenantId);
    }
}
