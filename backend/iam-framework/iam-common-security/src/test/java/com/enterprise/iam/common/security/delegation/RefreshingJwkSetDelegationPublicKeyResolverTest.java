package com.enterprise.iam.common.security.delegation;

import com.enterprise.iam.common.security.jwt.JwkSetKeyResolutionException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshingJwkSetDelegationPublicKeyResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private MutableClock clock;
    private ECKey keyOne;
    private ECKey keyTwo;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(NOW);
        keyOne = publicJwk("gateway-key-1");
        keyTwo = publicJwk("gateway-key-2");
    }

    @Test
    void cachesKnownKeyAndRefreshesOnceForRotatedUnknownKeyId() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> document = new AtomicReference<>(jwks(keyOne));
        var resolver = resolver(() -> {
            loads.incrementAndGet();
            return document.get();
        });

        assertThat(resolver.resolve("gateway-key-1")).isPresent();
        assertThat(resolver.resolve("gateway-key-1")).isPresent();
        assertThat(loads).hasValue(1);

        document.set(jwks(keyOne, keyTwo));
        clock.advance(Duration.ofSeconds(6));
        assertThat(resolver.resolve("gateway-key-2")).isPresent();
        assertThat(resolver.resolve("gateway-key-1")).isPresent();
        assertThat(loads).hasValue(2);
    }

    @Test
    void negativeCachesUnknownKeyToBoundAttackerTriggeredRefreshes() {
        AtomicInteger loads = new AtomicInteger();
        var resolver = resolver(() -> {
            loads.incrementAndGet();
            return jwks(keyOne);
        });

        assertThat(resolver.resolve("gateway-key-1")).isPresent();
        clock.advance(Duration.ofSeconds(6));
        assertThat(resolver.resolve("attacker-key")).isEmpty();
        assertThat(resolver.resolve("attacker-key")).isEmpty();
        assertThat(resolver.resolve("different-attacker-key")).isEmpty();
        assertThat(loads).hasValue(2);

        clock.advance(Duration.ofSeconds(31));
        assertThat(resolver.resolve("attacker-key")).isEmpty();
        assertThat(loads).hasValue(3);
    }

    @Test
    void retriesRotatedKeyAtRefreshCooldownInsteadOfFullNegativeTtl() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> document = new AtomicReference<>(jwks(keyOne));
        var resolver = resolver(() -> {
            loads.incrementAndGet();
            return document.get();
        });
        assertThat(resolver.resolve("gateway-key-1")).isPresent();

        document.set(jwks(keyOne, keyTwo));
        assertThat(resolver.resolve("gateway-key-2")).isEmpty();
        assertThat(loads).hasValue(1);

        clock.advance(Duration.ofSeconds(6));
        assertThat(resolver.resolve("gateway-key-2")).isPresent();
        assertThat(loads).hasValue(2);
    }

    @Test
    void expiredCacheDoesNotMaskJwksLoaderFailure() {
        AtomicReference<String> document = new AtomicReference<>(jwks(keyOne));
        var resolver = resolver(() -> {
            String value = document.get();
            if (value == null) {
                throw new IllegalStateException("JWKS endpoint unavailable");
            }
            return value;
        });
        assertThat(resolver.resolve("gateway-key-1")).isPresent();

        document.set(null);
        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> resolver.resolve("gateway-key-1"))
                .isInstanceOf(JwkSetKeyResolutionException.class)
                .hasMessageContaining("load failed");
    }

    @Test
    void boundsNegativeCacheUnderRandomKeyIdSpray() {
        AtomicInteger loads = new AtomicInteger();
        var resolver = resolver(() -> {
            loads.incrementAndGet();
            return jwks(keyOne);
        });
        assertThat(resolver.resolve("gateway-key-1")).isPresent();
        clock.advance(Duration.ofSeconds(6));

        for (int index = 0;
                index <= RefreshingJwkSetDelegationPublicKeyResolver.MAX_NEGATIVE_KEY_COUNT;
                index++) {
            assertThat(resolver.resolve("unknown-key-" + index)).isEmpty();
        }
        assertThat(loads).hasValue(2);

        clock.advance(Duration.ofSeconds(6));
        assertThat(resolver.resolve("unknown-key-0")).isEmpty();
        assertThat(loads).hasValue(2);
        assertThat(resolver.resolve("unknown-key-1024")).isEmpty();
        assertThat(loads).hasValue(3);
    }

    @Test
    void rejectsDuplicateUsableKeyIdsAndNonEs256OnlySets() throws Exception {
        String duplicate = "{\"keys\":[" + keyOne.toJSONString() + ","
                + keyOne.toJSONString() + "]}";
        var duplicateResolver = resolver(() -> duplicate);
        assertThatThrownBy(() -> duplicateResolver.resolve("gateway-key-1"))
                .isInstanceOf(JwkSetKeyResolutionException.class)
                .hasMessageContaining("duplicate");

        ECKey wrongAlgorithm = new ECKey.Builder(
                Curve.P_256, (ECPublicKey) generateKeyPair().getPublic())
                .keyID("gateway-key-3")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES384)
                .keyOperations(Set.of(KeyOperation.VERIFY))
                .build();
        var wrongAlgorithmResolver = resolver(() -> jwks(wrongAlgorithm));
        assertThatThrownBy(() -> wrongAlgorithmResolver.resolve("gateway-key-3"))
                .isInstanceOf(JwkSetKeyResolutionException.class)
                .hasMessageContaining("no usable ES256");

        KeyPair privatePair = generateKeyPair();
        ECKey privateKey = new ECKey.Builder(
                Curve.P_256, (ECPublicKey) privatePair.getPublic())
                .privateKey((ECPrivateKey) privatePair.getPrivate())
                .keyID("gateway-key-private")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256)
                .build();
        var privateKeyResolver = resolver(() ->
                "{\"keys\":[" + privateKey.toJSONString() + "]}");
        assertThatThrownBy(() -> privateKeyResolver.resolve("gateway-key-private"))
                .isInstanceOf(JwkSetKeyResolutionException.class)
                .hasMessageContaining("no usable ES256");
    }

    @Test
    void rejectsOversizedJwksBeforeParsing() {
        var resolver = resolver(() -> "x".repeat(
                RefreshingJwkSetDelegationPublicKeyResolver.MAX_JWKS_BYTES + 1));

        assertThatThrownBy(() -> resolver.resolve("gateway-key-1"))
                .isInstanceOf(JwkSetKeyResolutionException.class)
                .hasMessageContaining("size");
    }

    private RefreshingJwkSetDelegationPublicKeyResolver resolver(DelegationJwkSetLoader loader) {
        return new RefreshingJwkSetDelegationPublicKeyResolver(
                loader, clock, Duration.ofMinutes(5), Duration.ofSeconds(30));
    }

    private static ECKey publicJwk(String keyId) throws Exception {
        return new ECKey.Builder(Curve.P_256, (ECPublicKey) generateKeyPair().getPublic())
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256)
                .keyOperations(Set.of(KeyOperation.VERIFY))
                .build();
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String jwks(JWK... keys) {
        return new JWKSet(List.of(keys)).toString();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock supports UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
