package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.DictData;

import java.util.List;
import java.util.Optional;

public interface DictDataRepository {

    DictData save(DictData dictData);

    DictData update(DictData dictData);

    boolean delete(long tenantId, long id);

    Optional<DictData> findById(long tenantId, long id);

    List<DictData> findByType(long tenantId, String dictType);

    List<DictData> findPage(long tenantId, String dictType, String keyword, String status, int offset, int limit);

    long count(long tenantId, String dictType, String keyword, String status);
}
