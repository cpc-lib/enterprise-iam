package com.enterprise.iam.organization.application.model;

import com.enterprise.iam.organization.domain.model.TeamMemberRoleStatus;

public record AssignTeamRoleResult(
        long teamMemberRoleId,
        TeamMemberRoleStatus status,
        long version) {
}
