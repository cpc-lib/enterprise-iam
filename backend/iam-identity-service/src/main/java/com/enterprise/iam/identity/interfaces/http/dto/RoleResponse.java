package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;
import com.enterprise.iam.identity.domain.model.RoleStatus;

public record RoleResponse(long roleId, RoleStatus status, @NumericLong long version) {
}
