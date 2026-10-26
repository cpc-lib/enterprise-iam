package com.enterprise.iam.organization;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-4 幂等过滤器的全上下文（hermetic）强制测试：不依赖 MySQL/Redis，
 * 仓储端口用 Mockito 替身，DataSource 仅提供构造期 Bean。
 */
@SpringBootTest(classes = OrganizationApplication.class)
@AutoConfigureMockMvc
@Import(IdempotencyEnforcementTest.HermeticInfrastructureConfiguration.class)
@TestPropertySource(properties = {
        "iam.organization.enabled=true",
        "iam.idempotency.enabled=true",
        "management.endpoints.access.default=none",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class IdempotencyEnforcementTest {

    private static final String IDEMPOTENCY_KEY = "enforce-org-key-0000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void stubRepository() {
        Mockito.reset(organizationRepository);
        when(organizationRepository.findByCode(anyLong(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void missingIdempotencyKeyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/admin/v1/organizations")
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationBody("enforce-org-a", "Enforce Org A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void missingTenantContextIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/admin/v1/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationBody("enforce-org-a", "Enforce Org A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IAM_TENANT_CONTEXT_REQUIRED"));
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedWith409() throws Exception {
        mockMvc.perform(post("/admin/v1/organizations")
                        .header("X-Tenant-Id", "1")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationBody("enforce-org-a", "Enforce Org A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/admin/v1/organizations")
                        .header("X-Tenant-Id", "1")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationBody("enforce-org-b", "Enforce Org B")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IAM_IDEMPOTENCY_REPLAY_CONFLICT"));
    }

    private static String organizationBody(String orgCode, String orgName) {
        return "{\"orgCode\":\"" + orgCode + "\",\"orgName\":\"" + orgName + "\"}";
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
