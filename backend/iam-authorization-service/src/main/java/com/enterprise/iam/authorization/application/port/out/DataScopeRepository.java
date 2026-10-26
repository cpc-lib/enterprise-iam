package com.enterprise.iam.authorization.application.port.out;

public interface DataScopeRepository {

    long create(long tenantId, String scopeCode, String scopeName, String scopeType, Long customPolicyId);
}
