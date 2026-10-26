package com.enterprise.iam.organization.application.model;

import com.enterprise.iam.organization.domain.model.TeamMemberStatus;

public record AddTeamMemberResult(
        long teamMemberId,
        TeamMemberStatus membershipStatus,
        long version) {
}
