package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.DictType;

import java.util.List;
import java.util.Optional;

public interface DictTypeRepository {

    DictType save(DictType dictType);

    DictType update(DictType dictType);

    boolean delete(long tenantId, long id);

    Optional<DictType> findById(long tenantId, long id);

    Optional<DictType> findByType(long tenantId, String dictType);

    List<DictType> findPage(long tenantId, String keyword, String status, int offset, int limit);

    long count(long tenantId, String keyword, String status);
}
