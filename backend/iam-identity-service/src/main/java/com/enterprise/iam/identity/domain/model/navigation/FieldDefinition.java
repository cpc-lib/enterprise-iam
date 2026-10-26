package com.enterprise.iam.identity.domain.model.navigation;

public record FieldDefinition(
        String fieldKey,
        String fieldName,
        String dataType,
        boolean sensitive,
        boolean maskable,
        String description) {
}
