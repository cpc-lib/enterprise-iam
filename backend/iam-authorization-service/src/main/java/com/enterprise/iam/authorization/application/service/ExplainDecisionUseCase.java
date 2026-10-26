package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ExplainDecisionUseCase {

    private final AuthorizationDecisionRecordRepository recordRepository;

    public ExplainDecisionUseCase(AuthorizationDecisionRecordRepository recordRepository) {
        this.recordRepository = Objects.requireNonNull(recordRepository);
    }

    public Optional<ExplainResponse> explain(long decisionId) {
        return recordRepository.findById(decisionId).map(this::sanitize);
    }

    private ExplainResponse sanitize(AuthorizationResult result) {
        List<String> steps = new ArrayList<>();
        steps.add(result.reasonCode());
        if (result.decision() == AuthorizationDecision.ALLOW) {
            steps.add("matched_grant_ids=" + result.matchedGrantIds());
        }
        return new ExplainResponse(
                result.decisionId(),
                result.decision().name(),
                result.reasonCode(),
                result.evaluatedAt(),
                List.copyOf(steps));
    }

    public record ExplainResponse(
            long decisionId,
            String decision,
            String reasonCode,
            java.time.Instant evaluatedAt,
            List<String> sanitizedSteps) {
    }
}
