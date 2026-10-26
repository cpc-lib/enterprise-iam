package com.enterprise.iam.organization.application.model;

import com.enterprise.iam.organization.domain.model.TeamRoleStatus;

public record CreateTeamRoleResult(
        long teamRoleId,
        TeamRoleStatus status,
        long version) {
}
