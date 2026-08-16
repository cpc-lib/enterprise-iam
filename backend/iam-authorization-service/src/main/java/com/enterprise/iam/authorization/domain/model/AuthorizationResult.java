package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AuthorizationResult(
        AuthorizationDecision decision,
        AuthorizationReason reason,
        long decisionId,
        long permissionVersion,
        List<Long> matchedGrantIds,
        Map<String, Object> obligations,
        Instant evaluatedAt) {

    public AuthorizationResult {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (decisionId <= 0) {
            throw new IllegalArgumentException("decisionId must be positive");
        }
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("permissionVersion must not be negative");
        }
        matchedGrantIds = matchedGrantIds == null ? List.of() : List.copyOf(matchedGrantIds);
        if (matchedGrantIds.size() > 100) {
            throw new IllegalArgumentException("matchedGrantIds must not exceed 100 entries");
        }
        obligations = obligations == null ? Map.of() : Map.copyOf(obligations);
    }

    public String reasonCode() {
        return reason.code();
    }
}
