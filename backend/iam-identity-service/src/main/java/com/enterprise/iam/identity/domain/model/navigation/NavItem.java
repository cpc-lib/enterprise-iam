package com.enterprise.iam.identity.domain.model.navigation;

public record NavItem(
        String key,
        String path,
        String title,
        String icon,
        Integer order) {
}
