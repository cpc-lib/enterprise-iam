package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.service.AuthorizationCheckUseCase;
import com.enterprise.iam.authorization.application.service.ExplainDecisionUseCase;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import com.enterprise.iam.authorization.infrastructure.persistence.InMemoryAuthorizationDecisionRecordRepository;
import com.enterprise.iam.common.web.json.IamWebJacksonAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BatchAuthorizationCheckControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(1000);
        DefaultAuthorizationEngine engine = new DefaultAuthorizationEngine(
                Clock.fixed(NOW, ZoneOffset.UTC), ids::incrementAndGet);
        AuthorizationFactRepository factRepository = new AllowingFactsRepository();
        AuthorizationDecisionRecordRepository recordRepository =
                new InMemoryAuthorizationDecisionRecordRepository();
        AuthorizationCheckUseCase checkUseCase =
                new AuthorizationCheckUseCase(factRepository, engine, recordRepository);
        AuthorizationCheckController controller =
                new AuthorizationCheckController(checkUseCase, new ExplainDecisionUseCase(recordRepository));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new IamWebJacksonAutoConfiguration().iamIdAsStringCustomizer().customize(builder);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build()))
                .build();
    }

    @Test
    void batchCheckPreservesOrderAndReturnsOneResultPerCheck() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "checks", List.of(checkJson("request-00001"), checkJson("request-00002"))));

        mockMvc.perform(post("/internal/v1/authorization/batch-check")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.results[0].reasonCode").value("IAM_AUTHZ_GRANT_ALLOW"))
                .andExpect(jsonPath("$.data.results[0].decisionId").isString())
                .andExpect(jsonPath("$.data.results[0].permissionVersion").value(11))
                .andExpect(jsonPath("$.data.results[0].matchedGrantIds[0]").value("50"))
                .andExpect(jsonPath("$.data.results[1].decision").value("ALLOW"));
    }

    @Test
    void batchCheckAcceptsExactly100Requests() throws Exception {
        List<Map<String, Object>> checks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            checks.add(checkJson("request-" + String.format("%05d", i)));
        }

        mockMvc.perform(post("/internal/v1/authorization/batch-check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("checks", checks))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(100));
    }

    @Test
    void batchCheckRejectsMoreThan100RequestsWith400() throws Exception {
        List<Map<String, Object>> checks = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            checks.add(checkJson("request-" + String.format("%05d", i)));
        }

        mockMvc.perform(post("/internal/v1/authorization/batch-check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("checks", checks))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchCheckRejectsInvalidNestedCheckWith400() throws Exception {
        Map<String, Object> invalid = checkJson("request-00003");
        invalid.put("tenantId", null);

        mockMvc.perform(post("/internal/v1/authorization/batch-check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("checks", List.of(checkJson("request-00001"), invalid)))))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> checkJson(String requestId) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("tenantId", 7);
        check.put("subjectId", 100);
        check.put("sessionId", 200);
        check.put("resourceId", 300);
        check.put("operationId", 400);
        check.put("resourceInstanceId", "invoice-42");
        check.put("permissionVersion", 11);
        check.put("requestId", requestId);
        check.put("context", Map.of());
        return check;
    }

    private static final class AllowingFactsRepository implements AuthorizationFactRepository {
        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            ResolvedGrant allow = new ResolvedGrant(
                    50L, 7L, 100L, 300L, 400L, GrantEffect.ALLOW, true,
                    NOW.minusSeconds(60), null);
            return new AuthorizationFacts(
                    true, 7, 300, 400, 11, true, List.of(allow));
        }
    }
}
