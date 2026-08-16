package com.enterprise.iam.common.security.access;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Es256AccessTokenDecoderTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private ECPrivateKey privateKey;
    private ECPublicKey publicKey;
    private Es256AccessTokenDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = (ECPrivateKey) keyPair.getPrivate();
        publicKey = (ECPublicKey) keyPair.getPublic();
        decoder = decoder(keyId -> "auth-key-1".equals(keyId)
                ? Optional.of(publicKey)
                : Optional.empty());
    }

    @Test
    void verifiesStrictAtJwtAndPublishesVersionedIdentity() throws Exception {
        AccessTokenValidationResult result = decoder.decode(token(
                List.of("iam-gateway"), "iam-auth-service",
                10, 20, 30, 4, 5,
                NOW, NOW, NOW.plusSeconds(300)));

        assertThat(result.isValid()).isTrue();
        assertThat(result.verifiedToken()).get().satisfies(token -> {
            assertThat(token.tenantId()).isEqualTo(10);
            assertThat(token.subjectId()).isEqualTo(20);
            assertThat(token.sessionId()).isEqualTo(30);
            assertThat(token.tokenVersion()).isEqualTo(4);
            assertThat(token.sessionVersion()).isEqualTo(5);
            assertThat(token.tokenId()).isEqualTo("access-jti-0001");
        });
    }

    @Test
    void rejectsWrongOrMultipleAudienceAndFutureIssuedAt() throws Exception {
        assertFailure(token(
                List.of("iam-file-service"), "iam-auth-service",
                10, 20, 30, 1, 1, NOW, NOW, NOW.plusSeconds(300)),
                AccessTokenValidationFailure.INVALID_AUDIENCE);
        assertFailure(token(
                List.of("iam-gateway", "iam-file-service"), "iam-auth-service",
                10, 20, 30, 1, 1, NOW, NOW, NOW.plusSeconds(300)),
                AccessTokenValidationFailure.INVALID_AUDIENCE);
        assertFailure(token(
                List.of("iam-gateway"), "iam-auth-service",
                10, 20, 30, 1, 1,
                NOW.plusSeconds(31), NOW.plusSeconds(31), NOW.plusSeconds(300)),
                AccessTokenValidationFailure.INVALID_TIME_CLAIMS);
    }

    @Test
    void rejectsFractionalVersionClaimWithoutTruncation() throws Exception {
        JWTClaimsSet claims = validClaims()
                .claim("tver", 4.5)
                .build();
        SignedJWT token = signedToken(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType(AccessTokenPolicy.REQUIRED_TYPE))
                        .keyID("auth-key-1")
                        .build(),
                claims);
        token.sign(new ECDSASigner(privateKey));

        assertFailure(token.serialize(), AccessTokenValidationFailure.MALFORMED_TOKEN);
    }

    @Test
    void rejectsAlgorithmConfusionBeforeKeyResolution() throws Exception {
        AtomicInteger keyLookups = new AtomicInteger();
        Es256AccessTokenDecoder guarded = decoder(keyId -> {
            keyLookups.incrementAndGet();
            return Optional.of(publicKey);
        });
        SignedJWT token = signedToken(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .type(new JOSEObjectType(AccessTokenPolicy.REQUIRED_TYPE))
                        .keyID("auth-key-1")
                        .build(),
                validClaims().build());
        token.sign(new MACSigner(new byte[32]));

        assertThat(guarded.decode(token.serialize()).failure())
                .isEqualTo(AccessTokenValidationFailure.ALGORITHM_NOT_ALLOWED);
        assertThat(keyLookups).hasValue(0);
    }

    @Test
    void distinguishesUnknownKeyFromKeyDependencyOutage() throws Exception {
        String compact = token(
                List.of("iam-gateway"), "iam-auth-service",
                10, 20, 30, 1, 1, NOW, NOW, NOW.plusSeconds(300));
        assertThat(decoder(keyId -> Optional.empty()).decode(compact).failure())
                .isEqualTo(AccessTokenValidationFailure.UNKNOWN_KEY_ID);
        assertThat(decoder(keyId -> {
            throw new IllegalStateException("JWKS unavailable");
        }).decode(compact).failure())
                .isEqualTo(AccessTokenValidationFailure.KEY_RESOLUTION_UNAVAILABLE);
    }

    @Test
    void rejectsOversizedAndTamperedToken() throws Exception {
        assertThat(decoder.decode("x".repeat(
                Es256AccessTokenDecoder.MAX_COMPACT_TOKEN_LENGTH + 1)).failure())
                .isEqualTo(AccessTokenValidationFailure.TOKEN_TOO_LARGE);
        String compact = token(
                List.of("iam-gateway"), "iam-auth-service",
                10, 20, 30, 1, 1, NOW, NOW, NOW.plusSeconds(300));
        int signatureStart = compact.lastIndexOf('.') + 1;
        char replacement = compact.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = compact.substring(0, signatureStart)
                + replacement
                + compact.substring(signatureStart + 1);
        assertFailure(tampered, AccessTokenValidationFailure.INVALID_SIGNATURE);
    }

    @Test
    void policyItselfRejectsUnsafeTimeBoundsAndServiceNames() {
        assertThatThrownBy(() -> new AccessTokenPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC),
                "https://unfrozen-issuer.example",
                "iam-gateway",
                Duration.ofMinutes(5),
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedIssuer");
        assertThatThrownBy(() -> new AccessTokenPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC),
                "iam-auth-service",
                "iam-gateway",
                Duration.ofMinutes(6),
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumTtl");
        assertThatThrownBy(() -> new AccessTokenPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC),
                "iam-auth-service",
                "iam-gateway",
                Duration.ofMinutes(5),
                Duration.ofSeconds(31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clockSkew");
    }

    private String token(
            List<String> audience,
            String issuer,
            long tenantId,
            long subjectId,
            long sessionId,
            long tokenVersion,
            long sessionVersion,
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt) throws Exception {
        JWTClaimsSet claims = validClaims()
                .issuer(issuer)
                .audience(audience)
                .subject(Long.toString(subjectId))
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(notBefore))
                .expirationTime(Date.from(expiresAt))
                .claim("tid", tenantId)
                .claim("sid", sessionId)
                .claim("tver", tokenVersion)
                .claim("sver", sessionVersion)
                .build();
        SignedJWT token = signedToken(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType(AccessTokenPolicy.REQUIRED_TYPE))
                        .keyID("auth-key-1")
                        .build(),
                claims);
        token.sign(new ECDSASigner(privateKey));
        return token.serialize();
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("iam-auth-service")
                .audience("iam-gateway")
                .subject("20")
                .jwtID("access-jti-0001")
                .issueTime(Date.from(NOW))
                .notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .claim("tid", 10)
                .claim("sid", 30)
                .claim("tver", 4)
                .claim("sver", 5);
    }

    private static SignedJWT signedToken(JWSHeader header, JWTClaimsSet claims) {
        return new SignedJWT(header, claims);
    }

    private Es256AccessTokenDecoder decoder(AccessTokenPublicKeyResolver resolver) {
        return new Es256AccessTokenDecoder(
                resolver,
                new AccessTokenPolicy(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        "iam-auth-service",
                        "iam-gateway",
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(30)));
    }

    private void assertFailure(String compact, AccessTokenValidationFailure failure) {
        AccessTokenValidationResult result = decoder.decode(compact);
        assertThat(result.isValid()).isFalse();
        assertThat(result.failure()).isEqualTo(failure);
        assertThat(result.verifiedToken()).isEmpty();
    }
}
