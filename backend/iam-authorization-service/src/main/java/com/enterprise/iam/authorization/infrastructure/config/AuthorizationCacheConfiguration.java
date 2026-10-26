package com.enterprise.iam.authorization.infrastructure.config;

import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.infrastructure.cache.CaffeineAuthorizationPlanCache;
import com.enterprise.iam.authorization.infrastructure.cache.CachingAuthorizationFactRepository;
import com.enterprise.iam.authorization.infrastructure.cache.CaffeineAuthorizationPlanCache.DecisionCacheKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Optional L1/L2 Caffeine caches (spec 13). Both layers default OFF; L2 is a
 * {@link Primary} decorator around the JDBC fact repository, L1 is an
 * {@link AuthorizationPlanCache} consumed optionally by the check and replace
 * use cases.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class AuthorizationCacheConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "iam.authorization.cache.l2", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "cachedAuthorizationFactRepository")
    public CachingAuthorizationFactRepository cachedAuthorizationFactRepository(
            @Qualifier("authorizationFactRepository") AuthorizationFactRepository delegate,
            @Value("${iam.authorization.cache.l2.ttl:30s}") Duration ttl,
            @Value("${iam.authorization.cache.l2.max-size:50000}") int maxSize) {
        Cache<CachingAuthorizationFactRepository.FactCacheKey, AuthorizationFacts> cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maxSize)
                        .build();
        return new CachingAuthorizationFactRepository(delegate, cache);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationPlanCache.class)
    @ConditionalOnProperty(
            prefix = "iam.authorization.cache.l1", name = "enabled", havingValue = "true")
    public AuthorizationPlanCache caffeineAuthorizationPlanCache(
            @Value("${iam.authorization.cache.l1.ttl:2s}") Duration ttl,
            @Value("${iam.authorization.cache.l1.max-size:10000}") int maxSize,
            ObjectProvider<CachingAuthorizationFactRepository> factsCache) {
        Cache<DecisionCacheKey, com.enterprise.iam.authorization.domain.model.AuthorizationResult> cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maxSize)
                        .build();
        return new CaffeineAuthorizationPlanCache(cache, factsCache.getIfAvailable());
    }
}
