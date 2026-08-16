package com.enterprise.iam.common.security.access;

import com.enterprise.iam.common.security.jwt.P256Keys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict RFC 9068-style ES256 access-token decoder for the IAM profile. */
public final class Es256AccessTokenDecoder implements AccessTokenDecoder {

    public static final int MAX_COMPACT_TOKEN_LENGTH = 8_192;
    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Set<String> FORBIDDEN_KEY_REFERENCE_HEADERS =
            Set.of("jwk", "jku", "x5u", "x5c", "b64");

    private final AccessTokenPublicKeyResolver keyResolver;
    private final AccessTokenPolicy policy;

    public Es256AccessTokenDecoder(
            AccessTokenPublicKeyResolver keyResolver,
            AccessTokenPolicy policy) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public AccessTokenValidationResult decode(String compactToken) {
        if (compactToken == null || compactToken.isBlank()) {
            return invalid(AccessTokenValidationFailure.MISSING_TOKEN);
        }
        if (compactToken.length() > MAX_COMPACT_TOKEN_LENGTH) {
            return invalid(AccessTokenValidationFailure.TOKEN_TOO_LARGE);
        }
        try {
            SignedJWT token = SignedJWT.parse(compactToken);
            if (!JWSAlgorithm.ES256.equals(token.getHeader().getAlgorithm())) {
                return invalid(AccessTokenValidationFailure.ALGORITHM_NOT_ALLOWED);
            }
            if (token.getHeader().getType() == null
                    || !AccessTokenPolicy.REQUIRED_TYPE.equals(
                            token.getHeader().getType().getType())) {
                return invalid(AccessTokenValidationFailure.INVALID_TYPE);
            }
            Map<String, Object> header = token.getHeader().toJSONObject();
            if (header.containsKey("crit")
                    || FORBIDDEN_KEY_REFERENCE_HEADERS.stream().anyMatch(header::containsKey)) {
                return invalid(AccessTokenValidationFailure.UNSAFE_HEADER);
            }
            String keyId = token.getHeader().getKeyID();
            if (keyId == null || keyId.isBlank()) {
                return invalid(AccessTokenValidationFailure.MISSING_KEY_ID);
            }
            if (!KEY_ID.matcher(keyId).matches()) {
                return invalid(AccessTokenValidationFailure.INVALID_KEY_ID);
            }

            Optional<java.security.interfaces.ECPublicKey> resolved;
            try {
                resolved = Objects.requireNonNull(
                        keyResolver.resolve(keyId), "keyResolver result must not be null");
            } catch (RuntimeException exception) {
                return invalid(AccessTokenValidationFailure.KEY_RESOLUTION_UNAVAILABLE);
            }
            if (resolved.isEmpty()) {
                return invalid(AccessTokenValidationFailure.UNKNOWN_KEY_ID);
            }
            var publicKey = resolved.orElseThrow();
            if (!P256Keys.isP256(publicKey)) {
                return invalid(AccessTokenValidationFailure.ALGORITHM_NOT_ALLOWED);
            }
            if (!token.verify(new ECDSAVerifier(publicKey))) {
                return invalid(AccessTokenValidationFailure.INVALID_SIGNATURE);
            }

            JWTClaimsSet claims = token.getJWTClaimsSet();
            List<String> audience = claims.getAudience();
            AccessTokenClaims verifiedClaims = new AccessTokenClaims(
                    claims.getIssuer(),
                    audience,
                    requiredLong(claims, "tid"),
                    requiredLong(claims, "sub"),
                    requiredLong(claims, "sid"),
                    requiredLong(claims, "tver"),
                    requiredLong(claims, "sver"),
                    claims.getJWTID(),
                    instant(claims.getIssueTime()),
                    instant(claims.getNotBeforeTime()),
                    instant(claims.getExpirationTime()));
            return policy.validate(verifiedClaims);
        } catch (ParseException | JOSEException | RuntimeException exception) {
            return invalid(AccessTokenValidationFailure.MALFORMED_TOKEN);
        }
    }

    private static long requiredLong(JWTClaimsSet claims, String name) throws ParseException {
        Object value = "sub".equals(name) ? claims.getSubject() : claims.getClaim(name);
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

    private static AccessTokenValidationResult invalid(AccessTokenValidationFailure failure) {
        return AccessTokenValidationResult.invalid(failure);
    }
}
