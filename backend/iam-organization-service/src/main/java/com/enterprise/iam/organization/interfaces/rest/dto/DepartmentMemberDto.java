package com.enterprise.iam.organization.interfaces.rest.dto;

import java.time.Instant;

public record DepartmentMemberDto(
        long userId,
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
