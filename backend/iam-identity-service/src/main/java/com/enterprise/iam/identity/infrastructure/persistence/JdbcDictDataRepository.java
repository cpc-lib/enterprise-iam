package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.DictDataRepository;
import com.enterprise.iam.identity.domain.model.DictData;
import com.enterprise.iam.identity.domain.model.DictStatus;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper.DictDataRow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcDictDataRepository implements DictDataRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcDictDataRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public DictData save(DictData dictData) {
        int updated = mapper.insertDictData(
                dictData.id(),
                dictData.tenantId(),
                dictData.dictType(),
                dictData.dictLabel(),
                dictData.dictValue(),
                dictData.sortOrder(),
                dictData.status().name(),
                dictData.cssTag(),
                dictData.isDefault(),
                dictData.description(),
                dictData.version(),
                Instant.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("dict data insert did not affect exactly one row");
        }
        return dictData;
    }

    @Override
    public DictData update(DictData dictData) {
        int updated = mapper.updateDictData(
                dictData.id(),
                dictData.tenantId(),
                dictData.dictLabel(),
                dictData.dictValue(),
                dictData.sortOrder(),
                dictData.status().name(),
                dictData.cssTag(),
                dictData.isDefault(),
                dictData.description(),
                dictData.version(),
                Instant.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("dict data update failed or version conflict");
        }
        return findById(dictData.tenantId(), dictData.id())
                .orElseThrow(() -> new IllegalStateException("updated dict data not found"));
    }

    @Override
    public boolean delete(long tenantId, long id) {
        return mapper.softDeleteDictData(id, tenantId, Instant.now()) > 0;
    }

    @Override
    public Optional<DictData> findById(long tenantId, long id) {
        return Optional.ofNullable(mapper.selectDictDataById(tenantId, id))
                .map(this::toDomain);
    }

    @Override
    public List<DictData> findByType(long tenantId, String dictType) {
        return mapper.selectDictDataByType(tenantId, dictType)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<DictData> findPage(long tenantId, String dictType, String keyword, String status, int offset, int limit) {
        return mapper.selectDictDataPage(tenantId, dictType, keyword, status, offset, limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(long tenantId, String dictType, String keyword, String status) {
        return mapper.countDictData(tenantId, dictType, keyword, status);
    }

    private DictData toDomain(DictDataRow r) {
        return new DictData(
                r.id(),
                r.tenantId(),
                r.dictType(),
                r.dictLabel(),
                r.dictValue(),
                r.sortOrder(),
                DictStatus.valueOf(r.status()),
                r.cssTag(),
                r.isDefault(),
                r.description(),
                r.version(),
                r.createdAt(),
                r.updatedAt()
        );
    }
}
