package com.enterprise.iam.authorization.interfaces.http.dto;

import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuthorizationCheckResponse(
        String decision,
        String reasonCode,
        long decisionId,
        @NumericLong long permissionVersion,
        List<Long> matchedGrantIds,
        Map<String, Object> obligations,
        Instant evaluatedAt) {

    public static AuthorizationCheckResponse fromResult(AuthorizationResult result) {
        return new AuthorizationCheckResponse(
                result.decision().name(),
                result.reasonCode(),
                result.decisionId(),
                result.permissionVersion(),
                result.matchedGrantIds(),
                result.obligations(),
                result.evaluatedAt());
    }
}
