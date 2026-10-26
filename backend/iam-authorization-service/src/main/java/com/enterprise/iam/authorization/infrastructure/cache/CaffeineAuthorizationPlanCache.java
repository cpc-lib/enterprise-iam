package com.enterprise.iam.authorization.infrastructure.cache;

import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.github.benmanes.caffeine.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * L1 Caffeine decision cache. The key is the fully normalized request
 * (including permissionVersion and teamIds context), so a permission-version
 * bump or membership change can never be served a stale plan. Tenant
 * invalidation scans the bounded key set and removes matching entries.
 */
public final class CaffeineAuthorizationPlanCache implements AuthorizationPlanCache {

    private final Cache<DecisionCacheKey, AuthorizationResult> cache;
    private final CachingAuthorizationFactRepository factsCache;

    public CaffeineAuthorizationPlanCache(Cache<DecisionCacheKey, AuthorizationResult> cache) {
        this(cache, null);
    }

    public CaffeineAuthorizationPlanCache(
            Cache<DecisionCacheKey, AuthorizationResult> cache,
            CachingAuthorizationFactRepository factsCache) {
        this.cache = Objects.requireNonNull(cache);
        this.factsCache = factsCache;
    }

    @Override
    public Optional<AuthorizationResult> cachedDecision(AuthorizationRequest request) {
        return Optional.ofNullable(cache.getIfPresent(DecisionCacheKey.from(request)));
    }

    @Override
    public void storeDecision(AuthorizationRequest request, AuthorizationResult result) {
        cache.put(DecisionCacheKey.from(request), result);
    }

    @Override
    public void evictTenant(long tenantId) {
        cache.asMap().keySet().removeIf(key -> key.tenantId() == tenantId);
        if (factsCache != null) {
            factsCache.evictTenant(tenantId);
        }
    }

    public record DecisionCacheKey(
            long tenantId,
            long subjectId,
            long sessionId,
            long resourceId,
            long operationId,
            String resourceInstanceId,
            long permissionVersion,
            Map<String, Object> context) {

        static DecisionCacheKey from(AuthorizationRequest request) {
            Map<String, Object> normalizedContext = new LinkedHashMap<>(request.context());
            return new DecisionCacheKey(
                    request.tenantId(),
                    request.subjectId(),
                    request.sessionId(),
                    request.resourceId(),
                    request.operationId(),
                    request.resourceInstanceId(),
                    request.permissionVersion(),
                    normalizedContext);
        }
    }
}
