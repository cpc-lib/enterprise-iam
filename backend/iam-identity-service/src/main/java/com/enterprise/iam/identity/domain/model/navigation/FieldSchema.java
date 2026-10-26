package com.enterprise.iam.identity.domain.model.navigation;

import java.util.List;

public record FieldSchema(
        String resourceType,
        List<FieldDefinition> fields) {
}
