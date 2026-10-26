package com.enterprise.iam.outbox;

import com.enterprise.iam.outbox.mapper.OutboxPersistenceMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@AutoConfiguration(
        after = DataSourceTransactionManagerAutoConfiguration.class,
        afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass({SqlSessionFactory.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(OutboxRelayProperties.class)
@Import(OutboxRelaySchedulingConfiguration.class)
public class OutboxAutoConfiguration {

    @Bean("iamOutboxClock")
    @ConditionalOnMissingBean(name = "iamOutboxClock")
    Clock iamOutboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean(SqlSessionTemplate.class)
    @ConditionalOnMissingBean(OutboxPersistenceMapper.class)
    OutboxPersistenceMapper outboxPersistenceMapper(SqlSessionTemplate sqlSessionTemplate) {
        return sqlSessionTemplate.getMapper(OutboxPersistenceMapper.class);
    }

    @Bean
    @ConditionalOnBean({OutboxPersistenceMapper.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean(OutboxWriter.class)
    OutboxWriter outboxWriter(OutboxPersistenceMapper mapper) {
        return new JdbcOutboxWriter(mapper);
    }

    @Bean
    @ConditionalOnBean({OutboxPersistenceMapper.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean(OutboxRepository.class)
    OutboxRepository outboxRepository(
            OutboxPersistenceMapper mapper,
            PlatformTransactionManager transactionManager) {
        return new JdbcOutboxRepository(mapper, transactionManager);
    }
}
