package com.enterprise.iam.authorization;

import com.enterprise.iam.authorization.application.port.out.DataScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-4 幂等过滤器的全上下文（hermetic）强制测试：不依赖 MySQL/RabbitMQ/Redis，
 * 仓储端口用 Mockito 替身，DataSource 仅提供构造期 Bean。
 */
@SpringBootTest(classes = AuthorizationApplication.class)
@AutoConfigureMockMvc
@Import(IdempotencyEnforcementTest.HermeticInfrastructureConfiguration.class)
@TestPropertySource(properties = {
        "iam.authorization.enabled=true",
        "iam.idempotency.enabled=true",
        "management.endpoints.access.default=none",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class IdempotencyEnforcementTest {

    private static final String IDEMPOTENCY_KEY = "enforce-authz-key-000000000001";
    private static final long CREATED_DATA_SCOPE_ID = 9001000001L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataScopeRepository dataScopeRepository;

    @BeforeEach
    void stubRepository() {
        Mockito.reset(dataScopeRepository);
        when(dataScopeRepository.create(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(CREATED_DATA_SCOPE_ID);
    }

    @Test
    void missingIdempotencyKeyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/admin/v1/data-scopes")
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataScopeBody("ENFORCE_SCOPE_A", "Enforce Scope A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void missingTenantContextIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/admin/v1/data-scopes")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataScopeBody("ENFORCE_SCOPE_A", "Enforce Scope A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_TENANT_CONTEXT_REQUIRED"));
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedWith409() throws Exception {
        mockMvc.perform(post("/admin/v1/data-scopes")
                        .header("X-Tenant-Id", "1")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataScopeBody("ENFORCE_SCOPE_A", "Enforce Scope A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("9001000001"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/admin/v1/data-scopes")
                        .header("X-Tenant-Id", "1")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataScopeBody("ENFORCE_SCOPE_B", "Enforce Scope B")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_REPLAY_CONFLICT"));
    }

    private static String dataScopeBody(String scopeCode, String scopeName) {
        return "{\"scopeCode\":\"" + scopeCode + "\",\"scopeName\":\"" + scopeName
                + "\",\"scopeType\":\"ALL\"}";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HermeticInfrastructureConfiguration {

        @Bean
        DataSource hermeticDataSource() {
            return Mockito.mock(DataSource.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
