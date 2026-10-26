package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.infrastructure.persistence.InMemoryAuthorizationDecisionRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExplainDecisionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void explainsAllowDecisionWithSanitizedSteps() {
        AuthorizationDecisionRecordRepository repo = new InMemoryAuthorizationDecisionRecordRepository();
        ExplainDecisionUseCase useCase = new ExplainDecisionUseCase(repo);
        AuthorizationResult result = new AuthorizationResult(
                AuthorizationDecision.ALLOW, AuthorizationReason.GRANT_ALLOW,
                42L, 5L, List.of(10L, 20L), Map.of(), NOW);
        repo.record(result);

        ExplainDecisionUseCase.ExplainResponse response = useCase.explain(42L).orElseThrow();

        assertThat(response.decisionId()).isEqualTo(42L);
        assertThat(response.decision()).isEqualTo("ALLOW");
        assertThat(response.reasonCode()).isEqualTo("IAM_AUTHZ_GRANT_ALLOW");
        assertThat(response.sanitizedSteps()).containsExactly(
                "IAM_AUTHZ_GRANT_ALLOW", "matched_grant_ids=[10, 20]");
    }

    @Test
    void explainsDenyDecisionWithReasonCodeOnly() {
        AuthorizationDecisionRecordRepository repo = new InMemoryAuthorizationDecisionRecordRepository();
        ExplainDecisionUseCase useCase = new ExplainDecisionUseCase(repo);
        AuthorizationResult result = new AuthorizationResult(
                AuthorizationDecision.DENY, AuthorizationReason.NO_MATCHING_GRANT,
                43L, 5L, List.of(), Map.of(), NOW);
        repo.record(result);

        ExplainDecisionUseCase.ExplainResponse response = useCase.explain(43L).orElseThrow();

        assertThat(response.decision()).isEqualTo("DENY");
        assertThat(response.sanitizedSteps()).containsExactly("IAM_AUTHZ_NO_MATCHING_GRANT");
    }

    @Test
    void returnsEmptyForUnknownDecision() {
        ExplainDecisionUseCase useCase = new ExplainDecisionUseCase(
                new InMemoryAuthorizationDecisionRecordRepository());

        Optional<ExplainDecisionUseCase.ExplainResponse> response = useCase.explain(999L);

        assertThat(response).isEmpty();
    }
}
