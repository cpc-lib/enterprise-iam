package com.enterprise.iam.identity.application.model;

import com.enterprise.iam.identity.domain.model.UserStatus;

public record CreateUserResult(
        long userId,
        UserStatus status,
        long version) {
}
