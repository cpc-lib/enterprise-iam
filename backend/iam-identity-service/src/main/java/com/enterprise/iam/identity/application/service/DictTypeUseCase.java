package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.DictTypeRepository;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.domain.model.DictStatus;
import com.enterprise.iam.identity.domain.model.DictType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DictTypeUseCase {

    private final DictTypeRepository dictTypeRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public DictTypeUseCase(
            DictTypeRepository dictTypeRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.dictTypeRepository = Objects.requireNonNull(dictTypeRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public DictType create(
            long tenantId,
            String dictType,
            String dictName,
            DictStatus status,
            String description) {
        if (dictType == null || dictType.isBlank()) {
            throw new IllegalArgumentException("dictType cannot be empty");
        }
        if (dictName == null || dictName.isBlank()) {
            throw new IllegalArgumentException("dictName cannot be empty");
        }

        dictTypeRepository.findByType(tenantId, dictType.trim())
                .ifPresent(existing -> {
                    throw new IllegalStateException("dictType already exists: " + dictType);
                });

        long id = idGenerator.nextId();
        Instant now = Instant.now(clock);
        DictType type = new DictType(
                id,
                tenantId,
                dictType.trim(),
                dictName.trim(),
                status != null ? status : DictStatus.ACTIVE,
                description,
                0L,
                now,
                now
        );
        return dictTypeRepository.save(type);
    }

    public DictType update(
            long tenantId,
            long id,
            String dictName,
            DictStatus status,
            String description,
            long expectedVersion) {
        DictType existing = dictTypeRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("dictType not found: " + id));

        DictType updated = new DictType(
                existing.id(),
                existing.tenantId(),
                existing.dictType(),
                (dictName != null && !dictName.isBlank()) ? dictName.trim() : existing.dictName(),
                status != null ? status : existing.status(),
                description,
                expectedVersion,
                existing.createdAt(),
                Instant.now(clock)
        );
        return dictTypeRepository.update(updated);
    }

    public boolean delete(long tenantId, long id) {
        return dictTypeRepository.delete(tenantId, id);
    }

    public Optional<DictType> getById(long tenantId, long id) {
        return dictTypeRepository.findById(tenantId, id);
    }

    public Optional<DictType> getByType(long tenantId, String dictType) {
        return dictTypeRepository.findByType(tenantId, dictType);
    }

    public DictTypePageResult list(long tenantId, String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;

        String safeStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String safeKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        List<DictType> items = dictTypeRepository.findPage(tenantId, safeKeyword, safeStatus, offset, safePageSize);
        long total = dictTypeRepository.count(tenantId, safeKeyword, safeStatus);

        return new DictTypePageResult(items, total, safePage, safePageSize);
    }

    public record DictTypePageResult(
            List<DictType> items,
            long total,
            int page,
            int pageSize
    ) {}
}
