#!/usr/bin/env python3
"""Static gates for strict access JWT validation and Gateway session authentication."""

from __future__ import annotations

import pathlib
import sys

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
COMMON = (
    BACKEND
    / "iam-framework/iam-common-security/src/main/java"
    / "com/enterprise/iam/common/security"
)
GATEWAY = BACKEND / "iam-gateway/src/main/java/com/enterprise/iam/gateway"


def read(path: pathlib.Path, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"required file missing: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def require(path: pathlib.Path, markers: tuple[str, ...], errors: list[str]) -> str:
    source = read(path, errors)
    for marker in markers:
        if source and marker not in source:
            errors.append(f"{path.relative_to(ROOT)} missing marker: {marker}")
    return source


def validate_parameters(errors: list[str]) -> None:
    config = yaml.safe_load(
        (ROOT / "docs/security/SECURITY-PARAMETERS.yaml").read_text(encoding="utf-8")
    )
    access = config.get("jwtAccessToken", {})
    expected = {
        "algorithmAllowlist": ["ES256"],
        "requiredTyp": "at+jwt",
        "ttlSeconds": 300,
        "maximumClockSkewSeconds": 30,
        "maximumCompactTokenBytes": 8192,
        "requiredClaims": [
            "iss", "aud", "sub", "tid", "sid", "tver", "sver",
            "jti", "iat", "nbf", "exp",
        ],
        "audienceCardinality": 1,
        "acceptEmbeddedJwk": False,
        "keyIdRequired": True,
        "sessionStateRequired": True,
        "sessionDependencyUnavailableBehavior": "503_FAIL_CLOSED",
    }
    for key, value in expected.items():
        if access.get(key) != value:
            errors.append(f"jwtAccessToken.{key} must be {value!r}")


def validate_access_codec(errors: list[str]) -> None:
    require(
        COMMON / "jwt/BoundedRefreshingJwkSetPublicKeyResolver.java",
        (
            "MAX_JWKS_BYTES = 65_536",
            "MAX_JWK_COUNT = 32",
            "MAX_NEGATIVE_KEY_COUNT = 1_024",
            "unknownRefreshMinimumInterval",
            "!key.isPrivate()",
        ),
        errors,
    )
    decoder = require(
        COMMON / "access/Es256AccessTokenDecoder.java",
        (
            "MAX_COMPACT_TOKEN_LENGTH = 8_192",
            "JWSAlgorithm.ES256.equals",
            "AccessTokenPolicy.REQUIRED_TYPE.equals",
            "FORBIDDEN_KEY_REFERENCE_HEADERS",
            "KEY_ID.matcher(keyId).matches()",
            "keyResolver.resolve(keyId)",
            "KEY_RESOLUTION_UNAVAILABLE",
            "token.verify(new ECDSAVerifier(publicKey))",
            'requiredLong(claims, "tver")',
            'requiredLong(claims, "sver")',
        ),
        errors,
    )
    require(
        COMMON / "access/AccessTokenPolicy.java",
        (
            'REQUIRED_TYPE = "at+jwt"',
            "List.of(expectedAudience).equals(claims.audience())",
            "claims.issuedAt().isAfter(now.plus(clockSkew))",
            "maximumTtl must not exceed 5 minutes",
            "clockSkew must not exceed 30 seconds",
            "TOKEN_TTL_EXCEEDED",
            "tokenVersion() <= 0",
            "sessionVersion() <= 0",
        ),
        errors,
    )
    require(
        COMMON / "access/Es256AccessTokenSigner.java",
        (
            "JWSAlgorithm.ES256",
            "AccessTokenPolicy.REQUIRED_TYPE",
            ".audience(audience)",
            '.claim("tver", request.tokenVersion())',
            '.claim("sver", request.sessionVersion())',
            "P256Keys.isP256",
        ),
        errors,
    )
    if "BEGIN PRIVATE KEY" in decoder:
        errors.append("access decoder embeds private key material")

    test = read(
        BACKEND
        / "iam-framework/iam-common-security/src/test/java"
        / "com/enterprise/iam/common/security/access/Es256AccessTokenDecoderTest.java",
        errors,
    )
    for marker in (
        "verifiesStrictAtJwtAndPublishesVersionedIdentity",
        "rejectsWrongOrMultipleAudienceAndFutureIssuedAt",
        "rejectsFractionalVersionClaimWithoutTruncation",
        "rejectsAlgorithmConfusionBeforeKeyResolution",
        "distinguishesUnknownKeyFromKeyDependencyOutage",
    ):
        if marker not in test:
            errors.append(f"access-token test evidence missing: {marker}")

    signer_test = read(
        BACKEND
        / "iam-framework/iam-common-security/src/test/java"
        / "com/enterprise/iam/common/security/access/Es256AccessTokenSignerTest.java",
        errors,
    )
    if "signerAndDecoderRoundTripVersionedAccessContext" not in signer_test:
        errors.append("access signer/decoder round-trip evidence is missing")

    access_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (COMMON / "access").glob("*.java")
    )
    if "BEGIN PRIVATE KEY" in access_sources:
        errors.append("access-token source embeds private key material")


def validate_gateway_authentication(errors: list[str]) -> None:
    filter_source = require(
        GATEWAY / "security/GatewayAccessAuthenticationFilter.java",
        (
            "ORDER = Ordered.HIGHEST_PRECEDENCE + 1_000",
            "GATEWAY_ROUTE_ATTR",
            "authorizationHeaders.size() != 1",
            'regionMatches(true, 0, "Bearer ", 0, 7)',
            "tokenDecoder.decode(compactToken)",
            "sessionVerifier.verify(token)",
            "SessionVerificationOutcome.UNAVAILABLE",
            "AUTHENTICATED_PRINCIPAL_ATTRIBUTE",
            "IAM_AUTHENTICATION_REQUIRED",
            "IAM_AUTHENTICATION_DEPENDENCY_UNAVAILABLE",
        ),
        errors,
    )
    for forbidden in ('getFirst("X-Tenant-Id")', 'getFirst("X-User-Id")',
                      'getFirst("X-Session-Id")'):
        if forbidden in filter_source:
            errors.append(f"Gateway authentication trusts external identity header: {forbidden}")

    require(
        GATEWAY / "security/AuthoritativeReactiveSessionStateVerifier.java",
        (
            "snapshot.tenantId() == token.tenantId()",
            "snapshot.subjectId() == token.subjectId()",
            "snapshot.sessionId() == token.sessionId()",
            "snapshot.tokenVersion() == token.tokenVersion()",
            "snapshot.sessionVersion() == token.sessionVersion()",
            "SessionProjectionStatus.ACTIVE.equals",
            "now.isBefore(snapshot.idleExpiresAt())",
            "now.isBefore(snapshot.absoluteExpiresAt())",
            ".defaultIfEmpty(SessionStateVerification.INVALID)",
        ),
        errors,
    )
    require(
        GATEWAY / "config/GatewayAccessAuthenticationConfiguration.java",
        (
            'havingValue = "true"',
            "AccessTokenJwkSetLoader",
            "RefreshingJwkSetAccessTokenPublicKeyResolver",
            "ReactiveSessionSnapshotReader",
            "sanitizingFilter.getOrder() < GatewayAccessAuthenticationFilter.ORDER",
            "GatewayAccessAuthenticationFilter.ORDER < delegationFilter.getOrder()",
        ),
        errors,
    )

    gateway_config = yaml.safe_load(
        (BACKEND / "iam-gateway/src/main/resources/application.yml")
        .read_text(encoding="utf-8")
    )
    access = gateway_config.get("iam", {}).get("gateway", {}).get(
        "access-authentication", {}
    )
    expected = {
        "issuer": "iam-auth-service",
        "audience": "iam-gateway",
        "maximum-ttl": "5m",
        "clock-skew": "30s",
        "jwks-cache-ttl": "5m",
        "unknown-key-ttl": "30s",
        "unknown-key-refresh-minimum-interval": "5s",
        "jwks-connect-timeout": "2s",
        "jwks-request-timeout": "3s",
    }
    for key, value in expected.items():
        if access.get(key) != value:
            errors.append(f"Gateway access authentication {key} must be {value!r}")

    test = read(
        BACKEND
        / "iam-gateway/src/test/java/com/enterprise/iam/gateway/security"
        / "GatewayAccessAuthenticationFilterTest.java",
        errors,
    )
    for marker in (
        "establishesPrincipalOnlyAfterTokenAndSessionValidation",
        "missingOrMalformedBearerTokenReturnsGenericUnauthorized",
        "revokedOrVersionMismatchedSessionReturnsUnauthorized",
        "keyOrSessionDependencyFailureReturnsNonLeakingServiceUnavailable",
        "explicitPublicRouteDoesNotCreateIdentityOrCallDependencies",
        "downstreamFailureAfterSuccessfulAuthenticationIsNotRemapped",
    ):
        if marker not in test:
            errors.append(f"Gateway authentication test evidence missing: {marker}")

    session_test = read(
        BACKEND
        / "iam-gateway/src/test/java/com/enterprise/iam/gateway/security"
        / "AuthoritativeReactiveSessionStateVerifierTest.java",
        errors,
    )
    for marker in (
        "acceptsOnlyExactActiveUnexpiredSnapshot",
        "deniesMissingRevokedExpiredOrVersionMismatchedSnapshot",
        "propagatesReaderFailureForGatewayToMapTo503",
    ):
        if marker not in session_test:
            errors.append(f"session-fence test evidence missing: {marker}")

    configuration_test = read(
        BACKEND
        / "iam-gateway/src/test/java/com/enterprise/iam/gateway/config"
        / "GatewayAccessAuthenticationConfigurationTest.java",
        errors,
    )
    for marker in (
        "remainsDisabledUnlessExplicitlyEnabled",
        "enabledConfigurationFailsWithoutKeyAndSessionSources",
        "createsStrictDecoderSessionVerifierAndOrderedFilter",
        "createsHardenedJwksLoaderAndRedisReaderFromProductionAdapters",
        "rejectsUnsafeDefaultJwksEndpointAtStartup",
        "rejectsUnsafeTtlConfiguration",
    ):
        if marker not in configuration_test:
            errors.append(f"access auto-configuration test evidence missing: {marker}")

    delegation_chain_test = read(
        BACKEND
        / "iam-gateway/src/test/java/com/enterprise/iam/gateway/delegation"
        / "GatewayDelegationFilterTest.java",
        errors,
    )
    if "verifiedAccessAndActiveSessionFlowIntoRouteBoundDelegation" not in delegation_chain_test:
        errors.append("verified access-to-delegation chain test evidence is missing")


def validate_ci(errors: list[str]) -> None:
    for relative in (
        ".github/workflows/backend-build.yml",
        ".github/workflows/contract-quality.yml",
    ):
        source = read(ROOT / relative, errors)
        for marker in (
            "tools/validate_access_authentication.py",
            "python tools/validate_access_authentication.py",
        ):
            if source and marker not in source:
                errors.append(f"{relative} missing access-authentication gate: {marker}")


def main() -> int:
    errors: list[str] = []
    validate_parameters(errors)
    validate_access_codec(errors)
    validate_gateway_authentication(errors)
    validate_ci(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "Access authentication validation passed: strict ES256 at+jwt, "
        "authoritative session versions and ordered Gateway trust establishment"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
