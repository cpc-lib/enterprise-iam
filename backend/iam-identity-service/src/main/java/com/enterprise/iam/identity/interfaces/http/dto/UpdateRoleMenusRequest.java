package com.enterprise.iam.identity.interfaces.http.dto;

import java.util.List;

public record UpdateRoleMenusRequest(
        List<Long> menuIds
) {}
