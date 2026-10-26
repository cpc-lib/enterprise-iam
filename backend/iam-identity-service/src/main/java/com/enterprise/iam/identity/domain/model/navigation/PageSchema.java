package com.enterprise.iam.identity.domain.model.navigation;

import java.util.List;

public record PageSchema(
        String pageKey,
        String title,
        String resourceType,
        List<PageOperation> operations,
        List<PageColumn> columns) {
}
