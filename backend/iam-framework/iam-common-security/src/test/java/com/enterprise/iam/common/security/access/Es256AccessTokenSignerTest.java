package com.enterprise.iam.common.security.access;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Es256AccessTokenSignerTest {

    @Test
    void signerAndDecoderRoundTripVersionedAccessContext() throws Exception {
        Instant now = Instant.parse("2026-08-12T12:00:00Z");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        Es256AccessTokenSigner signer = new Es256AccessTokenSigner(
                (ECPrivateKey) pair.getPrivate(),
                "auth-key-1",
                "iam-auth-service",
                "iam-gateway",
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                () -> "access-jti-0001");
        SignedAccessToken signed = signer.sign(new AccessTokenSigningRequest(
                10, 20, 30, 4, 5));
        Es256AccessTokenDecoder decoder = new Es256AccessTokenDecoder(
                keyId -> "auth-key-1".equals(keyId)
                        ? Optional.of((ECPublicKey) pair.getPublic())
                        : Optional.empty(),
                new AccessTokenPolicy(
                        Clock.fixed(now, ZoneOffset.UTC),
                        "iam-auth-service",
                        "iam-gateway",
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(30)));

        AccessTokenValidationResult result = decoder.decode(signed.compact());

        assertThat(result.isValid()).isTrue();
        assertThat(signed.expiresInSeconds()).isEqualTo(300);
        assertThat(signed.toString()).doesNotContain(signed.compact());
        assertThat(result.verifiedToken()).get().satisfies(token -> {
            assertThat(token.tokenVersion()).isEqualTo(4);
            assertThat(token.sessionVersion()).isEqualTo(5);
            assertThat(token.expiresAt()).isEqualTo(now.plusSeconds(300));
        });
    }

    @Test
    void rejectsSubsecondLifetimeBeforeSigning() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        assertThatThrownBy(() -> new Es256AccessTokenSigner(
                (ECPrivateKey) pair.getPrivate(),
                "auth-key-1",
                "iam-auth-service",
                "iam-gateway",
                Clock.systemUTC(),
                Duration.ofMillis(500),
                () -> "access-jti-0002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole second");
    }
}
