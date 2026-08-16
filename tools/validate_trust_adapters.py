#!/usr/bin/env python3
"""Static gates for the HTTPS JWKS and Redis session trust adapters."""

from __future__ import annotations

import pathlib
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
COMMON = (
    BACKEND
    / "iam-framework/iam-common-security/src/main/java"
    / "com/enterprise/iam/common/security"
)
GATEWAY = BACKEND / "iam-gateway"
AUTH = BACKEND / "iam-auth-service"


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


def validate_shared_projection(errors: list[str]) -> None:
    session = COMMON / "session"
    require(
        session / "SessionSecurityProjection.java",
        (
            "requirePositive(tokenVersion",
            "requirePositive(sessionVersion",
            "requireMillisecondPrecision",
            "idleExpiresAt.isAfter(absoluteExpiresAt)",
        ),
        errors,
    )
    require(
        session / "SessionProjectionStatus.java",
        ("ACTIVE", "REVOKED", "EXPIRED"),
        errors,
    )
    require(
        session / "SessionProjectionSchema.java",
        (
            'SCHEMA_VERSION = "1"',
            'KEY_PREFIX = "iam:session-security:"',
            'FIELD_SCHEMA_VERSION = "schemaVersion"',
            'FIELD_TOKEN_VERSION = "tokenVersion"',
            'FIELD_ABSOLUTE_EXPIRES_AT = "absoluteExpiresAtEpochMs"',
            "POSITIVE_DECIMAL",
            "MAX_FIELD_LENGTH = 128",
            "values.size() != FIELDS.size()",
            "SessionProjectionStatus.valueOf",
            "isCompletelyAbsent",
        ),
        errors,
    )
    test = read(
        BACKEND
        / "iam-framework/iam-common-security/src/test/java"
        / "com/enterprise/iam/common/security/session/SessionProjectionSchemaTest.java",
        errors,
    )
    for marker in (
        "roundTripsTheFrozenHashFieldOrder",
        "rejectsPartialUnknownOrNonCanonicalValues",
        "distinguishesAnAbsentHashFromACorruptProjection",
    ):
        if marker not in test:
            errors.append(f"shared projection test evidence missing: {marker}")


def validate_redis_adapters(errors: list[str]) -> None:
    reader = require(
        GATEWAY
        / "src/main/java/com/enterprise/iam/gateway/security"
        / "RedisReactiveSessionSnapshotReader.java",
        (
            "ReactiveStringRedisTemplate",
            "opsForHash()",
            "multiGet(key, SessionProjectionSchema.FIELDS)",
            "SessionProjectionSchema.isCompletelyAbsent",
            "SessionProjectionSchema.decode",
        ),
        errors,
    )
    for forbidden in ("ConcurrentHashMap", "Map.of()", "onErrorReturn"):
        if forbidden in reader:
            errors.append(f"Redis reader contains fallback marker: {forbidden}")

    publisher = require(
        AUTH
        / "src/main/java/com/enterprise/iam/auth/infrastructure/redis"
        / "RedisSessionSecurityProjectionPublisher.java",
        (
            "StringRedisTemplate",
            "DefaultRedisScript",
            "isPositiveLong",
            "9223372036854775807",
            "HMGET",
            "incomingTokenVersion < currentTokenVersion",
            "incomingSessionVersion < currentSessionVersion",
            "current[7] ~= 'ACTIVE' and ARGV[7] == 'ACTIVE'",
            "HSET",
            "PEXPIREAT",
            "STALE_IGNORED",
            "SessionProjectionPublicationException",
        ),
        errors,
    )
    reader_test = read(
        GATEWAY
        / "src/test/java/com/enterprise/iam/gateway/security"
        / "RedisReactiveSessionSnapshotReaderTest.java",
        errors,
    )
    for marker in (
        "readsTheSharedProjectionInFrozenFieldOrder",
        "mapsACompletelyMissingHashToEmptyButRejectsPartialState",
        "propagatesRedisFailureWithoutAnInMemoryFallback",
    ):
        if marker not in reader_test:
            errors.append(f"Redis reader test evidence missing: {marker}")

    publisher_test = read(
        AUTH
        / "src/test/java/com/enterprise/iam/auth/infrastructure/redis"
        / "RedisSessionSecurityProjectionPublisherTest.java",
        errors,
    )
    for marker in (
        "freezesAtomicExpiryVersionAndTerminalStateControls",
        "distinguishesAppliedStaleAndIndeterminateResults",
    ):
        if marker not in publisher_test:
            errors.append(f"Redis publisher test evidence missing: {marker}")

    gateway_pom = read(GATEWAY / "pom.xml", errors)
    if "spring-boot-starter-data-redis-reactive" not in gateway_pom:
        errors.append("Gateway reactive Redis dependency is missing")
    auth_pom = read(AUTH / "pom.xml", errors)
    for artifact in ("spring-boot-starter-data-redis", "iam-common-security"):
        if artifact not in auth_pom:
            errors.append(f"auth-service dependency is missing: {artifact}")


def validate_https_jwks(errors: list[str]) -> None:
    package = (
        GATEWAY
        / "src/main/java/com/enterprise/iam/gateway/security/jwks"
    )
    loader = require(
        package / "AllowlistedHttpsAccessTokenJwkSetLoader.java",
        (
            '"https".equalsIgnoreCase(uri.getScheme())',
            "uri.getRawUserInfo() != null",
            "uri.getRawQuery() != null",
            "uri.getRawFragment() != null",
            "uri.getPort() != 443",
            "JWKS URI host is not exactly allowlisted",
            "requireOnlyGlobalAddresses",
            "isAnyLocalAddress",
            "isSiteLocalAddress",
            "isIpv4Mapped",
            "statusCode() != 200",
            'headerValues("Content-Type")',
            'headerValues("Content-Encoding")',
            'headerValues("Content-Length")',
            "readNBytes(MAX_BYTES + 1)",
            "CodingErrorAction.REPORT",
        ),
        errors,
    )
    for forbidden in ("jku", "X-Forwarded", "getQueryParameter", "ofByteArray()"):
        if forbidden in loader:
            errors.append(f"JWKS loader contains unsafe marker: {forbidden}")

    require(
        package / "JavaHttpClientJwksTransport.java",
        (
            "HttpClient.Redirect.NEVER",
            "Proxy.NO_PROXY",
            'tls.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"})',
            'tls.setEndpointIdentificationAlgorithm("HTTPS")',
            ".connectTimeout(connectTimeout)",
            ".timeout(requestTimeout)",
            'header("Accept", "application/jwk-set+json, application/json")',
            "BodyHandlers.ofInputStream()",
            "Thread.currentThread().interrupt()",
        ),
        errors,
    )
    require(
        GATEWAY
        / "src/main/java/com/enterprise/iam/gateway/config"
        / "GatewayAccessAuthenticationConfiguration.java",
        (
            "AllowlistedHttpsAccessTokenJwkSetLoader",
            "JavaHttpClientJwksTransport",
            "SystemJwksDnsResolver",
            "RedisReactiveSessionSnapshotReader",
            "validateJwksTransportConfiguration",
        ),
        errors,
    )
    require(
        GATEWAY
        / "src/main/java/com/enterprise/iam/gateway/config"
        / "GatewayAccessAuthenticationProperties.java",
        (
            "jwksUri",
            "jwksAllowedHosts",
            "jwksConnectTimeout = Duration.ofSeconds(2)",
            "jwksRequestTimeout = Duration.ofSeconds(3)",
            "Duration.ofSeconds(5), \"jwks-connect-timeout\"",
            "Duration.ofSeconds(10), \"jwks-request-timeout\"",
        ),
        errors,
    )
    jwks_test = read(
        GATEWAY
        / "src/test/java/com/enterprise/iam/gateway/security/jwks"
        / "AllowlistedHttpsAccessTokenJwkSetLoaderTest.java",
        errors,
    )
    for marker in (
        "fetchesAnExactAllowlistedGlobalHttpsEndpoint",
        "rejectsNonHttpsCredentialsQueryTraversalAndHostMismatch",
        "blocksPrivateOrMixedDnsAnswersBeforeTransport",
        "rejectsRedirectContentTypeEncodingAndAmbiguousLength",
        "enforcesTheStreamingByteLimitAndStrictUtf8",
        "classifiesPrivateDocumentationAndGlobalAddresses",
    ):
        if marker not in jwks_test:
            errors.append(f"HTTPS JWKS test evidence missing: {marker}")


def validate_contract_and_ci(errors: list[str]) -> None:
    require(
        ROOT / "docs/spec/44-https-jwks-and-redis-session-projection-freeze.md",
        (
            "DNS validation before the request is not IP pinning",
            "transactional outbox",
            "PEXPIREAT",
            "65,536 bytes",
            "Java 21 Maven/JUnit execution",
        ),
        errors,
    )
    for relative in (
        ".github/workflows/backend-build.yml",
        ".github/workflows/contract-quality.yml",
    ):
        source = read(ROOT / relative, errors)
        for marker in (
            "tools/validate_trust_adapters.py",
            "python tools/validate_trust_adapters.py",
        ):
            if source and marker not in source:
                errors.append(f"{relative} missing trust-adapter gate: {marker}")


def main() -> int:
    errors: list[str] = []
    validate_shared_projection(errors)
    validate_redis_adapters(errors)
    validate_https_jwks(errors)
    validate_contract_and_ci(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "Trust-adapter validation passed: exact-host bounded HTTPS JWKS and "
        "strict monotonic Redis session projection"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
