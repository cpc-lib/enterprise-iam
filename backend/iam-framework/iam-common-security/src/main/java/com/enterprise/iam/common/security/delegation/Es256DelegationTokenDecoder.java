package com.enterprise.iam.common.security.delegation;

import com.enterprise.iam.common.security.jwt.P256Keys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class Es256DelegationTokenDecoder implements DelegationTokenDecoder {

    public static final int MAX_COMPACT_TOKEN_LENGTH = 4096;
    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final DelegationPublicKeyResolver keyResolver;
    private final DelegationTokenPolicy policy;

    public Es256DelegationTokenDecoder(
            DelegationPublicKeyResolver keyResolver,
            DelegationTokenPolicy policy) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public DelegationValidationResult decode(String compactToken) {
        if (compactToken == null || compactToken.isBlank()) {
            return invalid(DelegationValidationFailure.MISSING_TOKEN);
        }
        if (compactToken.length() > MAX_COMPACT_TOKEN_LENGTH) {
            return invalid(DelegationValidationFailure.TOKEN_TOO_LARGE);
        }
        try {
            SignedJWT token = SignedJWT.parse(compactToken);
            if (!JWSAlgorithm.ES256.equals(token.getHeader().getAlgorithm())) {
                return invalid(DelegationValidationFailure.ALGORITHM_NOT_ALLOWED);
            }
            if (token.getHeader().getType() == null
                    || !DelegationTokenPolicy.REQUIRED_TYPE.equals(token.getHeader().getType().getType())) {
                return invalid(DelegationValidationFailure.INVALID_TYPE);
            }
            String keyId = token.getHeader().getKeyID();
            if (keyId == null || keyId.isBlank()) {
                return invalid(DelegationValidationFailure.MISSING_KEY_ID);
            }
            if (!KEY_ID.matcher(keyId).matches()) {
                return invalid(DelegationValidationFailure.INVALID_KEY_ID);
            }
            Optional<java.security.interfaces.ECPublicKey> resolved;
            try {
                resolved = Objects.requireNonNull(
                        keyResolver.resolve(keyId), "keyResolver result must not be null");
            } catch (RuntimeException exception) {
                return invalid(DelegationValidationFailure.KEY_RESOLUTION_UNAVAILABLE);
            }
            if (resolved.isEmpty()) {
                return invalid(DelegationValidationFailure.UNKNOWN_KEY_ID);
            }
            var publicKey = resolved.get();
            if (!P256Keys.isP256(publicKey)) {
                return invalid(DelegationValidationFailure.ALGORITHM_NOT_ALLOWED);
            }
            if (!token.verify(new ECDSAVerifier(publicKey))) {
                return invalid(DelegationValidationFailure.INVALID_SIGNATURE);
            }

            JWTClaimsSet claims = token.getJWTClaimsSet();
            DelegationTokenClaims verifiedClaims = new DelegationTokenClaims(
                    true,
                    token.getHeader().getAlgorithm().getName(),
                    token.getHeader().getType().getType(),
                    keyId,
                    claims.getIssuer(),
                    Set.copyOf(claims.getAudience()),
                    requiredLong(claims, "tid"),
                    requiredLong(claims, "sub"),
                    requiredLong(claims, "sid"),
                    claims.getJWTID(),
                    claims.getStringClaim("rid"),
                    instant(claims.getIssueTime()),
                    instant(claims.getNotBeforeTime()),
                    instant(claims.getExpirationTime()));
            return policy.validate(verifiedClaims);
        } catch (ParseException | JOSEException | RuntimeException exception) {
            return invalid(DelegationValidationFailure.MALFORMED_TOKEN);
        }
    }

    private static long requiredLong(JWTClaimsSet claims, String name) throws ParseException {
        if ("sub".equals(name)) {
            return parsePositiveLong(claims.getSubject());
        }
        Object value = claims.getClaim(name);
        if (value instanceof Number number) {
            return parsePositiveLong(number.toString());
        }
        return parsePositiveLong(value == null ? null : value.toString());
    }

    private static long parsePositiveLong(String value) throws ParseException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ParseException("required numeric claim is invalid", 0);
        }
    }

    private static Instant instant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private static DelegationValidationResult invalid(DelegationValidationFailure failure) {
        return DelegationValidationResult.invalid(failure);
    }
}
