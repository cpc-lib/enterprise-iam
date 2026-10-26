package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.DictTypeRepository;
import com.enterprise.iam.identity.domain.model.DictStatus;
import com.enterprise.iam.identity.domain.model.DictType;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper.DictTypeRow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcDictTypeRepository implements DictTypeRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcDictTypeRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public DictType save(DictType dictType) {
        int updated = mapper.insertDictType(
                dictType.id(),
                dictType.tenantId(),
                dictType.dictType(),
                dictType.dictName(),
                dictType.status().name(),
                dictType.description(),
                dictType.version(),
                Instant.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("dict type insert did not affect exactly one row");
        }
        return dictType;
    }

    @Override
    public DictType update(DictType dictType) {
        int updated = mapper.updateDictType(
                dictType.id(),
                dictType.tenantId(),
                dictType.dictName(),
                dictType.status().name(),
                dictType.description(),
                dictType.version(),
                Instant.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("dict type update failed or version conflict");
        }
        return findById(dictType.tenantId(), dictType.id())
                .orElseThrow(() -> new IllegalStateException("updated dict type not found"));
    }

    @Override
    public boolean delete(long tenantId, long id) {
        return mapper.softDeleteDictType(id, tenantId, Instant.now()) > 0;
    }

    @Override
    public Optional<DictType> findById(long tenantId, long id) {
        return Optional.ofNullable(mapper.selectDictTypeById(tenantId, id))
                .map(this::toDomain);
    }

    @Override
    public Optional<DictType> findByType(long tenantId, String dictType) {
        return Optional.ofNullable(mapper.selectDictTypeByType(tenantId, dictType))
                .map(this::toDomain);
    }

    @Override
    public List<DictType> findPage(long tenantId, String keyword, String status, int offset, int limit) {
        return mapper.selectDictTypesPage(tenantId, keyword, status, offset, limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(long tenantId, String keyword, String status) {
        return mapper.countDictTypes(tenantId, keyword, status);
    }

    private DictType toDomain(DictTypeRow r) {
        return new DictType(
                r.id(),
                r.tenantId(),
                r.dictType(),
                r.dictName(),
                DictStatus.valueOf(r.status()),
                r.description(),
                r.version(),
                r.createdAt(),
                r.updatedAt()
        );
    }
}
