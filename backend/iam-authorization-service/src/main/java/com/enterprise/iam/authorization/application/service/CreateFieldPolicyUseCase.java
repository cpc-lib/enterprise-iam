package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.FieldPolicyRepository;

import java.util.Objects;

public final class CreateFieldPolicyUseCase {

    private final FieldPolicyRepository fieldPolicyRepository;

    public CreateFieldPolicyUseCase(FieldPolicyRepository fieldPolicyRepository) {
        this.fieldPolicyRepository = Objects.requireNonNull(fieldPolicyRepository);
    }

    public Result create(long tenantId, long resourceId, long operationId, long fieldId,
                         boolean readable, boolean writable, boolean hidden,
                         Long maskStrategyId, int priority) {
        if (hidden && (readable || writable)) {
            throw new DomainInvariantException(
                    "hidden field policy must not be readable or writable");
        }
        long id = fieldPolicyRepository.create(
                tenantId, resourceId, operationId, fieldId,
                readable, writable, hidden, maskStrategyId, priority);
        return new Result(id, 0L, "ACTIVE");
    }

    public record Result(long id, long version, String status) {
    }
}
