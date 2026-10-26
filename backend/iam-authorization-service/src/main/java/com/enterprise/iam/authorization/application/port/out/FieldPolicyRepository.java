package com.enterprise.iam.authorization.application.port.out;

public interface FieldPolicyRepository {

    long create(long tenantId, long resourceId, long operationId, long fieldId,
                boolean readable, boolean writable, boolean hidden,
                Long maskStrategyId, int priority);
}
