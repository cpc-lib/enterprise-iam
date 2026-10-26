package com.enterprise.iam.organization.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;

public record DepartmentNodeDto(
        long id,
        String orgCode,
        String orgName,
        Long parentId,
        String materializedPath,
        int depth,
        String status,
        long version,
        int memberCount,
        Instant createdAt,
        Instant updatedAt,
        List<DepartmentNodeDto> children
) {}
