#!/usr/bin/env python3
"""Static evidence checks for transactional login-session and token issuance."""

from __future__ import annotations

import pathlib
import sys

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"


def require(path: pathlib.Path, markers: tuple[str, ...], errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"missing: {path.relative_to(ROOT)}")
        return
    source = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            errors.append(f"{path.relative_to(ROOT)}: missing marker {marker!r}")


def validate_signing_boundary(errors: list[str]) -> None:
    root = (
        BACKEND
        / "iam-framework/iam-common-security/src/main/java"
        / "com/enterprise/iam/common/security/access"
    )
    require(root / "AccessTokenSigner.java", ("SignedAccessToken sign", "HSM/KMS"), errors)
    require(
        root / "SignedAccessToken.java",
        ("COMPACT_JWS", "MAX_COMPACT_TOKEN_LENGTH", "compact=REDACTED"),
        errors,
    )
    require(
        root / "Es256AccessTokenSigner.java",
        ("implements AccessTokenSigner", "new SignedAccessToken"),
        errors,
    )


def validate_refresh_security(errors: list[str]) -> None:
    root = (
        BACKEND
        / "iam-auth-service/src/main/java/com/enterprise/iam/auth"
    )
    require(
        root / "application/model/SensitiveRefreshToken.java",
        ("value.clone()", "Arrays.fill", "REDACTED", "destroy()"),
        errors,
    )
    require(
        root / "application/model/IssuedLoginSession.java",
        ("SensitiveRefreshToken", "accessToken=REDACTED", "refreshToken=REDACTED"),
        errors,
    )
    generator = root / "infrastructure/security/SecureOpaqueRefreshTokenGenerator.java"
    require(
        generator,
        ("RANDOM_BYTES = 32", '"rt1."', "withoutPadding()", "Arrays.fill"),
        errors,
    )
    hasher = root / "infrastructure/security/HmacSha256RefreshTokenHasher.java"
    require(
        hasher,
        ("Mac.getInstance(\"HmacSHA256\")", "HASH_BYTES = 32", "Arrays.fill"),
        errors,
    )
    if hasher.is_file() and "new String" in hasher.read_text(encoding="utf-8"):
        errors.append("production refresh hashing must not create an immutable token String")
    require(
        root / "infrastructure/security/RefreshTokenHashKey.java",
        ("HmacSHA256", "at least 256 bits", "getEncoded()"),
        errors,
    )


def validate_transaction(errors: list[str]) -> None:
    root = (
        BACKEND
        / "iam-auth-service/src/main/java/com/enterprise/iam/auth/infrastructure"
    )
    require(
        root / "persistence/TimeOrderedPositiveIdGenerator.java",
        (
            "CUSTOM_EPOCH_MILLIS",
            "MAX_NODE_ID = 1_023",
            "MAX_SEQUENCE = 4_095",
            "clock moved backwards",
        ),
        errors,
    )
    require(
        root / "persistence/TransactionalJdbcLoginSessionIssuer.java",
        (
            "PROPAGATION_REQUIRES_NEW",
            "ISOLATION_READ_COMMITTED",
            "FOR UPDATE",
            "getMaximumConcurrentSessions",
            "INSERT INTO iam_login_session",
            "INSERT INTO iam_refresh_token",
            "projectionAppender.append",
            "accessTokenSigner.sign",
            "tokenToDestroyOnFailure",
            "Arrays.fill(refreshHash",
        ),
        errors,
    )
    require(
        root / "config/SessionIssuanceConfiguration.java",
        (
            "iam.auth.session-issuance",
            'havingValue = "true"',
            "RefreshTokenHashKey",
            "AccessTokenSigner",
        ),
        errors,
    )
    require(
        root / "config/SessionIssuanceProperties.java",
        ("node-id must be explicitly set", "maximum-concurrent-sessions"),
        errors,
    )


def validate_contract_and_tests(errors: list[str]) -> None:
    require(
        BACKEND / "pom.xml",
        (
            "maven-dependency-plugin",
            "resolve-test-agent-paths",
            "-javaagent:${org.mockito:mockito-core:jar}",
            "<classesDirectory>${project.build.outputDirectory}</classesDirectory>",
        ),
        errors,
    )
    require(
        BACKEND
        / "iam-test-support/src/main/java/com/enterprise/iam/testsupport/database"
        / "MySqlIntegrationDatabase.java",
        (
            "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE",
            "IAM_TEST_MYSQL_JDBC_URL",
            'DockerImageName.parse("mysql:8.4.9")',
            "mysql.start()",
        ),
        errors,
    )
    security = yaml.safe_load(
        (ROOT / "docs/security/SECURITY-PARAMETERS.yaml").read_text(encoding="utf-8")
    )
    refresh = security.get("refreshToken", {})
    expected = {
        "formatPrefix": "rt1",
        "entropyBits": 256,
        "randomBytes": 32,
        "keyIdRequired": True,
        "hashAlgorithm": "HmacSHA256",
        "minimumHashKeyBits": 256,
        "storage": "keyed-hash-only",
    }
    for key, value in expected.items():
        if refresh.get(key) != value:
            errors.append(f"refreshToken.{key} must be {value!r}")
    cookie = refresh.get("cookie", {})
    expected_cookie = {
        "name": "IAM_REFRESH",
        "httpOnly": True,
        "secure": True,
        "sameSite": "Strict",
        "path": "/api/v1/auth",
        "exactAttributeOrder": ["Path", "Secure", "HttpOnly", "SameSite"],
    }
    for key, value in expected_cookie.items():
        if cookie.get(key) != value:
            errors.append(f"refreshToken.cookie.{key} must be {value!r}")
    if security.get("session", {}).get("idNumericMaximum") != 9_223_372_036_854_775_807:
        errors.append("session.idNumericMaximum must match positive Java long")
    login_mutation = security.get("loginMutation", {})
    if login_mutation.get("genericIdempotencyReplay") != "explicitly-excluded":
        errors.append("login mutation must explicitly exclude generic secret replay")
    if login_mutation.get("requestIdPurpose") != "correlation-only":
        errors.append("login requestId must be frozen as correlation-only")

    app_config = yaml.safe_load(
        (BACKEND / "iam-auth-service/src/main/resources/application.yml")
        .read_text(encoding="utf-8")
    )
    issuance = app_config.get("iam", {}).get("auth", {}).get("session-issuance", {})
    if issuance.get("enabled") != "${IAM_AUTH_SESSION_ISSUANCE_ENABLED:false}":
        errors.append("session issuance must remain disabled by default")
    if "node-id" in issuance:
        errors.append("source config must not provide a default deployment node ID")

    openapi = yaml.safe_load(
        (ROOT / "docs/api/openapi-code-phase-01.yaml").read_text(encoding="utf-8")
    )
    login = openapi.get("paths", {}).get("/api/v1/auth/login", {}).get("post", {})
    if login.get("x-iam-idempotency-policy") != "EXPLICITLY_EXCLUDED_NON_REPLAYABLE_SECRET":
        errors.append("login idempotency exclusion is not frozen in OpenAPI")
    if "409" not in login.get("responses", {}):
        errors.append("login response must map the session limit to 409")
    login_cookie = (
        login.get("responses", {}).get("200", {}).get("headers", {})
        .get("Set-Cookie", {}).get("schema", {})
    )
    if "IAM_REFRESH=rt1" not in login_cookie.get("pattern", ""):
        errors.append("login response is missing the exact refresh-cookie pattern")
    for name in ("LoginResponse", "RefreshResponse"):
        access = (
            openapi.get("components", {}).get("schemas", {}).get(name, {})
            .get("properties", {}).get("data", {}).get("properties", {})
            .get("accessToken", {})
        )
        if access.get("writeOnly") is not None:
            errors.append(f"{name} accessToken must be modeled as a response value")

    tests = "\n".join(
        path.read_text(encoding="utf-8")
        for path in BACKEND.glob("**/src/test/java/**/*.java")
    )
    for marker in (
        "signerFailureRollsBackEveryDurableRow",
        "commitsSessionRefreshHashAndProjectionEventBeforeReturningTokens",
        "missingSecurityStateFailsClosedBeforeAnyCredentialIsCreated",
        "concurrentSessionLimitRejectsBeforeTokenOrOutboxCreation",
        "simultaneousIssuanceSerializesLimitAndCommitsExactlyOneSession",
        "refreshBufferIsCopyingDestroyableAndNeverRendered",
        "emitsVersionedKeyIdAndFull256BitRandomBody",
        "enabledConfigurationFailsClosedWithoutSigningCapability",
    ):
        if marker not in tests:
            errors.append(f"session issuance test evidence missing {marker}")

    require(
        ROOT / "docs/spec/46-transactional-login-session-and-token-issuance-freeze.md",
        (
            "commit before returning",
            "RFC 9700",
            "success-audit transaction",
            "correlation-only",
            "IAM_AUTH_SESSION_LIMIT_REACHED",
            "9223372036854775807",
        ),
        errors,
    )
    require(
        ROOT / "docs/api/ERROR-CODES.md",
        ("IAM_AUTH_SESSION_LIMIT_REACHED", "409"),
        errors,
    )
    for workflow in (
        ROOT / ".github/workflows/backend-build.yml",
        ROOT / ".github/workflows/contract-quality.yml",
    ):
        require(
            workflow,
            (
                "tools/validate_session_issuance.py",
                "Validate transactional login session issuance",
            ),
            errors,
        )


def main() -> int:
    errors: list[str] = []
    validate_signing_boundary(errors)
    validate_refresh_security(errors)
    validate_transaction(errors)
    validate_contract_and_tests(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "Session issuance validation passed: explicit signing/hash capabilities, "
        "256-bit opaque refresh tokens, commit-before-return JDBC transaction, "
        "security-state lock, concurrent session limit, exact response contracts, "
        "Outbox append, redaction, rollback and CI/spec evidence"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
