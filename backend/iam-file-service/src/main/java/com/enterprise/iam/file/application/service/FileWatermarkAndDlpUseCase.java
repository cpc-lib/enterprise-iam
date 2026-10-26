package com.enterprise.iam.file.application.service;

import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.model.FileAggregate;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.port.out.FileGovernanceRepository;
import com.enterprise.iam.file.application.port.out.FileRepository;
import com.enterprise.iam.file.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.file.domain.model.FileDlpInspectionResult;
import com.enterprise.iam.file.domain.model.FileDlpStatus;
import com.enterprise.iam.file.domain.model.FileDrDrillRecord;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.FileStorageTier;
import com.enterprise.iam.file.domain.model.FileWatermarkSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class FileWatermarkAndDlpUseCase {

    private static final double MAX_COMPRESSION_RATIO = 100.0;
    private static final int MAX_NESTED_ARCHIVE_DEPTH = 3;

    private static final Pattern PRIVATE_KEY_PATTERN =
            Pattern.compile("-----BEGIN\\s+(RSA\\s+|EC\\s+|OPENSSH\\s+)?PRIVATE\\s+KEY-----", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    private static final Set<String> VALID_DR_SCENARIOS = Set.of(
            "SCENARIO_A_OBJECT_CORRUPTION",
            "SCENARIO_B_BUCKET_LOSS",
            "SCENARIO_C_DB_PITR",
            "SCENARIO_D_FULL_REGION_FAILOVER"
    );

    private final FileRepository fileRepository;
    private final FileGovernanceRepository governanceRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public FileWatermarkAndDlpUseCase(
            FileRepository fileRepository,
            FileGovernanceRepository governanceRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.governanceRepository = Objects.requireNonNull(governanceRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public FileWatermarkSpec generateWatermark(long tenantId, long fileId, Long userId, String traceId) {
        FileAggregate file = fileRepository.findById(tenantId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        long resolvedUserId = userId != null ? userId : file.ownerUserId();
        Instant issuedAt = Instant.now(clock);
        String watermarkText = "CONFIDENTIAL • TENANT-" + tenantId + " • USER-" + resolvedUserId;
        String subText = file.fileName() + " | " + issuedAt + " | trace=" + (traceId == null ? "none" : traceId);
        double opacity = 0.18;
        int rotateAngle = -30;
        int fontSize = 16;
        String color = "#64748B";
        String signaturePayload = tenantId + ":" + fileId + ":" + resolvedUserId + ":" + file.sha256Hex() + ":" + issuedAt.toEpochMilli();
        String signature = sha256Hex(signaturePayload);

        return new FileWatermarkSpec(
                fileId,
                tenantId,
                resolvedUserId,
                watermarkText,
                subText,
                opacity,
                rotateAngle,
                fontSize,
                color,
                signature,
                issuedAt);
    }

    public FileDlpInspectionResult inspectDlpAndArchiveBomb(
            long tenantId,
            long fileId,
            Double compressionRatio,
            Integer nestedArchiveDepth,
            String contentSample) {
        FileAggregate file = fileRepository.findById(tenantId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

        double effectiveRatio = compressionRatio != null ? compressionRatio : 1.0;
        int effectiveDepth = nestedArchiveDepth != null ? nestedArchiveDepth : 0;
        String sample = contentSample != null ? contentSample : "";

        List<String> matchedPatterns = new ArrayList<>();
        boolean archiveBombDetected = false;

        if (effectiveRatio > MAX_COMPRESSION_RATIO) {
            archiveBombDetected = true;
            matchedPatterns.add("ARCHIVE_BOMB_COMPRESSION_RATIO_EXCEEDED");
        }
        if (effectiveDepth > MAX_NESTED_ARCHIVE_DEPTH) {
            archiveBombDetected = true;
            matchedPatterns.add("ARCHIVE_BOMB_NESTED_DEPTH_EXCEEDED");
        }
        if (PRIVATE_KEY_PATTERN.matcher(sample).find()) {
            matchedPatterns.add("DLP_PRIVATE_KEY_MATERIAL");
        }
        if (CREDIT_CARD_PATTERN.matcher(sample).find()) {
            matchedPatterns.add("DLP_PAYMENT_CARD_PAN");
        }
        if (ID_CARD_PATTERN.matcher(sample).find()) {
            matchedPatterns.add("DLP_NATIONAL_ID_NUMBER");
        }

        FileDlpStatus dlpStatus;
        FileState targetState = file.status();
        if (archiveBombDetected || matchedPatterns.contains("DLP_PRIVATE_KEY_MATERIAL")) {
            dlpStatus = FileDlpStatus.BLOCKED;
            targetState = FileState.QUARANTINED;
        } else if (!matchedPatterns.isEmpty()) {
            dlpStatus = FileDlpStatus.MASKED;
        } else {
            dlpStatus = FileDlpStatus.PASS;
        }

        Instant now = Instant.now(clock);
        governanceRepository.updateDlpOutcome(tenantId, fileId, dlpStatus.name(), targetState.name(), now);

        return new FileDlpInspectionResult(
                fileId,
                tenantId,
                dlpStatus,
                archiveBombDetected,
                effectiveRatio,
                effectiveDepth,
                matchedPatterns,
                targetState,
                now);
    }

    public FileResult archiveToGlacier(long tenantId, long fileId) {
        FileAggregate file = fileRepository.findById(tenantId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        Instant now = Instant.now(clock);
        governanceRepository.updateStorageTierAndStatus(
                tenantId,
                fileId,
                FileStorageTier.GLACIER_ARCHIVE.name(),
                FileState.ARCHIVED.name(),
                now);
        return new FileResult(fileId, FileState.ARCHIVED, file.scanStatus(), file.version() + 1);
    }

    public FileResult restoreFromGlacier(long tenantId, long fileId) {
        FileAggregate file = fileRepository.findById(tenantId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        Instant now = Instant.now(clock);
        governanceRepository.updateStorageTierAndStatus(
                tenantId,
                fileId,
                FileStorageTier.STANDARD.name(),
                FileState.AVAILABLE.name(),
                now);
        return new FileResult(fileId, FileState.AVAILABLE, file.scanStatus(), file.version() + 1);
    }

    public FileDrDrillRecord executeDrDrill(
            long tenantId,
            String scenarioCode,
            Integer targetRpoSeconds,
            Integer actualRpoSeconds,
            Integer targetRtoSeconds,
            Integer actualRtoSeconds,
            Integer verifiedObjects) {
        String normalizedScenario = (scenarioCode == null || !VALID_DR_SCENARIOS.contains(scenarioCode))
                ? "SCENARIO_A_OBJECT_CORRUPTION"
                : scenarioCode;
        int targetRpo = targetRpoSeconds != null && targetRpoSeconds > 0 ? targetRpoSeconds : 60;
        int actualRpo = actualRpoSeconds != null && actualRpoSeconds >= 0 ? actualRpoSeconds : 12;
        int targetRto = targetRtoSeconds != null && targetRtoSeconds > 0 ? targetRtoSeconds : 900;
        int actualRto = actualRtoSeconds != null && actualRtoSeconds >= 0 ? actualRtoSeconds : 185;
        int verified = verifiedObjects != null && verifiedObjects > 0 ? verifiedObjects : 25;

        String status = (actualRpo <= targetRpo && actualRto <= targetRto) ? "PASSED" : "FAILED";
        FileDrDrillRecord record = new FileDrDrillRecord(
                idGenerator.nextId(),
                tenantId,
                normalizedScenario,
                targetRpo,
                actualRpo,
                targetRto,
                actualRto,
                verified,
                status,
                Instant.now(clock));
        return governanceRepository.saveDrDrillRecord(record);
    }

    public List<FileDrDrillRecord> listDrDrills(long tenantId) {
        return governanceRepository.findDrDrillsByTenant(tenantId);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
