package com.enterprise.iam.identity.interfaces.http.dto;

public record PageColumnDto(
        String fieldKey,
        String label,
        String dataType,
        boolean sortable,
        boolean filterable) {
}
