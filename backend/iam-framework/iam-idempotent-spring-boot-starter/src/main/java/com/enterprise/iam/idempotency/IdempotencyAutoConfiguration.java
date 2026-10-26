package com.enterprise.iam.idempotency;

import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * 幂等过滤器自动装配。{@code iam.idempotency.enabled=true} 时生效，
 * 受保护路径由 {@code iam.idempotency.paths} 显式声明（Ant 模式）。
 */
@AutoConfiguration
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnProperty(prefix = "iam.idempotency", name = "enabled", havingValue = "true")
public class IdempotencyAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iam.idempotency", name = "store", havingValue = "jdbc")
    @MapperScan(basePackageClasses = IdempotencyPersistenceMapper.class)
    static class JdbcMapperConfiguration {
    }

    /**
     * Redis store wiring is isolated so that the optional
     * spring-data-redis classes are only introspected when present
     * (class-level condition is evaluated from ASM metadata).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "iam.idempotency", name = "store", havingValue = "redis")
    static class RedisStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(ReplayIdempotencyStore.class)
        ReplayIdempotencyStore redisReplayIdempotencyStore(
                StringRedisTemplate redisTemplate,
                IdempotencyProperties properties) {
            return new RedisReplayIdempotencyStore(redisTemplate, properties.getTtl());
        }
    }

    @Bean
    @ConditionalOnMissingBean(ReplayIdempotencyStore.class)
    @ConditionalOnProperty(prefix = "iam.idempotency", name = "store",
            havingValue = "memory", matchIfMissing = true)
    public ReplayIdempotencyStore inMemoryReplayIdempotencyStore(IdempotencyProperties properties) {
        return new InMemoryReplayIdempotencyStore(properties.getTtl());
    }

    @Bean
    @ConditionalOnMissingBean(ReplayIdempotencyStore.class)
    @ConditionalOnProperty(prefix = "iam.idempotency", name = "store", havingValue = "jdbc")
    public ReplayIdempotencyStore jdbcReplayIdempotencyStore(
            IdempotencyPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            IdempotencyProperties properties) {
        return new JdbcReplayIdempotencyStore(
                mapper, transactionManager, properties.getTtl());
    }

    @Bean
    public FilterRegistrationBean<IdempotentRequestFilter> idempotencyFilterRegistration(
            IdempotencyProperties properties, ReplayIdempotencyStore store) {
        IdempotentRequestFilter filter =
                new IdempotentRequestFilter(properties, store, Clock.systemUTC());
        FilterRegistrationBean<IdempotentRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        registration.setName("idempotencyFilter");
        return registration;
    }
}
