#!/usr/bin/env python3
"""Static contract checks for the session-projection transactional outbox slice."""

from __future__ import annotations

import pathlib
import sys

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"


def require_file(path: pathlib.Path, markers: tuple[str, ...], errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"missing: {path.relative_to(ROOT)}")
        return
    source = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            errors.append(f"{path.relative_to(ROOT)}: missing marker {marker!r}")


def validate_migration(errors: list[str]) -> None:
    canonical = ROOT / "docs/database/code-phase-01/auth/V2__session_projection_outbox.sql"
    runtime = (
        BACKEND
        / "iam-auth-service/src/main/resources/db/migration"
        / "V2__session_projection_outbox.sql"
    )
    if not canonical.is_file() or not runtime.is_file():
        errors.append("auth session-projection outbox canonical/runtime V2 migration is missing")
        return
    if canonical.read_bytes() != runtime.read_bytes():
        errors.append("auth runtime V2 migration differs byte-for-byte from canonical DDL")
    source = runtime.read_text(encoding="utf-8")
    for marker in (
        "CREATE TABLE sys_outbox_event",
        "UNIQUE KEY uk_outbox_event (event_id)",
        "idx_outbox_relay (event_status, next_retry_at, id)",
        "idx_outbox_claim (event_status, claim_until, id)",
        "ck_outbox_claim_pair",
        "ck_outbox_published_at",
        "CHECK (retry_count <= 20)",
    ):
        if marker not in source:
            errors.append(f"auth V2 outbox migration missing {marker}")


def validate_foundation(errors: list[str]) -> None:
    root = (
        BACKEND
        / "iam-framework/iam-outbox-spring-boot-starter/src/main/java"
        / "com/enterprise/iam/outbox"
    )
    required = {
        "JdbcOutboxWriter.java": (
            "isActualTransactionActive()",
            "isCurrentTransactionReadOnly()",
            "INSERT INTO sys_outbox_event",
        ),
        "JdbcOutboxRepository.java": (
            "PROPAGATION_REQUIRES_NEW",
            "ISOLATION_READ_COMMITTED",
            "FOR UPDATE SKIP LOCKED",
            "claim_owner = ?",
        ),
        "OutboxRelay.java": (
            "UNSUPPORTED_EVENT_SCHEMA",
            "OutboxNonRetryableException",
            "RETRY_SCHEDULED",
            "LEASE_LOST",
        ),
        "OutboxRetryPolicy.java": ("deterministic ±20%", "maximumMillis"),
        "MicrometerOutboxRelayObserver.java": (
            '"iam.outbox.claimed"',
            '"iam.outbox.delivery"',
            '.tag("result", result)',
        ),
        "OutboxAutoConfiguration.java": ("@AutoConfiguration", "OutboxWriter"),
    }
    for name, markers in required.items():
        require_file(root / name, markers, errors)
    auto_import = (
        BACKEND
        / "iam-framework/iam-outbox-spring-boot-starter/src/main/resources"
        / "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    )
    require_file(auto_import, ("com.enterprise.iam.outbox.OutboxAutoConfiguration",), errors)


def validate_auth_adapter(errors: list[str]) -> None:
    root = (
        BACKEND
        / "iam-auth-service/src/main/java/com/enterprise/iam/auth/infrastructure/outbox"
    )
    required = {
        "SessionProjectionEventCodec.java": (
            "FAIL_ON_UNKNOWN_PROPERTIES",
            "FAIL_ON_NULL_FOR_PRIMITIVES",
            "FAIL_ON_TRAILING_TOKENS",
            "ACCEPT_FLOAT_AS_INT",
            "ALLOW_COERCION_OF_SCALARS",
            "STRICT_DUPLICATE_DETECTION",
        ),
        "JdbcSessionProjectionOutboxAppender.java": (
            'EVENT_TYPE = "iam.auth.session-security-projection"',
            'AGGREGATE_TYPE = "LOGIN_SESSION"',
            "projection.sessionVersion()",
        ),
        "SessionProjectionOutboxEventHandler.java": (
            "INVALID_EVENT_PAYLOAD",
            "INVALID_EVENT_METADATA",
            "publisher.publish(projection)",
        ),
        "SessionProjectionOutboxConfiguration.java": (
            "ObjectProvider<OutboxWriter>",
            "@ConditionalOnBean(SessionSecurityProjectionPublisher.class)",
        ),
    }
    for name, markers in required.items():
        require_file(root / name, markers, errors)

    tests = "\n".join(
        path.read_text(encoding="utf-8")
        for path in BACKEND.glob("**/src/test/java/**/*Outbox*Test.java")
    )
    for marker in (
        "already-active business transaction",
        "INVALID_EVENT_PAYLOAD",
        "INVALID_EVENT_METADATA",
        "UNSUPPORTED_EVENT_SCHEMA",
        "RETRY_SCHEDULED",
        "duplicate",
    ):
        if marker not in tests:
            errors.append(f"focused outbox tests missing evidence marker {marker}")

    config = yaml.safe_load(
        (BACKEND / "iam-auth-service/src/main/resources/application.yml")
        .read_text(encoding="utf-8")
    )
    relay = config.get("iam", {}).get("outbox", {}).get("relay", {})
    if relay.get("enabled") != "${IAM_OUTBOX_RELAY_ENABLED:false}":
        errors.append("auth outbox relay must remain disabled by default")


def validate_release_contract(errors: list[str]) -> None:
    require_file(
        ROOT / "docs/spec/45-session-projection-transactional-outbox-freeze.md",
        (
            "at-least-once",
            "FOR UPDATE SKIP LOCKED",
            "must still call",
            "SPEC 46 already supplies the JDBC login producer",
        ),
        errors,
    )
    require_file(
        ROOT / "docs/operations/runbooks/outbox-backlog.md",
        ("expired leases", "SKIP LOCKED", "bulk-edit statuses"),
        errors,
    )
    require_file(
        ROOT / "docs/operations/runbooks/dlq-growth.md",
        ("last_error_code", "authorized, auditable replay", "Never bulk-change"),
        errors,
    )
    require_file(
        ROOT / "docs/observability/METRIC-CATALOG.csv",
        (
            "iam_outbox_claimed_total",
            "iam_outbox_delivery_total",
            "iam_outbox_delivery_duration_seconds",
        ),
        errors,
    )
    for workflow in (
        ROOT / ".github/workflows/backend-build.yml",
        ROOT / ".github/workflows/contract-quality.yml",
    ):
        require_file(
            workflow,
            (
                "tools/validate_session_projection_outbox.py",
                "Validate session projection transactional outbox",
            ),
            errors,
        )


def main() -> int:
    errors: list[str] = []
    validate_migration(errors)
    validate_foundation(errors)
    validate_auth_adapter(errors)
    validate_release_contract(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "Session projection outbox validation passed: transaction-enforced append, "
        "leased SKIP LOCKED relay, bounded retry/dead-letter behavior, strict payload "
        "handling, Redis idempotence, migration and CI/spec evidence"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
