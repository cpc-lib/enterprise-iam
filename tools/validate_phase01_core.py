#!/usr/bin/env python3
"""Static evidence checks for the CODE PHASE 01 security-core slice."""

from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

MIGRATIONS = {
    "identity": (
        ROOT / "docs/database/code-phase-01/identity/V1__identity_baseline.sql",
        BACKEND / "iam-identity-service/src/main/resources/db/migration/V1__identity_baseline.sql",
    ),
    "auth": (
        ROOT / "docs/database/code-phase-01/auth/V1__auth_baseline.sql",
        BACKEND / "iam-auth-service/src/main/resources/db/migration/V1__auth_baseline.sql",
    ),
    "auth-outbox": (
        ROOT / "docs/database/code-phase-01/auth/V2__session_projection_outbox.sql",
        BACKEND
        / "iam-auth-service/src/main/resources/db/migration"
        / "V2__session_projection_outbox.sql",
    ),
    "authorization": (
        ROOT / "docs/database/code-phase-01/authorization/V1__authorization_rbac_baseline.sql",
        BACKEND
        / "iam-authorization-service/src/main/resources/db/migration"
        / "V1__authorization_rbac_baseline.sql",
    ),
}

PERSISTENCE_MODULES = (
    "iam-identity-service",
    "iam-auth-service",
    "iam-authorization-service",
)

MYSQL_FIXTURE_CALLS = {
    "iam-identity-service": 'MySqlIntegrationDatabase.start("identity", "iam_identity")',
    "iam-auth-service": 'MySqlIntegrationDatabase.start("auth", "iam_auth")',
    "iam-authorization-service": (
        'MySqlIntegrationDatabase.start("authorization", "iam_authorization")'
    ),
}


def validate_migrations(errors: list[str]) -> None:
    for owner, (authority, runtime) in MIGRATIONS.items():
        if not authority.is_file() or not runtime.is_file():
            errors.append(f"{owner}: canonical or runtime migration is missing")
            continue
        if authority.read_bytes() != runtime.read_bytes():
            errors.append(f"{owner}: runtime migration differs byte-for-byte from canonical DDL")

    required_dependencies = {
        "spring-boot-starter-jdbc",
        "flyway-core",
        "flyway-mysql",
        "mysql-connector-j",
        "spring-boot-starter-test",
    }
    for module in PERSISTENCE_MODULES:
        pom = BACKEND / module / "pom.xml"
        node = ET.parse(pom).getroot()
        dependencies = {
            value.text.strip()
            for value in node.findall("m:dependencies/m:dependency/m:artifactId", NS)
        }
        missing = sorted(required_dependencies - dependencies)
        if missing:
            errors.append(f"{module}: persistence dependencies missing: {missing}")
        testcontainers_dependencies = {
            dependency.findtext("m:artifactId", default="", namespaces=NS).strip()
            for dependency in node.findall("m:dependencies/m:dependency", NS)
            if dependency.findtext("m:groupId", default="", namespaces=NS).strip()
            == "org.testcontainers"
        }
        if testcontainers_dependencies:
            errors.append(
                f"{module}: Testcontainers dependencies must be centralized in "
                f"iam-test-support: {sorted(testcontainers_dependencies)}"
            )

        config = yaml.safe_load(
            (BACKEND / module / "src/main/resources/application.yml").read_text(encoding="utf-8")
        )
        datasource = config.get("spring", {}).get("datasource", {})
        flyway = config.get("spring", {}).get("flyway", {})
        if datasource.get("url") != "${DB_URL}":
            errors.append(f"{module}: DB_URL must be required with no source default")
        if datasource.get("username") != "${DB_USERNAME}":
            errors.append(f"{module}: DB_USERNAME must be required with no source default")
        if datasource.get("password") != "${DB_PASSWORD}":
            errors.append(f"{module}: DB_PASSWORD must be required with no source default")
        expected_flyway = {
            "enabled": True,
            "locations": "classpath:db/migration",
            "clean-disabled": True,
            "validate-on-migrate": True,
            "out-of-order": False,
            "baseline-on-migrate": False,
        }
        for key, expected in expected_flyway.items():
            if flyway.get(key) != expected:
                errors.append(f"{module}: spring.flyway.{key} must be {expected!r}")

        integration_tests = list((BACKEND / module / "src/test/java").glob("**/FlywayMigrationIT.java"))
        if len(integration_tests) != 1:
            errors.append(f"{module}: exactly one FlywayMigrationIT is required")
        else:
            integration_source = integration_tests[0].read_text(encoding="utf-8")
            expected_fixture_call = MYSQL_FIXTURE_CALLS[module]
            if expected_fixture_call not in integration_source:
                errors.append(
                    f"{module}: Flyway suite missing isolated fixture call "
                    f"{expected_fixture_call}"
                )
            for marker in (
                "MigrateResult first = flyway.migrate()",
                "MigrateResult second = flyway.migrate()",
                "second.migrationsExecuted).isZero()",
                "validateWithResult().validationSuccessful",
            ):
                if marker not in integration_source:
                    errors.append(f"{module}: Flyway integration evidence missing {marker}")

    fixture = (
        BACKEND
        / "iam-test-support/src/main/java/com/enterprise/iam/testsupport/database"
        / "MySqlIntegrationDatabase.java"
    )
    if not fixture.is_file():
        errors.append("shared MySQL integration fixture is missing")
    else:
        fixture_source = fixture.read_text(encoding="utf-8")
        for marker in (
            'DockerImageName.parse("mysql:8.4.9")',
            "IAM_TEST_MYSQL_JDBC_URL_TEMPLATE",
            "IAM_TEST_MYSQL_JDBC_URL",
            '"IAM_TEST_"',
            '"_MYSQL_"',
            "mysql.start()",
            '"{database}"',
        ):
            if marker not in fixture_source:
                errors.append(f"shared MySQL integration fixture missing {marker}")

    test_support_pom = ET.parse(BACKEND / "iam-test-support/pom.xml").getroot()
    test_support_dependencies = {
        value.text.strip()
        for value in test_support_pom.findall(
            "m:dependencies/m:dependency/m:artifactId", NS
        )
    }
    if "mysql" not in test_support_dependencies:
        errors.append("iam-test-support: Testcontainers MySQL dependency is missing")


def validate_authorization_core(errors: list[str]) -> None:
    domain_root = (
        BACKEND
        / "iam-authorization-service/src/main/java/com/enterprise/iam/authorization/domain"
    )
    required = [
        domain_root / "model/AuthorizationRequest.java",
        domain_root / "model/AuthorizationFacts.java",
        domain_root / "model/ResolvedGrant.java",
        domain_root / "model/AuthorizationResult.java",
        domain_root / "service/DefaultAuthorizationEngine.java",
    ]
    for path in required:
        if not path.is_file():
            errors.append(f"authorization core missing: {path.relative_to(ROOT)}")
    if errors:
        return

    for path in domain_root.glob("**/*.java"):
        source = path.read_text(encoding="utf-8")
        forbidden = ("org.springframework", "org.mybatis", "com.baomidou.mybatisplus", ".infrastructure.")
        if any(value in source for value in forbidden):
            errors.append(f"{path.relative_to(ROOT)}: domain imports a forbidden adapter/framework")

    engine = (domain_root / "service/DefaultAuthorizationEngine.java").read_text(encoding="utf-8")
    precedence = [
        "!facts.authoritativeAvailable()",
        "facts.tenantId() != request.tenantId()",
        "facts.resourceId() != request.resourceId()",
        "request.permissionVersion() < facts.authoritativePermissionVersion()",
        "request.permissionVersion() > facts.authoritativePermissionVersion()",
        "!facts.resourceOperationEnabled()",
        "explicitDenies.isEmpty()",
        "allows.isEmpty()",
    ]
    positions = [engine.find(marker) for marker in precedence]
    if any(position < 0 for position in positions):
        errors.append("authorization engine is missing a frozen fail-closed precedence marker")
    elif positions != sorted(positions):
        errors.append("authorization engine fail-closed precedence changed")
    if "AuthorizationDecision.ALLOW" not in engine or "AuthorizationReason.GRANT_ALLOW" not in engine:
        errors.append("authorization engine has no explicit exact-grant ALLOW branch")

    java_tests = "\n".join(
        path.read_text(encoding="utf-8") for path in BACKEND.glob("**/src/test/java/**/*.java")
    )
    evidence_ids = {
        "SEC-TEN-001",
        "SEC-FAILCLOSED-001",
        "PROP-AUTHZ-001",
        "PROP-AUTHZ-003",
    }
    missing_evidence = sorted(value for value in evidence_ids if value not in java_tests)
    if missing_evidence:
        errors.append(f"implemented test evidence IDs missing: {missing_evidence}")


def validate_trusted_context(errors: list[str]) -> None:
    security = yaml.safe_load(
        (ROOT / "docs/security/SECURITY-PARAMETERS.yaml").read_text(encoding="utf-8")
    )
    delegation = security.get("jwtDelegationToken", {})
    expected = {
        "algorithmAllowlist": ["ES256"],
        "requiredTyp": "iam-delegation+jwt",
        "ttlSeconds": 30,
        "maximumClockSkewSeconds": 5,
        "keyIdRequired": True,
        "audienceCardinality": 1,
        "externalTokenAcceptedByDownstream": False,
        "missingOrInvalidBehavior": "DENY",
    }
    for key, value in expected.items():
        if delegation.get(key) != value:
            errors.append(f"jwtDelegationToken.{key} must be {value!r}")

    policy = (
        BACKEND
        / "iam-framework/iam-common-security/src/main/java"
        / "com/enterprise/iam/common/security/delegation/DelegationTokenPolicy.java"
    )
    gateway_filter = (
        BACKEND
        / "iam-gateway/src/main/java/com/enterprise/iam/gateway/security"
        / "ExternalIdentityHeaderSanitizingFilter.java"
    )
    for path in (policy, gateway_filter):
        if not path.is_file():
            errors.append(f"trusted-context implementation missing: {path.relative_to(ROOT)}")
    if policy.is_file():
        source = policy.read_text(encoding="utf-8")
        for marker in (
            "INVALID_SIGNATURE",
            "ALGORITHM_NOT_ALLOWED",
            "INVALID_AUDIENCE",
            "Set.of(expectedAudience).equals(claims.audience())",
            "TOKEN_EXPIRED",
            "TOKEN_TTL_EXCEEDED",
            "MISSING_REQUIRED_CONTEXT",
        ):
            if marker not in source:
                errors.append(f"delegation policy missing fail-closed marker: {marker}")
    if gateway_filter.is_file():
        source = gateway_filter.read_text(encoding="utf-8")
        if "Ordered.HIGHEST_PRECEDENCE" not in source or "UntrustedIdentityHeaders" not in source:
            errors.append("gateway identity-header sanitization is not highest precedence")


def main() -> int:
    errors: list[str] = []
    validate_migrations(errors)
    validate_authorization_core(errors)
    validate_trusted_context(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "CODE PHASE 01 core validation passed: authorization precedence, trusted context, "
        "gateway header sanitization and four canonical Flyway migrations"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
