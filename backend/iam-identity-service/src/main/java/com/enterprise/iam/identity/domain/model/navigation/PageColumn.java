package com.enterprise.iam.identity.domain.model.navigation;

public record PageColumn(
        String fieldKey,
        String label,
        String dataType,
        boolean sortable,
        boolean filterable) {
}
