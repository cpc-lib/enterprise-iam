package com.enterprise.iam.common.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Transport-neutral, bounded and rotation-aware JWKS cache shared by access
 * and delegation token profiles.
 */
public class BoundedRefreshingJwkSetPublicKeyResolver
        implements Es256PublicKeyResolver {

    public static final int MAX_JWKS_BYTES = 65_536;
    public static final int MAX_JWK_COUNT = 32;
    public static final int MAX_NEGATIVE_KEY_COUNT = 1_024;
    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final JwkSetLoader loader;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Duration unknownKeyTtl;
    private final Duration unknownRefreshMinimumInterval;
    private final Object refreshMonitor = new Object();
    private final ConcurrentHashMap<String, Instant> unknownKeyUntil = new ConcurrentHashMap<>();
    private volatile CachedKeys cache;
    private volatile Instant lastRefreshAt;

    public BoundedRefreshingJwkSetPublicKeyResolver(
            JwkSetLoader loader,
            Clock clock,
            Duration cacheTtl,
            Duration unknownKeyTtl) {
        this(loader, clock, cacheTtl, unknownKeyTtl, Duration.ofSeconds(5));
    }

    public BoundedRefreshingJwkSetPublicKeyResolver(
            JwkSetLoader loader,
            Clock clock,
            Duration cacheTtl,
            Duration unknownKeyTtl,
            Duration unknownRefreshMinimumInterval) {
        this.loader = Objects.requireNonNull(loader, "loader must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cacheTtl = requireDuration(cacheTtl, "cacheTtl", Duration.ofMinutes(10));
        this.unknownKeyTtl = requireDuration(
                unknownKeyTtl, "unknownKeyTtl", Duration.ofMinutes(1));
        if (unknownKeyTtl.compareTo(cacheTtl) > 0) {
            throw new IllegalArgumentException("unknownKeyTtl must not exceed cacheTtl");
        }
        this.unknownRefreshMinimumInterval = requireDuration(
                unknownRefreshMinimumInterval,
                "unknownRefreshMinimumInterval",
                Duration.ofMinutes(1));
        if (unknownRefreshMinimumInterval.compareTo(unknownKeyTtl) > 0) {
            throw new IllegalArgumentException(
                    "unknownRefreshMinimumInterval must not exceed unknownKeyTtl");
        }
    }

    @Override
    public Optional<ECPublicKey> resolve(String keyId) {
        String normalizedKeyId = requireKeyId(keyId);
        Instant now = clock.instant();
        CachedKeys observed = cache;

        if (observed == null || !now.isBefore(observed.expiresAt())) {
            observed = refreshIfStillRequired(observed, false);
        }
        ECPublicKey key = observed.keys().get(normalizedKeyId);
        if (key != null) {
            unknownKeyUntil.remove(normalizedKeyId);
            return Optional.of(key);
        }
        Instant negativeExpiry = unknownKeyUntil.get(normalizedKeyId);
        if (negativeExpiry != null && now.isBefore(negativeExpiry)) {
            return Optional.empty();
        }

        CachedKeys refreshed = refreshIfStillRequired(observed, true);
        key = refreshed.keys().get(normalizedKeyId);
        if (key == null) {
            cacheUnknownKey(normalizedKeyId, observed, refreshed);
            return Optional.empty();
        }
        unknownKeyUntil.remove(normalizedKeyId);
        return Optional.of(key);
    }

    private void cacheUnknownKey(
            String keyId,
            CachedKeys observed,
            CachedKeys refreshed) {
        Instant checkedAt = clock.instant();
        Instant negativeExpiry = checkedAt.plus(unknownKeyTtl);
        if (refreshed == observed && lastRefreshAt != null) {
            Instant nextRefreshAllowedAt = lastRefreshAt.plus(
                    unknownRefreshMinimumInterval);
            if (nextRefreshAllowedAt.isBefore(negativeExpiry)) {
                negativeExpiry = nextRefreshAllowedAt;
            }
        }
        if (!negativeExpiry.isAfter(checkedAt)) {
            return;
        }
        synchronized (refreshMonitor) {
            unknownKeyUntil.entrySet().removeIf(
                    entry -> !checkedAt.isBefore(entry.getValue()));
            if (unknownKeyUntil.containsKey(keyId)
                    || unknownKeyUntil.size() < MAX_NEGATIVE_KEY_COUNT) {
                unknownKeyUntil.put(keyId, negativeExpiry);
            }
        }
    }

    private CachedKeys refreshIfStillRequired(CachedKeys observed, boolean forceUnknownRefresh) {
        synchronized (refreshMonitor) {
            Instant now = clock.instant();
            CachedKeys current = cache;
            boolean expired = current == null || !now.isBefore(current.expiresAt());
            boolean anotherThreadRefreshed = current != null && current != observed;
            if (!expired && (!forceUnknownRefresh || anotherThreadRefreshed)) {
                return current;
            }
            if (!expired
                    && forceUnknownRefresh
                    && lastRefreshAt != null
                    && now.isBefore(lastRefreshAt.plus(unknownRefreshMinimumInterval))) {
                return current;
            }
            CachedKeys refreshed = loadKeys(now);
            cache = refreshed;
            Instant completedAt = clock.instant();
            lastRefreshAt = completedAt;
            unknownKeyUntil.entrySet().removeIf(
                    entry -> !completedAt.isBefore(entry.getValue()));
            return refreshed;
        }
    }

    private CachedKeys loadKeys(Instant loadedAt) {
        final String json;
        try {
            json = Objects.requireNonNull(loader.load(), "JWKS loader returned null");
        } catch (RuntimeException exception) {
            throw new JwkSetKeyResolutionException("JWKS load failed", exception);
        }
        if (json.isBlank()
                || json.getBytes(StandardCharsets.UTF_8).length > MAX_JWKS_BYTES) {
            throw new JwkSetKeyResolutionException("JWKS size is invalid");
        }

        final JWKSet jwkSet;
        try {
            jwkSet = JWKSet.parse(json);
        } catch (ParseException | RuntimeException exception) {
            throw new JwkSetKeyResolutionException("JWKS is malformed", exception);
        }
        if (jwkSet.getKeys().isEmpty() || jwkSet.getKeys().size() > MAX_JWK_COUNT) {
            throw new JwkSetKeyResolutionException("JWKS key count is invalid");
        }

        Map<String, ECPublicKey> keys = new HashMap<>();
        for (JWK jwk : jwkSet.getKeys()) {
            if (!(jwk instanceof ECKey ecKey) || !isUsableVerificationKey(ecKey)) {
                continue;
            }
            String keyId = requireKeyId(ecKey.getKeyID());
            try {
                ECPublicKey key = ecKey.toECPublicKey();
                if (!P256Keys.isP256(key)) {
                    throw new JwkSetKeyResolutionException("JWKS contains a non-P-256 key");
                }
                ECPublicKey previous = keys.putIfAbsent(keyId, key);
                if (previous != null) {
                    throw new JwkSetKeyResolutionException(
                            "JWKS contains duplicate usable key ID");
                }
            } catch (JOSEException exception) {
                throw new JwkSetKeyResolutionException(
                        "JWKS contains an invalid EC key", exception);
            }
        }
        if (keys.isEmpty()) {
            throw new JwkSetKeyResolutionException(
                    "JWKS has no usable ES256 verification key");
        }
        return new CachedKeys(Map.copyOf(keys), loadedAt.plus(cacheTtl));
    }

    private static boolean isUsableVerificationKey(ECKey key) {
        return Curve.P_256.equals(key.getCurve())
                && KeyUse.SIGNATURE.equals(key.getKeyUse())
                && JWSAlgorithm.ES256.equals(key.getAlgorithm())
                && !key.isPrivate()
                && (key.getKeyOperations() == null
                    || key.getKeyOperations().contains(KeyOperation.VERIFY));
    }

    private static String requireKeyId(String value) {
        if (value == null || !KEY_ID.matcher(value).matches()) {
            throw new JwkSetKeyResolutionException("JWKS key ID format is invalid");
        }
        return value;
    }

    private static Duration requireDuration(
            Duration value,
            String name,
            Duration maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
        return value;
    }

    private record CachedKeys(Map<String, ECPublicKey> keys, Instant expiresAt) {
    }
}
