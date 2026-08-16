package com.enterprise.iam.common.security.access;

import com.enterprise.iam.common.security.jwt.P256Keys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Signs the frozen short-lived IAM access-token profile with externally held key material. */
public final class Es256AccessTokenSigner implements AccessTokenSigner {

    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");
    private static final Pattern TOKEN_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final ECPrivateKey privateKey;
    private final String keyId;
    private final String issuer;
    private final String audience;
    private final Clock clock;
    private final Duration timeToLive;
    private final Supplier<String> tokenIdSupplier;

    public Es256AccessTokenSigner(
            ECPrivateKey privateKey,
            String keyId,
            String issuer,
            String audience,
            Clock clock,
            Duration timeToLive,
            Supplier<String> tokenIdSupplier) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        if (!P256Keys.isP256(this.privateKey)) {
            throw new IllegalArgumentException("ES256 requires a P-256 private key");
        }
        this.keyId = requirePattern(keyId, "keyId", KEY_ID);
        this.issuer = requirePattern(issuer, "issuer", SERVICE_NAME);
        this.audience = requirePattern(audience, "audience", SERVICE_NAME);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive must not be null");
        if (timeToLive.isNegative()
                || timeToLive.isZero()
                || timeToLive.compareTo(Duration.ofMinutes(5)) > 0
                || timeToLive.toNanosPart() != 0) {
            throw new IllegalArgumentException(
                    "access-token TTL must be a whole second from 1 to 300 seconds");
        }
        this.tokenIdSupplier = Objects.requireNonNull(
                tokenIdSupplier, "tokenIdSupplier must not be null");
    }

    @Override
    public SignedAccessToken sign(AccessTokenSigningRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant issuedAt = clock.instant();
        String tokenId = requirePattern(tokenIdSupplier.get(), "tokenId", TOKEN_ID);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(Long.toString(request.subjectId()))
                .jwtID(tokenId)
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(timeToLive)))
                .claim("tid", request.tenantId())
                .claim("sid", request.sessionId())
                .claim("tver", request.tokenVersion())
                .claim("sver", request.sessionVersion())
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType(AccessTokenPolicy.REQUIRED_TYPE))
                        .keyID(keyId)
                        .build(),
                claims);
        try {
            token.sign(new ECDSASigner(privateKey));
            return new SignedAccessToken(
                    token.serialize(), issuedAt, issuedAt.plus(timeToLive));
        } catch (JOSEException exception) {
            throw new AccessTokenSigningException("access token signing failed", exception);
        }
    }

    private static String requirePattern(
            String value,
            String name,
            Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
        return value;
    }
}
