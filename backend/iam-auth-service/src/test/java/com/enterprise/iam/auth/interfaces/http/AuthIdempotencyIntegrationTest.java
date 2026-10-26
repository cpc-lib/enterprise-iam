package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.AuthApplication;
import com.enterprise.iam.auth.application.port.out.AuthenticationAttemptSink;
import com.enterprise.iam.auth.application.port.in.ChangePasswordUseCase;
import com.enterprise.iam.auth.application.port.out.CredentialRepository;
import com.enterprise.iam.auth.application.port.out.LoginIdentityDirectory;
import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.PasswordVerifier;
import com.enterprise.iam.auth.application.port.out.RefreshTokenRotator;
import com.enterprise.iam.auth.application.port.out.SessionRevoker;
import com.enterprise.iam.auth.application.service.AuthenticateLoginUseCase;
import com.enterprise.iam.common.security.delegation.TrustedRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC47 D-4：全上下文验证认证命令路由的 Idempotency-Key 强制。
 * 路径来自 application.yml 中冻结的 iam.idempotency.paths；
 * login/refresh 按 x-iam-idempotency-policy 刻意排除）。此处仅开启开关，
 * 通过 StubWebLayer 手工装配控制器（生产装配由 iam.auth.http.enabled 控制），
 * 全程无 MySQL/Redis 依赖。
 */
@SpringBootTest(
        classes = AuthApplication.class,
        properties = {
                "management.endpoints.access.default=none",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
@AutoConfigureMockMvc
@TestPropertySource(properties = "iam.idempotency.enabled=true")
@Import(AuthIdempotencyIntegrationTest.StubWebLayer.class)
class AuthIdempotencyIntegrationTest {

    private static final String LOGOUT_BODY_A = "{\"marker\":\"one\"}";
    private static final String LOGOUT_BODY_B = "{\"marker\":\"two\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChangePasswordUseCase changePasswordUseCase;

    @Test
    void missingIdempotencyKeyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-Tenant-Id", "1")
                        .header("X-Session-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGOUT_BODY_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void passwordChangeAlsoRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("X-Tenant-Id", "1")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "IAM_REFRESH", "rt1.key." + "a".repeat(43)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"current-password-value\","
                                + "\"newPassword\":\"replacement-password-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void passwordChangeReplaysEmptySuccessWithoutReprocessingCredentials() throws Exception {
        String body = "{\"currentPassword\":\"current-password-value\","
                + "\"newPassword\":\"replacement-password-value\"}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/password/change")
                            .header("Idempotency-Key", "test-password-change-key-0001")
                            .header("X-Tenant-Id", "1")
                            .requestAttr(
                                    TrustedRequestContext.class.getName(),
                                    trustedContext())
                            .cookie(new jakarta.servlet.http.Cookie(
                                    "IAM_REFRESH", "rt1.key." + "a".repeat(43)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        org.mockito.Mockito.verify(changePasswordUseCase).changePassword(any());
    }

    private static TrustedRequestContext trustedContext() {
        return new TrustedRequestContext(
                1L,
                11L,
                101L,
                "delegation-token-0001",
                "request-0001",
                Instant.parse("2026-10-11T23:59:59Z"));
    }

    @Test
    void missingTenantContextIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Idempotency-Key", "test-idempotency-key-0001")
                        .header("X-Session-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGOUT_BODY_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_TENANT_CONTEXT_REQUIRED"));
    }

    @Test
    void sameKeyWithDifferentBodyIsReplayConflict() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Idempotency-Key", "test-idempotency-key-0002")
                        .header("X-Tenant-Id", "1")
                        .header("X-Session-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGOUT_BODY_A))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Idempotency-Key", "test-idempotency-key-0002")
                        .header("X-Tenant-Id", "1")
                        .header("X-Session-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGOUT_BODY_B))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_REPLAY_CONFLICT"));
    }

    @TestConfiguration
    static class StubWebLayer {

        @Bean
        ChangePasswordUseCase changePasswordUseCase() {
            return mock(ChangePasswordUseCase.class);
        }

        @Bean
        AuthController authController(ChangePasswordUseCase changePasswordUseCase) {
            Clock clock = Clock.systemUTC();
            AuthenticateLoginUseCase authenticateLoginUseCase = new AuthenticateLoginUseCase(
                    mock(LoginIdentityDirectory.class),
                    mock(CredentialRepository.class),
                    mock(PasswordVerifier.class),
                    mock(LoginSessionIssuer.class),
                    mock(AuthenticationAttemptSink.class),
                    clock);
            return new AuthController(
                    authenticateLoginUseCase,
                    mock(RefreshTokenRotator.class),
                    mock(SessionRevoker.class),
                    mock(com.enterprise.iam.auth.application.port.out.LoginDeliveryConfirmer.class),
                    changePasswordUseCase);
        }
    }
}
