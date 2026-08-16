package com.enterprise.iam.common.security.delegation;

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

public final class Es256DelegationTokenSigner {

    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ECPrivateKey privateKey;
    private final String keyId;
    private final String issuer;
    private final Clock clock;
    private final Duration timeToLive;
    private final Supplier<String> tokenIdSupplier;

    public Es256DelegationTokenSigner(
            ECPrivateKey privateKey,
            String keyId,
            String issuer,
            Clock clock,
            Duration timeToLive,
        Supplier<String> tokenIdSupplier) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        if (!P256Keys.isP256(this.privateKey)) {
            throw new IllegalArgumentException("ES256 requires a P-256 private key");
        }
        this.keyId = requireText(keyId, "keyId");
        if (!KEY_ID.matcher(this.keyId).matches()) {
            throw new IllegalArgumentException("keyId format is invalid");
        }
        this.issuer = requireText(issuer, "issuer");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive must not be null");
        if (timeToLive.isNegative() || timeToLive.isZero() || timeToLive.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("delegation TTL must be between 1 and 30 seconds");
        }
        this.tokenIdSupplier = Objects.requireNonNull(tokenIdSupplier, "tokenIdSupplier must not be null");
    }

    public String sign(DelegationSigningRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant issuedAt = clock.instant();
        String tokenId = requireText(tokenIdSupplier.get(), "tokenId");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(request.audience())
                .subject(Long.toString(request.subjectId()))
                .jwtID(tokenId)
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(timeToLive)))
                .claim("tid", request.tenantId())
                .claim("sid", request.sessionId())
                .claim("rid", request.requestId())
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType(DelegationTokenPolicy.REQUIRED_TYPE))
                        .keyID(keyId)
                        .build(),
                claims);
        try {
            token.sign(new ECDSASigner(privateKey));
            return token.serialize();
        } catch (JOSEException exception) {
            throw new DelegationTokenSigningException("delegation token signing failed", exception);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException(name + " must be non-blank and at most 128 characters");
        }
        return normalized;
    }
}
