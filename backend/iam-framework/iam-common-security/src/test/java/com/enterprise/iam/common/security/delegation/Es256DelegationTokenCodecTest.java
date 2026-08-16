package com.enterprise.iam.common.security.delegation;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Es256DelegationTokenCodecTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private ECPrivateKey privateKey;
    private ECPublicKey publicKey;
    private Es256DelegationTokenSigner signer;
    private Es256DelegationTokenDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = (ECPrivateKey) keyPair.getPrivate();
        publicKey = (ECPublicKey) keyPair.getPublic();
        signer = new Es256DelegationTokenSigner(
                privateKey,
                "gateway-key-1",
                "iam-gateway",
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                () -> "delegation-jti-1");
        decoder = decoderFor("iam-authorization-service", keyId ->
                "gateway-key-1".equals(keyId) ? Optional.of(publicKey) : Optional.empty());
    }

    @Test
    void signsAndCryptographicallyVerifiesAudienceBoundEs256Token() {
        String token = signer.sign(new DelegationSigningRequest(
                "iam-authorization-service", 10, 20, 30, "request-0001"));

        DelegationValidationResult result = decoder.decode(token);

        assertThat(result.isValid()).isTrue();
        assertThat(result.trustedContext()).get().satisfies(context -> {
            assertThat(context.tenantId()).isEqualTo(10);
            assertThat(context.subjectId()).isEqualTo(20);
            assertThat(context.sessionId()).isEqualTo(30);
            assertThat(context.tokenId()).isEqualTo("delegation-jti-1");
            assertThat(context.expiresAt()).isEqualTo(NOW.plusSeconds(30));
        });
    }

    @Test
    void rejectsSignatureTampering() {
        String token = signer.sign(new DelegationSigningRequest(
                "iam-authorization-service", 10, 20, 30, "request-0001"));
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);

        assertFailure(decoder.decode(tampered), DelegationValidationFailure.INVALID_SIGNATURE);
    }

    @Test
    void rejectsUnknownKeyBeforeClaimTrust() {
        String token = signer.sign(new DelegationSigningRequest(
                "iam-authorization-service", 10, 20, 30, "request-0001"));
        Es256DelegationTokenDecoder unknownKeyDecoder = decoderFor(
                "iam-authorization-service", keyId -> Optional.empty());

        assertFailure(unknownKeyDecoder.decode(token), DelegationValidationFailure.UNKNOWN_KEY_ID);
    }

    @Test
    void distinguishesKeyResolverOutageFromInvalidToken() {
        String token = signer.sign(new DelegationSigningRequest(
                "iam-authorization-service", 10, 20, 30, "request-0001"));
        Es256DelegationTokenDecoder unavailableDecoder = decoderFor(
                "iam-authorization-service", keyId -> {
                    throw new IllegalStateException("key service unavailable");
                });

        assertFailure(
                unavailableDecoder.decode(token),
                DelegationValidationFailure.KEY_RESOLUTION_UNAVAILABLE);
    }

    @Test
    void rejectsValidSignatureForDifferentServiceAudience() {
        String token = signer.sign(new DelegationSigningRequest(
                "iam-file-service", 10, 20, 30, "request-0001"));

        assertFailure(decoder.decode(token), DelegationValidationFailure.INVALID_AUDIENCE);
    }

    @Test
    void rejectsFractionalNumericContextInsteadOfTruncatingIt() throws Exception {
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new com.nimbusds.jose.JOSEObjectType(
                                DelegationTokenPolicy.REQUIRED_TYPE))
                        .keyID("gateway-key-1")
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer("iam-gateway")
                        .subject("20")
                        .audience("iam-authorization-service")
                        .jwtID("delegation-jti-1")
                        .issueTime(Date.from(NOW))
                        .notBeforeTime(Date.from(NOW))
                        .expirationTime(Date.from(NOW.plusSeconds(30)))
                        .claim("tid", 10.5)
                        .claim("sid", 30)
                        .claim("rid", "request-0001")
                        .build());
        token.sign(new ECDSASigner(privateKey));

        assertFailure(
                decoder.decode(token.serialize()),
                DelegationValidationFailure.MALFORMED_TOKEN);
    }

    @Test
    void rejectsAlgorithmSubstitutionWithoutResolvingKey() throws Exception {
        AtomicInteger keyLookups = new AtomicInteger();
        Es256DelegationTokenDecoder guardedDecoder = decoderFor(
                "iam-authorization-service", keyId -> {
                    keyLookups.incrementAndGet();
                    return Optional.of(publicKey);
                });
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("gateway-key-1").build(),
                new JWTClaimsSet.Builder()
                        .issuer("iam-gateway")
                        .subject("20")
                        .audience("iam-authorization-service")
                        .issueTime(Date.from(NOW))
                        .expirationTime(Date.from(NOW.plusSeconds(30)))
                        .build());
        token.sign(new MACSigner(new byte[32]));

        assertFailure(
                guardedDecoder.decode(token.serialize()),
                DelegationValidationFailure.ALGORITHM_NOT_ALLOWED);
        assertThat(keyLookups).hasValue(0);
    }

    @Test
    void rejectsUnsafeKeyIdBeforeCallingResolver() throws Exception {
        AtomicInteger keyLookups = new AtomicInteger();
        Es256DelegationTokenDecoder guardedDecoder = decoderFor(
                "iam-authorization-service", keyId -> {
                    keyLookups.incrementAndGet();
                    return Optional.of(publicKey);
                });
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new com.nimbusds.jose.JOSEObjectType(
                                DelegationTokenPolicy.REQUIRED_TYPE))
                        .keyID("../unsafe-key")
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer("iam-gateway")
                        .subject("20")
                        .audience("iam-authorization-service")
                        .jwtID("delegation-jti-1")
                        .issueTime(Date.from(NOW))
                        .notBeforeTime(Date.from(NOW))
                        .expirationTime(Date.from(NOW.plusSeconds(30)))
                        .claim("tid", 10)
                        .claim("sid", 30)
                        .claim("rid", "request-0001")
                        .build());
        token.sign(new ECDSASigner(privateKey));

        assertFailure(
                guardedDecoder.decode(token.serialize()),
                DelegationValidationFailure.INVALID_KEY_ID);
        assertThat(keyLookups).hasValue(0);
    }

    @Test
    void rejectsNonP256EcKeyEvenWhenItIsOtherwiseAnEcKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair p384 = generator.generateKeyPair();

        assertThatThrownBy(() -> new Es256DelegationTokenSigner(
                (ECPrivateKey) p384.getPrivate(),
                "gateway-key-2",
                "iam-gateway",
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                () -> "delegation-jti-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P-256");

        String token = signer.sign(new DelegationSigningRequest(
                "iam-authorization-service", 10, 20, 30, "request-0001"));
        Es256DelegationTokenDecoder wrongCurveDecoder = decoderFor(
                "iam-authorization-service",
                keyId -> Optional.of((ECPublicKey) p384.getPublic()));

        assertFailure(
                wrongCurveDecoder.decode(token),
                DelegationValidationFailure.ALGORITHM_NOT_ALLOWED);
    }

    @Test
    void rejectsOversizedOrMalformedCompactToken() {
        assertFailure(
                decoder.decode("x".repeat(Es256DelegationTokenDecoder.MAX_COMPACT_TOKEN_LENGTH + 1)),
                DelegationValidationFailure.TOKEN_TOO_LARGE);
        assertFailure(decoder.decode("not-a-jwt"), DelegationValidationFailure.MALFORMED_TOKEN);
    }

    private Es256DelegationTokenDecoder decoderFor(
            String audience,
            DelegationPublicKeyResolver keyResolver) {
        DelegationTokenPolicy policy = new DelegationTokenPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of("ES256"),
                "iam-gateway",
                audience,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));
        return new Es256DelegationTokenDecoder(keyResolver, policy);
    }

    private void assertFailure(
            DelegationValidationResult result,
            DelegationValidationFailure expected) {
        assertThat(result.isValid()).isFalse();
        assertThat(result.failure()).isEqualTo(expected);
        assertThat(result.trustedContext()).isEmpty();
    }
}
