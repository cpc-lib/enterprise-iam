package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.DataScopeRepository;

import java.util.Objects;

public final class CreateDataScopeUseCase {

    private final DataScopeRepository dataScopeRepository;

    public CreateDataScopeUseCase(DataScopeRepository dataScopeRepository) {
        this.dataScopeRepository = Objects.requireNonNull(dataScopeRepository);
    }

    public Result create(long tenantId, String scopeCode, String scopeName, String scopeType, Long customPolicyId) {
        Objects.requireNonNull(scopeCode, "scopeCode must not be null");
        Objects.requireNonNull(scopeName, "scopeName must not be null");
        Objects.requireNonNull(scopeType, "scopeType must not be null");
        long id = dataScopeRepository.create(tenantId, scopeCode, scopeName, scopeType, customPolicyId);
        return new Result(id, 0L, "ACTIVE");
    }

    public record Result(long id, long version, String status) {
    }
}
