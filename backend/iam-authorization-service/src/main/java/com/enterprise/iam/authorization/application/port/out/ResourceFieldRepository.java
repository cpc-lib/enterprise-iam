package com.enterprise.iam.authorization.application.port.out;

import java.util.List;

public interface ResourceFieldRepository {

    record ResourceFieldInput(
            String fieldCode,
            String propertyPath,
            String columnName,
            String dataType,
            String sensitiveLevel,
            boolean systemManaged) {
    }

    void replaceFields(long tenantId, long resourceId, List<ResourceFieldInput> fields, long expectedResourceVersion);
}
