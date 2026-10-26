package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;
import com.enterprise.iam.identity.domain.model.UserStatus;

public record UserResponse(long userId, UserStatus status, @NumericLong long version) {
}
