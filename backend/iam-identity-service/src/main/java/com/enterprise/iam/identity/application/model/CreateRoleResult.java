package com.enterprise.iam.identity.application.model;

import com.enterprise.iam.identity.domain.model.RoleStatus;

public record CreateRoleResult(
        long roleId,
        RoleStatus status,
        long version) {
}
