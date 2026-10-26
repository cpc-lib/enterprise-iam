package com.enterprise.iam.authorization.infrastructure.cache;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.service.AuthorizationCheckUseCase;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import com.enterprise.iam.authorization.infrastructure.cache.CaffeineAuthorizationPlanCache.DecisionCacheKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void l1ServesSecondIdenticalDecisionWithoutReloadingFacts() {
        AtomicInteger factLoads = new AtomicInteger();
        AtomicInteger records = new AtomicInteger();
        AuthorizationCheckUseCase useCase = useCase(factLoads, records, planCache(), null);
        AuthorizationRequest request = request(11);

        AuthorizationResult first = useCase.check(request);
        AuthorizationResult second = useCase.check(request);

        assertThat(factLoads).hasValue(1);
        assertThat(records).hasValue(1);
        assertThat(second.decisionId()).isEqualTo(first.decisionId());
    }

    @Test
    void l1MissesAcrossPermissionVersionsAndDoesNotCacheDenials() {
        AtomicInteger factLoads = new AtomicInteger();
        AuthorizationCheckUseCase useCase = useCase(factLoads, new AtomicInteger(), planCache(), null);

        useCase.check(request(11));
        useCase.check(request(12));
        int loadsAfterTwoVersions = factLoads.get();

        AuthorizationRequest stale = request(10);
        useCase.check(stale);
        useCase.check(stale);

        assertThat(loadsAfterTwoVersions).isEqualTo(2);
        // STALE_PERMISSION_VERSION is a deny: re-evaluated every time.
        assertThat(factLoads).hasValue(4);
    }

    @Test
    void l2CachesFactsAndReloadsAfterTenantEviction() {
        AtomicInteger delegateLoads = new AtomicInteger();
        AuthorizationFactRepository delegate = new CountingFactsRepository(delegateLoads);
        Cache<CachingAuthorizationFactRepository.FactCacheKey, AuthorizationFacts> cache =
                Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(100).build();
        CachingAuthorizationFactRepository l2 =
                new CachingAuthorizationFactRepository(delegate, cache);
        Cache<DecisionCacheKey, AuthorizationResult> l1Cache =
                Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(2)).maximumSize(100).build();
        AuthorizationPlanCache l1 = new CaffeineAuthorizationPlanCache(l1Cache, l2);
        AuthorizationRequest request = request(11);

        l2.loadFacts(request);
        l2.loadFacts(request);
        assertThat(delegateLoads).hasValue(1);

        l1.evictTenant(7);
        l2.loadFacts(request);
        assertThat(delegateLoads).hasValue(2);

        // Other tenants stay cached.
        l1.evictTenant(999);
        l2.loadFacts(request);
        assertThat(delegateLoads).hasValue(2);
    }

    @Test
    void unavailableFactsAreNotCachedByL2() {
        AtomicInteger delegateLoads = new AtomicInteger();
        AuthorizationFactRepository delegate = request -> {
            delegateLoads.incrementAndGet();
            return AuthorizationFacts.unavailable(request);
        };
        Cache<CachingAuthorizationFactRepository.FactCacheKey, AuthorizationFacts> cache =
                Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build();
        CachingAuthorizationFactRepository l2 =
                new CachingAuthorizationFactRepository(delegate, cache);

        AuthorizationRequest request = request(11);
        l2.loadFacts(request);
        l2.loadFacts(request);

        assertThat(delegateLoads).hasValue(2);
    }

    private AuthorizationPlanCache planCache() {
        Cache<DecisionCacheKey, AuthorizationResult> cache =
                Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(2)).maximumSize(100).build();
        return new CaffeineAuthorizationPlanCache(cache);
    }

    private AuthorizationCheckUseCase useCase(
            AtomicInteger factLoads,
            AtomicInteger records,
            AuthorizationPlanCache planCache,
            com.enterprise.iam.authorization.application.port.out.ShareAclProjectionRepository shareRepo) {
        AuthorizationFactRepository factRepository = new CountingFactsRepository(factLoads);
        AuthorizationDecisionRecordRepository recordRepository =
                new AuthorizationDecisionRecordRepository() {
                    @Override
                    public java.util.Optional<AuthorizationResult> findById(long decisionId) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void record(AuthorizationResult result) {
                        records.incrementAndGet();
                    }
                };
        DefaultAuthorizationEngine engine = new DefaultAuthorizationEngine(
                Clock.fixed(NOW, ZoneOffset.UTC), new AtomicLong(1)::incrementAndGet);
        return new AuthorizationCheckUseCase(
                factRepository, engine, recordRepository, shareRepo,
                Clock.fixed(NOW, ZoneOffset.UTC), planCache);
    }

    private AuthorizationRequest request(long permissionVersion) {
        return new AuthorizationRequest(
                7, 100, 200, 300, 400, "invoice-42",
                permissionVersion, "request-" + permissionVersion, Map.of());
    }

    private static final class CountingFactsRepository implements AuthorizationFactRepository {
        private final AtomicInteger factLoads;

        private CountingFactsRepository(AtomicInteger factLoads) {
            this.factLoads = factLoads;
        }

        @Override
        public AuthorizationFacts loadFacts(AuthorizationRequest request) {
            factLoads.incrementAndGet();
            ResolvedGrant allow = new ResolvedGrant(
                    50L, 7L, 100L, 300L, 400L, GrantEffect.ALLOW, true,
                    NOW.minusSeconds(60), null);
            return new AuthorizationFacts(
                    true, 7, 300, 400, 11, true, List.of(allow));
        }
    }
}
