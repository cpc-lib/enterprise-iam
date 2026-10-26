package com.enterprise.iam.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotentRequestFilterTest {

    static final String KEY = "12345678901234567890";
    static final String TENANT = "42";

    @RestController
    static class TestController {
        final AtomicInteger creates = new AtomicInteger();

        @PostMapping("/admin/v1/widgets")
        public String create() {
            return "{\"n\":" + creates.incrementAndGet() + "}";
        }

        @PostMapping("/admin/v1/widgets/boom")
        public String fail() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/admin/v1/widgets")
        public String list() {
            return "[]";
        }
    }

    @RestControllerAdvice
    static class FailAdvice {
        @ExceptionHandler(IllegalStateException.class)
        ResponseEntity<String> boom(IllegalStateException e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private TestController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new TestController();
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setPaths(java.util.List.of("/admin/v1/widgets", "/admin/v1/widgets/**"));
        properties.setTtl(Duration.ofHours(1));
        IdempotentRequestFilter filter = new IdempotentRequestFilter(
                properties, new InMemoryReplayIdempotencyStore(properties.getTtl()), Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FailAdvice())
                .addFilters(filter)
                .build();
    }

    private MockHttpServletRequestBuilder command(String body) {
        return post("/admin/v1/widgets")
                .header("Idempotency-Key", KEY)
                .header("X-Tenant-Id", TENANT)
                .contentType("application/json")
                .content(body);
    }

    @Test
    void missing_key_is_rejected() throws Exception {
        mvc.perform(post("/admin/v1/widgets").header("X-Tenant-Id", TENANT).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IAM_IDEMPOTENCY_KEY_REQUIRED")));
    }

    @Test
    void too_short_key_is_rejected() throws Exception {
        mvc.perform(post("/admin/v1/widgets")
                        .header("Idempotency-Key", "short")
                        .header("X-Tenant-Id", TENANT).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IAM_IDEMPOTENCY_KEY_INVALID")));
    }

    @Test
    void missing_trusted_tenant_is_rejected() throws Exception {
        mvc.perform(post("/admin/v1/widgets").header("Idempotency-Key", KEY).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IAM_TENANT_CONTEXT_REQUIRED")));
    }

    @Test
    void identical_replay_returns_cached_response_without_reexecuting() throws Exception {
        mvc.perform(command("{\"a\":1}")).andExpect(status().isOk()).andExpect(content().string("{\"n\":1}"));
        mvc.perform(command("{\"a\":1}")).andExpect(status().isOk()).andExpect(content().string("{\"n\":1}"));
    }

    @Test
    void same_key_different_body_conflicts() throws Exception {
        mvc.perform(command("{\"a\":1}")).andExpect(status().isOk());
        mvc.perform(command("{\"a\":2}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IAM_IDEMPOTENCY_REPLAY_CONFLICT")));
    }

    @Test
    void failed_first_call_releases_claim_for_retry() throws Exception {
        mvc.perform(post("/admin/v1/widgets/boom")
                        .header("Idempotency-Key", KEY).header("X-Tenant-Id", TENANT).content("{}"))
                .andExpect(status().is5xxServerError());
        // 同一 key 同体重试应被允许（不返回 IN_FLIGHT 409）
        mvc.perform(command("{\"a\":1}")).andExpect(status().isOk());
    }

    @Test
    void get_and_unconfigured_paths_pass_through_without_key() throws Exception {
        mvc.perform(get("/admin/v1/widgets")).andExpect(status().isOk()).andExpect(content().string("[]"));
        mvc.perform(post("/admin/v1/other").content("{}")).andExpect(status().isNotFound());
    }
}
