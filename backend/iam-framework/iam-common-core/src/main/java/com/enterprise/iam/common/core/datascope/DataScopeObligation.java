package com.enterprise.iam.common.core.datascope;

import java.util.List;

/**
 * One resolved dataScopes obligation entry. teamIds carries concrete teams
 * for SPECIFIED_TEAM / TEAM_AND_CHILDREN; customPolicyId is present for
 * CUSTOM_POLICY and cannot be enforced generically.
 */
public record DataScopeObligation(
        String scopeType,
        String scopeCode,
        Long customPolicyId,
        String mergeMode,
        List<Long> teamIds) {

    public DataScopeObligation {
        teamIds = teamIds == null ? List.of() : List.copyOf(teamIds);
    }
}
