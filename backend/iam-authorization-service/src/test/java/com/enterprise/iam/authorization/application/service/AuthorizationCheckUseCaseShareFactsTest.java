package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.ShareAclProjectionRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.ShareEpoch;
import com.enterprise.iam.authorization.domain.model.ShareGrant;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCheckUseCaseShareFactsTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private final DefaultAuthorizationEngine engine = new DefaultAuthorizationEngine(
            Clock.fixed(NOW, ZoneOffset.UTC), new AtomicLong(1)::incrementAndGet);
    private final AuthorizationDecisionRecordRepository recordRepository =
            new AuthorizationDecisionRecordRepository() {
                @Override
                public java.util.Optional<AuthorizationResult> findById(long decisionId) {
                    return java.util.Optional.empty();
                }

                @Override
                public void record(AuthorizationResult result) {
                }
            };

    @Test
    void enrichesFactsWithTeamShareGrantAndAllows() {
        AuthorizationRequest request = new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42", 11, "request-0001",
                Map.of("teamIds", List.of(56)));
        ShareGrant teamShare = new ShareGrant(
                902, 7, 300, "invoice-42", ShareGrant.TARGET_TEAM, 56, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 1);
        AuthorizationCheckUseCase useCase = new AuthorizationCheckUseCase(
                emptyRoleFacts(), engine, recordRepository,
                new StubShareRepository(List.of(teamShare), new ShareEpoch(1L, 1L)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        AuthorizationResult result = useCase.check(request);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.ALLOW);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.SHARE_GRANT);
        assertThat(result.matchedGrantIds()).containsExactly(902L);
    }

    @Test
    void failsClosedWhenProjectionEpochTrailsExpectedEpoch() {
        AuthorizationRequest request = new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42", 11, "request-0002", Map.of());
        ShareGrant userShare = new ShareGrant(
                905, 7, 300, "invoice-42", ShareGrant.TARGET_USER, 100, 400,
                NOW.minusSeconds(60), NOW.plusSeconds(60), 2);
        AuthorizationCheckUseCase useCase = new AuthorizationCheckUseCase(
                emptyRoleFacts(), engine, recordRepository,
                new StubShareRepository(List.of(userShare), new ShareEpoch(3L, 2L)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        AuthorizationResult result = useCase.check(request);

        assertThat(result.decision()).isEqualTo(AuthorizationDecision.DENY);
        assertThat(result.reason()).isEqualTo(AuthorizationReason.SHARE_PROJECTION_STALE);
    }

    private AuthorizationFactRepository emptyRoleFacts() {
        return request -> new AuthorizationFacts(
                true, 7, 300, 400, 11, true, List.of());
    }

    private record StubShareRepository(List<ShareGrant> grants, ShareEpoch epoch)
            implements ShareAclProjectionRepository {

        @Override
        public List<ShareGrant> findActiveGrants(
                long tenantId, long subjectUserId, List<Long> teamIds,
                long resourceId, String resourceInstanceKey, long operationId,
                Instant evaluatedAt) {
            return grants;
        }

        @Override
        public ShareEpoch readEpoch(long tenantId, long resourceId) {
            return epoch;
        }
    }
}
