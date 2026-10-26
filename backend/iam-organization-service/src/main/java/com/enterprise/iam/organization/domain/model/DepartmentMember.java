package com.enterprise.iam.organization.domain.model;

import java.time.Instant;

public record DepartmentMember(
        long userId,
        long tenantId,
        String username,
        String displayName,
        String email,
        String phone,
        String status,
        Long departmentId,
        Long positionId,
        String positionName,
        Instant joinedAt
) {}
