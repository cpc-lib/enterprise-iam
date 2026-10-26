package com.enterprise.iam.file.application.port.out;

import com.enterprise.iam.file.domain.model.FileDrDrillRecord;

import java.time.Instant;
import java.util.List;

public interface FileGovernanceRepository {

    int updateDlpOutcome(long tenantId, long fileId, String dlpStatus, String fileStatus, Instant now);

    int updateStorageTierAndStatus(long tenantId, long fileId, String storageTier, String fileStatus, Instant now);

    FileDrDrillRecord saveDrDrillRecord(FileDrDrillRecord record);

    List<FileDrDrillRecord> findDrDrillsByTenant(long tenantId);
}
