package com.enterprise.iam.identity.domain.model.navigation;

import java.util.List;

public record NavSchemaNode(
        String key,
        String title,
        String path,
        String icon,
        Integer order,
        List<NavItem> items) {
}
