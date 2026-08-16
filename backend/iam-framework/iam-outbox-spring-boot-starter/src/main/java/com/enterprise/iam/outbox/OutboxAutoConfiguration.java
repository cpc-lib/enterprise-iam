package com.enterprise.iam.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@AutoConfiguration(after = {
        JdbcTemplateAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
})
@ConditionalOnClass({JdbcTemplate.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(OutboxRelayProperties.class)
@Import(OutboxRelaySchedulingConfiguration.class)
public class OutboxAutoConfiguration {

    @Bean("iamOutboxClock")
    @ConditionalOnMissingBean(name = "iamOutboxClock")
    Clock iamOutboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean(OutboxWriter.class)
    OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate) {
        return new JdbcOutboxWriter(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean(OutboxRepository.class)
    OutboxRepository outboxRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return new JdbcOutboxRepository(jdbcTemplate, transactionManager);
    }
}
