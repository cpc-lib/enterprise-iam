#!/usr/bin/env python3
"""Static gates for JWKS rotation, downstream auto-configuration and Gateway issuance."""

from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
COMMON = (
    BACKEND
    / "iam-framework/iam-common-security/src/main/java"
    / "com/enterprise/iam/common/security/delegation"
)
JWT = (
    BACKEND
    / "iam-framework/iam-common-security/src/main/java"
    / "com/enterprise/iam/common/security/jwt"
)
STARTER = (
    BACKEND
    / "iam-framework/iam-security-spring-boot-starter/src/main/java"
    / "com/enterprise/iam/security"
)
GATEWAY = BACKEND / "iam-gateway/src/main/java/com/enterprise/iam/gateway"


def read(path: pathlib.Path, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"required file missing: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def require(path: pathlib.Path, markers: tuple[str, ...], errors: list[str]) -> str:
    text = read(path, errors)
    for marker in markers:
        if text and marker not in text:
            errors.append(f"{path.relative_to(ROOT)} missing marker: {marker}")
    return text


def dependencies(path: pathlib.Path) -> set[str]:
    node = ET.parse(path).getroot()
    return {
        element.text.strip()
        for element in node.findall("m:dependencies/m:dependency/m:artifactId", NS)
    }


def validate_parameters(errors: list[str]) -> None:
    security = yaml.safe_load(
        (ROOT / "docs/security/SECURITY-PARAMETERS.yaml").read_text(encoding="utf-8")
    )
    delegation = security.get("jwtDelegationToken", {})
    expected = {
        "algorithmAllowlist": ["ES256"],
        "ttlSeconds": 30,
        "maximumClockSkewSeconds": 5,
        "maximumCompactTokenBytes": 4096,
        "jwksMaximumBytes": 65_536,
        "jwksMaximumKeys": 32,
        "jwksCacheSeconds": 300,
        "unknownKeyNegativeCacheSeconds": 30,
        "unknownKeyRefreshMinimumIntervalSeconds": 5,
        "unknownKeyNegativeCacheMaximumEntries": 1024,
        "audienceCardinality": 1,
        "externalTokenAcceptedByDownstream": False,
        "missingOrInvalidBehavior": "DENY",
    }
    for key, value in expected.items():
        if delegation.get(key) != value:
            errors.append(f"jwtDelegationToken.{key} must be {value!r}")


def validate_jwks(errors: list[str]) -> None:
    resolver = require(
        JWT / "BoundedRefreshingJwkSetPublicKeyResolver.java",
        (
            "MAX_JWKS_BYTES = 65_536",
            "MAX_JWK_COUNT = 32",
            "MAX_NEGATIVE_KEY_COUNT = 1_024",
            "Curve.P_256.equals",
            "KeyUse.SIGNATURE.equals",
            "JWSAlgorithm.ES256.equals",
            "!key.isPrivate()",
            "KeyOperation.VERIFY",
            "duplicate usable key ID",
            "unknownKeyUntil",
            "lastRefreshAt",
            "unknownRefreshMinimumInterval",
            "refreshIfStillRequired(observed, true)",
            "JwkSetKeyResolutionException",
        ),
        errors,
    )
    test = read(
        BACKEND
        / "iam-framework/iam-common-security/src/test/java"
        / "com/enterprise/iam/common/security/delegation"
        / "RefreshingJwkSetDelegationPublicKeyResolverTest.java",
        errors,
    )
    for marker in (
        "cachesKnownKeyAndRefreshesOnceForRotatedUnknownKeyId",
        "negativeCachesUnknownKeyToBoundAttackerTriggeredRefreshes",
        "retriesRotatedKeyAtRefreshCooldownInsteadOfFullNegativeTtl",
        "boundsNegativeCacheUnderRandomKeyIdSpray",
        "expiredCacheDoesNotMaskJwksLoaderFailure",
        "rejectsDuplicateUsableKeyIdsAndNonEs256OnlySets",
        "rejectsOversizedJwksBeforeParsing",
    ):
        if marker not in test:
            errors.append(f"JWKS test evidence missing: {marker}")

    common_main = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (BACKEND / "iam-framework/iam-common-security/src/main/java").glob("**/*.java")
    )
    for forbidden in ("jakarta.servlet", "org.springframework.web", "springframework.security.web"):
        if forbidden in common_main:
            errors.append(f"iam-common-security contains forbidden web coupling: {forbidden}")
    common_dependencies = dependencies(BACKEND / "iam-framework/iam-common-security/pom.xml")
    for forbidden in ("spring-security-web", "spring-web", "jakarta.servlet-api"):
        if forbidden in common_dependencies:
            errors.append(f"iam-common-security contains forbidden dependency: {forbidden}")
    if "BEGIN PRIVATE KEY" in resolver:
        errors.append("JWKS resolver source embeds private key material")


def validate_starter(errors: list[str]) -> None:
    imports = read(
        BACKEND
        / "iam-framework/iam-security-spring-boot-starter/src/main/resources"
        / "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        errors,
    )
    if "IamDelegationSecurityAutoConfiguration" not in imports:
        errors.append("security starter auto-configuration import is missing")

    require(
        STARTER / "autoconfigure/IamDelegationSecurityAutoConfiguration.java",
        (
            "@AutoConfiguration",
            "ConditionalOnWebApplication.Type.SERVLET",
            'havingValue = "true"',
            "DelegationJwkSetLoader",
            "RefreshingJwkSetDelegationPublicKeyResolver",
            "Es256DelegationTokenDecoder",
            "FilterRegistrationBean<TrustedDelegationFilter>",
        ),
        errors,
    )
    require(
        STARTER / "delegation/PathPatternDelegationRequestMatcher.java",
        (
            "PathPatternParser",
            'pattern.startsWith("/internal/")',
            "at least one protected path is required",
        ),
        errors,
    )
    filter_source = require(
        STARTER / "delegation/TrustedDelegationFilter.java",
        (
            "decoder.decode",
            "KEY_RESOLUTION_UNAVAILABLE",
            "SC_SERVICE_UNAVAILABLE",
            "SC_UNAUTHORIZED",
            "request.setAttribute(TRUSTED_CONTEXT_ATTRIBUTE",
        ),
        errors,
    )
    if "INVALID_SIGNATURE" in filter_source:
        errors.append("downstream filter must not expose validation reasons")

    starter_dependencies = dependencies(
        BACKEND / "iam-framework/iam-security-spring-boot-starter/pom.xml"
    )
    for required in (
        "iam-common-security",
        "spring-boot-autoconfigure",
        "spring-web",
        "spring-security-web",
        "jakarta.servlet-api",
    ):
        if required not in starter_dependencies:
            errors.append(f"security starter dependency missing: {required}")

    authz_dependencies = dependencies(BACKEND / "iam-authorization-service/pom.xml")
    if "iam-security-spring-boot-starter" not in authz_dependencies:
        errors.append("authorization service must consume the security starter")
    config = yaml.safe_load(
        (BACKEND / "iam-authorization-service/src/main/resources/application.yml")
        .read_text(encoding="utf-8")
    )
    delegation = config.get("iam", {}).get("security", {}).get("delegation", {})
    expected = {
        "issuer": "iam-gateway",
        "audience": "iam-authorization-service",
        "protected-paths": ["/internal/**"],
        "maximum-ttl": "30s",
        "clock-skew": "5s",
        "jwks-cache-ttl": "5m",
        "unknown-key-ttl": "30s",
        "unknown-key-refresh-minimum-interval": "5s",
    }
    for key, value in expected.items():
        if delegation.get(key) != value:
            errors.append(f"authorization delegation config {key} must be {value!r}")


def validate_gateway(errors: list[str]) -> None:
    filter_source = require(
        GATEWAY / "delegation/GatewayDelegationFilter.java",
        (
            "GATEWAY_ROUTE_ATTR",
            "audienceRegistry.audienceForRoute(routeId)",
            "AUTHENTICATED_PRINCIPAL_ATTRIBUTE",
            "headers.remove(HttpHeaders.AUTHORIZATION)",
            "headers.remove(DELEGATION_HEADER)",
            "headers.set(DELEGATION_HEADER, compactToken)",
            "IAM_AUTHENTICATION_REQUIRED",
            "IAM_DELEGATION_UNAVAILABLE",
        ),
        errors,
    )
    require(
        GATEWAY / "delegation/ConfiguredDownstreamRouteAudienceRegistry.java",
        (
            "Map.copyOf",
            "protected route ID format is invalid",
            "protected route audience format is invalid",
            "a route cannot be both public and delegation protected",
            "isExplicitPublicRoute",
        ),
        errors,
    )
    configuration = require(
        GATEWAY / "config/GatewayDelegationConfiguration.java",
        (
            'havingValue = "true"',
            "Es256DelegationTokenSigner",
            "ConfiguredDownstreamRouteAudienceRegistry",
            "GatewayDelegationFilter",
        ),
        errors,
    )
    if "BEGIN PRIVATE KEY" in filter_source + configuration:
        errors.append("Gateway delegation source embeds private key material")

    test = read(
        BACKEND
        / "iam-gateway/src/test/java/com/enterprise/iam/gateway/delegation"
        / "GatewayDelegationFilterTest.java",
        errors,
    )
    for marker in (
        "bindsProtectedRouteToAudienceAndRemovesExternalBearerToken",
        "protectedRouteWithoutAuthenticatedPrincipalFailsClosed",
        "signingFailureReturnsNonLeakingServiceUnavailable",
        "unprotectedRouteStillRemovesExternalCredentialsBeforeForwarding",
        "routeWithoutExplicitSecurityPolicyFailsClosed",
    ):
        if marker not in test:
            errors.append(f"Gateway delegation test evidence missing: {marker}")


def main() -> int:
    errors: list[str] = []
    validate_parameters(errors)
    validate_jwks(errors)
    validate_starter(errors)
    validate_gateway(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "Delegation wiring validation passed: bounded rotating JWKS, Servlet "
        "auto-configuration and route-bound Gateway issuance"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
