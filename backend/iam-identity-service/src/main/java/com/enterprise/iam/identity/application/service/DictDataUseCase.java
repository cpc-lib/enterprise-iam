package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.DictDataRepository;
import com.enterprise.iam.identity.application.port.out.DictTypeRepository;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.domain.model.DictData;
import com.enterprise.iam.identity.domain.model.DictStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DictDataUseCase {

    private final DictDataRepository dictDataRepository;
    private final DictTypeRepository dictTypeRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public DictDataUseCase(
            DictDataRepository dictDataRepository,
            DictTypeRepository dictTypeRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.dictDataRepository = Objects.requireNonNull(dictDataRepository);
        this.dictTypeRepository = Objects.requireNonNull(dictTypeRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public DictData create(
            long tenantId,
            String dictType,
            String dictLabel,
            String dictValue,
            int sortOrder,
            DictStatus status,
            String cssTag,
            boolean isDefault,
            String description) {
        if (dictType == null || dictType.isBlank()) {
            throw new IllegalArgumentException("dictType cannot be empty");
        }
        if (dictLabel == null || dictLabel.isBlank()) {
            throw new IllegalArgumentException("dictLabel cannot be empty");
        }
        if (dictValue == null || dictValue.isBlank()) {
            throw new IllegalArgumentException("dictValue cannot be empty");
        }

        dictTypeRepository.findByType(tenantId, dictType.trim())
                .orElseThrow(() -> new IllegalArgumentException("dictType does not exist: " + dictType));

        long id = idGenerator.nextId();
        Instant now = Instant.now(clock);
        DictData data = new DictData(
                id,
                tenantId,
                dictType.trim(),
                dictLabel.trim(),
                dictValue.trim(),
                sortOrder,
                status != null ? status : DictStatus.ACTIVE,
                cssTag,
                isDefault,
                description,
                0L,
                now,
                now
        );
        return dictDataRepository.save(data);
    }

    public DictData update(
            long tenantId,
            long id,
            String dictLabel,
            String dictValue,
            int sortOrder,
            DictStatus status,
            String cssTag,
            boolean isDefault,
            String description,
            long expectedVersion) {
        DictData existing = dictDataRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("dictData not found: " + id));

        DictData updated = new DictData(
                existing.id(),
                existing.tenantId(),
                existing.dictType(),
                (dictLabel != null && !dictLabel.isBlank()) ? dictLabel.trim() : existing.dictLabel(),
                (dictValue != null && !dictValue.isBlank()) ? dictValue.trim() : existing.dictValue(),
                sortOrder,
                status != null ? status : existing.status(),
                cssTag,
                isDefault,
                description,
                expectedVersion,
                existing.createdAt(),
                Instant.now(clock)
        );
        return dictDataRepository.update(updated);
    }

    public boolean delete(long tenantId, long id) {
        return dictDataRepository.delete(tenantId, id);
    }

    public Optional<DictData> getById(long tenantId, long id) {
        return dictDataRepository.findById(tenantId, id);
    }

    public List<DictData> getByType(long tenantId, String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        return dictDataRepository.findByType(tenantId, dictType.trim());
    }

    public DictDataPageResult list(long tenantId, String dictType, String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;

        String safeType = (dictType != null && !dictType.isBlank()) ? dictType.trim() : null;
        String safeStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String safeKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        List<DictData> items = dictDataRepository.findPage(tenantId, safeType, safeKeyword, safeStatus, offset, safePageSize);
        long total = dictDataRepository.count(tenantId, safeType, safeKeyword, safeStatus);

        return new DictDataPageResult(items, total, safePage, safePageSize);
    }

    public record DictDataPageResult(
            List<DictData> items,
            long total,
            int page,
            int pageSize
    ) {}
}
