package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.service.AuthorizationCheckUseCase;
import com.enterprise.iam.authorization.application.service.ExplainDecisionUseCase;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import com.enterprise.iam.authorization.infrastructure.persistence.InMemoryAuthorizationDecisionRecordRepository;
import com.enterprise.iam.common.web.json.IamWebJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationCheckControllerExplainTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void explainReturnsDecisionWithSanitizedSteps() throws Exception {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        recordRepo.record(new AuthorizationResult(
                AuthorizationDecision.ALLOW, AuthorizationReason.GRANT_ALLOW,
                42L, 5L, List.of(10L), Map.of(), NOW));

        AtomicLong ids = new AtomicLong(1000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationFactRepository factRepo = new UnavailableFactsRepository();
        AuthorizationCheckUseCase checkUseCase = new AuthorizationCheckUseCase(factRepo, engine, recordRepo);
        ExplainDecisionUseCase explainUseCase = new ExplainDecisionUseCase(recordRepo);
        AuthorizationCheckController controller =
                new AuthorizationCheckController(checkUseCase, explainUseCase);

        MockMvc mockMvc = standaloneMockMvcWithIamJson(controller);

        mockMvc.perform(get("/internal/v1/authorization/decisions/42/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.decisionId").value("42"))
                .andExpect(jsonPath("$.data.decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.reasonCode").value("IAM_AUTHZ_GRANT_ALLOW"))
                .andExpect(jsonPath("$.data.sanitizedSteps[0]").value("IAM_AUTHZ_GRANT_ALLOW"))
                .andExpect(jsonPath("$.data.sanitizedSteps[1]").value("matched_grant_ids=[10]"));
    }

    private static MockMvc standaloneMockMvcWithIamJson(AuthorizationCheckController controller) {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new IamWebJacksonAutoConfiguration().iamIdAsStringCustomizer().customize(builder);
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build()))
                .build();
    }

    @Test
    void explainReturns404ForUnknownDecision() throws Exception {
        AuthorizationDecisionRecordRepository recordRepo = new InMemoryAuthorizationDecisionRecordRepository();
        AtomicLong ids = new AtomicLong(1000);
        DefaultAuthorizationEngine engine =
                new DefaultAuthorizationEngine(Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationFactRepository factRepo = new UnavailableFactsRepository();
        AuthorizationCheckUseCase checkUseCase = new AuthorizationCheckUseCase(factRepo, engine, recordRepo);
        ExplainDecisionUseCase explainUseCase = new ExplainDecisionUseCase(recordRepo);
        AuthorizationCheckController controller =
                new AuthorizationCheckController(checkUseCase, explainUseCase);

        MockMvc mockMvc = standaloneMockMvcWithIamJson(controller);

        mockMvc.perform(get("/internal/v1/authorization/decisions/999/explain"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IAM_AUTHZ_DECISION_NOT_FOUND"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    static class UnavailableFactsRepository implements AuthorizationFactRepository {
        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            return AuthorizationFacts.unavailable(request);
        }
    }
}
