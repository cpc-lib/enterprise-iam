package com.enterprise.iam.authorization.infrastructure.cache;

import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.github.benmanes.caffeine.cache.Cache;

import java.util.Objects;

/**
 * L2 Caffeine facts cache wrapping the JDBC fact repository. Unavailable
 * facts are deliberately not cached so transient dependency failures
 * self-heal immediately; tenant invalidation follows permission-version
 * bumps.
 */
public final class CachingAuthorizationFactRepository implements AuthorizationFactRepository {

    private final AuthorizationFactRepository delegate;
    private final Cache<FactCacheKey, AuthorizationFacts> cache;

    public CachingAuthorizationFactRepository(
            AuthorizationFactRepository delegate,
            Cache<FactCacheKey, AuthorizationFacts> cache) {
        this.delegate = Objects.requireNonNull(delegate);
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public AuthorizationFacts loadFacts(AuthorizationRequest request) {
        FactCacheKey key = FactCacheKey.from(request);
        AuthorizationFacts cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        AuthorizationFacts loaded = delegate.loadFacts(request);
        if (loaded.authoritativeAvailable()) {
            cache.put(key, loaded);
        }
        return loaded;
    }

    public void evictTenant(long tenantId) {
        cache.asMap().keySet().removeIf(key -> key.tenantId() == tenantId);
    }

    public record FactCacheKey(
            long tenantId,
            long subjectId,
            long sessionId,
            long resourceId,
            long operationId,
            String resourceInstanceId,
            long permissionVersion) {

        static FactCacheKey from(AuthorizationRequest request) {
            return new FactCacheKey(
                    request.tenantId(),
                    request.subjectId(),
                    request.sessionId(),
                    request.resourceId(),
                    request.operationId(),
                    request.resourceInstanceId(),
                    request.permissionVersion());
        }
    }
}
