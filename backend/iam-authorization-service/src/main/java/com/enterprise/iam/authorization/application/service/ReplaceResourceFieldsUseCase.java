package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository;
import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository.ResourceFieldInput;

import java.util.List;
import java.util.Objects;

public final class ReplaceResourceFieldsUseCase {

    private final ResourceFieldRepository resourceFieldRepository;

    public ReplaceResourceFieldsUseCase(ResourceFieldRepository resourceFieldRepository) {
        this.resourceFieldRepository = Objects.requireNonNull(resourceFieldRepository);
    }

    public Result replace(long tenantId, long resourceId, List<ResourceFieldInput> fields, long expectedResourceVersion) {
        Objects.requireNonNull(fields, "fields must not be null");
        resourceFieldRepository.replaceFields(tenantId, resourceId, fields, expectedResourceVersion);
        return new Result(resourceId, expectedResourceVersion + 1, "ACTIVE");
    }

    public record Result(long id, long version, String status) {
    }
}
