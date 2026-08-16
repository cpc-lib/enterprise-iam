#!/usr/bin/env python3
"""Deterministic structural and semantic checks for CODE-READY SPEC 1.10.1."""

from __future__ import annotations

import csv
import hashlib
import json
import pathlib
import re
import sys
from collections import Counter
from typing import Any

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
OPENAPI_FILES = sorted((ROOT / "docs/api").glob("openapi-code-phase-*.yaml"))
ASYNCAPI_FILES = sorted((ROOT / "docs/events").glob("asyncapi-code-phase-*.yaml"))
SECURITY = ROOT / "docs/security/SECURITY-PARAMETERS.yaml"
MANIFEST = ROOT / "docs/architecture/FINAL-FREEZE-MANIFEST.json"
TRACE = ROOT / "docs/architecture/REQUIREMENTS-TRACEABILITY.csv"
ACCEPTANCE_FILES = sorted((ROOT / "docs/testing").glob("CODE-PHASE-*-ACCEPTANCE.md"))
ID_PATTERN = r"^[1-9][0-9]{0,18}$"
ID_MAXIMUM = 9_223_372_036_854_775_807
REFRESH_COOKIE_PATTERN = (
    r"^IAM_REFRESH=rt1\.[A-Za-z0-9._:-]{1,128}\.[A-Za-z0-9_-]{43}; "
    r"Path=/api/v1/auth; Secure; HttpOnly; SameSite=Strict$"
)


def load_yaml(path: pathlib.Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = yaml.safe_load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def resolve_pointer(document: Any, ref: str) -> Any:
    if not ref.startswith("#/"):
        raise ValueError(f"external ref is not frozen: {ref}")
    current = document
    for token in ref[2:].split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or token not in current:
            raise ValueError(f"unresolved ref: {ref}")
        current = current[token]
    return current


def walk_refs(node: Any, document: dict[str, Any]) -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "$ref":
                resolve_pointer(document, value)
            else:
                walk_refs(value, document)
    elif isinstance(node, list):
        for value in node:
            walk_refs(value, document)


def validate_id_schema(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    schema = document.get("components", {}).get("schemas", {}).get("Id")
    if not isinstance(schema, dict):
        return ["components.schemas.Id is missing"]
    expected = {
        "type": "string",
        "pattern": ID_PATTERN,
        "maxLength": 19,
        "x-iam-numeric-maximum": ID_MAXIMUM,
    }
    for key, value in expected.items():
        if schema.get(key) != value:
            errors.append(f"Id.{key} must be {value!r}")
    return errors


def validate_login_contract(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    login = document.get("paths", {}).get("/api/v1/auth/login", {}).get("post")
    if login is None:
        return errors
    if login.get("x-iam-idempotency-policy") != "EXPLICITLY_EXCLUDED_NON_REPLAYABLE_SECRET":
        errors.append("login must declare its non-replayable-secret idempotency exclusion")
    if "409" not in login.get("responses", {}):
        errors.append("login must declare 409 for the concurrent-session limit")
    cookie = (
        login.get("responses", {}).get("200", {}).get("headers", {})
        .get("Set-Cookie", {}).get("schema", {})
    )
    if cookie.get("pattern") != REFRESH_COOKIE_PATTERN:
        errors.append("login Set-Cookie pattern differs from the frozen refresh-cookie boundary")

    schemas = document.get("components", {}).get("schemas", {})
    for name in ("LoginResponse", "RefreshResponse"):
        data = schemas.get(name, {}).get("properties", {}).get("data", {})
        properties = data.get("properties", {})
        access = properties.get("accessToken", {})
        if access.get("writeOnly") is not None:
            errors.append(f"{name}.data.accessToken must not use writeOnly in a response")
        required = set(data.get("required", []))
        if not {"accessToken", "tokenType"}.issubset(required):
            errors.append(f"{name}.data must require accessToken and tokenType")
    return errors


def validate_openapi(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if document.get("openapi") != "3.1.0":
        errors.append("OpenAPI version must be 3.1.0")
    try:
        walk_refs(document, document)
    except ValueError as exc:
        errors.append(str(exc))

    operation_ids: list[str] = []
    methods = {"get", "post", "put", "patch", "delete"}
    idempotency_exclusions = {"login", "checkAuthorization", "resolveEffectivePolicies"}
    for path, path_item in document.get("paths", {}).items():
        for method, operation in path_item.items():
            if method not in methods:
                continue
            operation_id = operation.get("operationId")
            if not operation_id:
                errors.append(f"{method.upper()} {path}: missing operationId")
                continue
            operation_ids.append(operation_id)
            if method in {"post", "put", "patch", "delete"} and operation_id not in idempotency_exclusions:
                parameters = operation.get("parameters", [])
                if not any(p.get("$ref", "").endswith("/IdempotencyKey") for p in parameters if isinstance(p, dict)):
                    errors.append(f"{operation_id}: mutation missing Idempotency-Key")
            if not operation.get("responses"):
                errors.append(f"{operation_id}: missing responses")
    duplicates = [value for value, count in Counter(operation_ids).items() if count > 1]
    if duplicates:
        errors.append(f"duplicate operationId: {duplicates}")
    errors.extend(validate_id_schema(document))
    errors.extend(validate_login_contract(document))
    return errors


def validate_asyncapi(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if document.get("asyncapi") != "3.1.0":
        errors.append("AsyncAPI version must be 3.1.0")
    try:
        walk_refs(document, document)
    except ValueError as exc:
        errors.append(str(exc))
    messages = document.get("components", {}).get("messages", {})
    if not messages:
        errors.append("AsyncAPI must define messages")
    for key, message in messages.items():
        name = message.get("name", "")
        if not name.endswith(".v1"):
            errors.append(f"{key}: message name must end in .v1")
    errors.extend(validate_id_schema(document))
    return errors


def validate_traceability() -> list[str]:
    errors: list[str] = []
    required = {
        "requirement_id", "requirement", "source", "api_or_event",
        "data_owner", "verification_id", "release_gate", "contract_status",
    }
    with TRACE.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if set(reader.fieldnames or []) != required:
            errors.append("traceability columns differ from frozen schema")
        rows = list(reader)
    ids = [row.get("requirement_id", "") for row in rows]
    if not rows or any(not value for value in ids):
        errors.append("traceability rows/IDs must not be empty")
    if len(ids) != len(set(ids)):
        errors.append("traceability requirement IDs must be unique")
    if any(not row.get("verification_id") for row in rows):
        errors.append("every requirement needs verification evidence IDs")
    acceptance_text = "\n".join(path.read_text(encoding="utf-8") for path in ACCEPTANCE_FILES)
    verification_ids = {
        value.strip()
        for row in rows
        for value in row.get("verification_id", "").split(";")
        if value.strip()
    }
    missing = sorted(value for value in verification_ids if value not in acceptance_text)
    if missing:
        errors.append(f"verification IDs missing from acceptance catalog: {missing}")
    return errors


def validate_freeze() -> list[str]:
    errors: list[str] = []
    security = load_yaml(SECURITY)
    if security.get("password", {}).get("minimumLength") != 15:
        errors.append("single-factor password minimum length must be 15")
    if security.get("authorization", {}).get("defaultDecision") != "DENY":
        errors.append("security default decision must be DENY")
    if security.get("jwtAccessToken", {}).get("acceptNoneAlgorithm") is not False:
        errors.append("JWT none algorithm must be forbidden")
    if security.get("policyEngine", {}).get("rawSqlAllowed") is not False:
        errors.append("policy engine must forbid raw SQL")
    if security.get("sharing", {}).get("crossTenantAllowed") is not False:
        errors.append("V1 sharing must forbid cross-tenant targets")
    if security.get("fileSecurity", {}).get("scanBeforeAccess") is not True:
        errors.append("file access must be scan gated")
    if security.get("fileSecurity", {}).get("storageObjectKeyFromUserFilename") is not False:
        errors.append("storage keys must not derive from user filenames")
    with MANIFEST.open(encoding="utf-8") as handle:
        manifest = json.load(handle)
    if manifest.get("spec_count") != 46:
        errors.append("manifest spec_count must be 46")
    actual_specs = list((ROOT / "docs/spec").glob("[0-9][0-9]-*.md"))
    if len(actual_specs) != 46:
        errors.append(f"actual numbered SPEC count must be 46, got {len(actual_specs)}")
    if manifest.get("contract_release") != "1.10.1":
        errors.append("manifest contract_release must be 1.10.1")
    if manifest.get("authoritative_spec") != "38-core-v1-authorization-machine-contract-freeze.md":
        errors.append("manifest must point to SPEC 38")
    if manifest.get("build_foundation_spec") != "39-backend-build-foundation-freeze.md":
        errors.append("manifest must point to SPEC 39 for build authority")
    if not (ROOT / "docs/spec/39-backend-build-foundation-freeze.md").is_file():
        errors.append("SPEC 39 build-foundation authority is missing")
    if manifest.get("phase01_core_spec") != "40-code-phase-01-security-core-implementation-freeze.md":
        errors.append("manifest must point to SPEC 40 for Phase-01 core authority")
    if not (ROOT / "docs/spec/40-code-phase-01-security-core-implementation-freeze.md").is_file():
        errors.append("SPEC 40 Phase-01 core authority is missing")
    if manifest.get("phase01_auth_crypto_spec") != "41-authentication-and-delegation-crypto-implementation-freeze.md":
        errors.append("manifest must point to SPEC 41 for authentication/crypto authority")
    if not (ROOT / "docs/spec/41-authentication-and-delegation-crypto-implementation-freeze.md").is_file():
        errors.append("SPEC 41 authentication/crypto authority is missing")
    if manifest.get("phase01_delegation_wiring_spec") != "42-delegation-key-rotation-and-service-wiring-freeze.md":
        errors.append("manifest must point to SPEC 42 for delegation wiring authority")
    if not (ROOT / "docs/spec/42-delegation-key-rotation-and-service-wiring-freeze.md").is_file():
        errors.append("SPEC 42 delegation wiring authority is missing")
    if manifest.get("phase01_access_authentication_spec") != "43-gateway-access-authentication-and-session-fence-freeze.md":
        errors.append("manifest must point to SPEC 43 for access authentication authority")
    if not (ROOT / "docs/spec/43-gateway-access-authentication-and-session-fence-freeze.md").is_file():
        errors.append("SPEC 43 access authentication authority is missing")
    if manifest.get("phase01_trust_adapters_spec") != "44-https-jwks-and-redis-session-projection-freeze.md":
        errors.append("manifest must point to SPEC 44 for trust-adapter authority")
    if not (ROOT / "docs/spec/44-https-jwks-and-redis-session-projection-freeze.md").is_file():
        errors.append("SPEC 44 trust-adapter authority is missing")
    if manifest.get("phase01_session_outbox_spec") != "45-session-projection-transactional-outbox-freeze.md":
        errors.append("manifest must point to SPEC 45 for session-outbox authority")
    if not (ROOT / "docs/spec/45-session-projection-transactional-outbox-freeze.md").is_file():
        errors.append("SPEC 45 session-outbox authority is missing")
    if manifest.get("phase01_session_issuance_spec") != "46-transactional-login-session-and-token-issuance-freeze.md":
        errors.append("manifest must point to SPEC 46 for session-issuance authority")
    if not (ROOT / "docs/spec/46-transactional-login-session-and-token-issuance-freeze.md").is_file():
        errors.append("SPEC 46 session-issuance authority is missing")
    for path in manifest.get("normative_machine_contracts", []):
        if not (ROOT / path).exists():
            errors.append(f"manifest target missing: {path}")
    for path in manifest.get("normative_build_contracts", []):
        if not (ROOT / path).exists():
            errors.append(f"manifest build target missing: {path}")
    hash_manifest = ROOT / manifest.get("content_hash_manifest", "")
    if not hash_manifest.is_file():
        errors.append("frozen-content SHA-256 manifest is missing")
    else:
        declared: dict[str, str] = {}
        for line_number, line in enumerate(
                hash_manifest.read_text(encoding="utf-8").splitlines(), start=1):
            if not line.strip():
                continue
            parts = line.split(None, 1)
            if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
                errors.append(f"invalid frozen hash line {line_number}")
                continue
            declared[parts[1]] = parts[0]

        frozen_paths: set[pathlib.Path] = set()
        targets = [
            *manifest.get("normative_machine_contracts", []),
            *manifest.get("normative_build_contracts", []),
            *manifest.get("content_hash_additions", []),
        ]
        for target in targets:
            candidate = ROOT / target
            if candidate.is_file():
                frozen_paths.add(candidate)
            elif candidate.is_dir():
                frozen_paths.update(path for path in candidate.rglob("*") if path.is_file())
            else:
                errors.append(f"frozen content target missing: {target}")
        expected_names = {path.relative_to(ROOT).as_posix() for path in frozen_paths}
        if set(declared) != expected_names:
            missing = sorted(expected_names - set(declared))
            extra = sorted(set(declared) - expected_names)
            errors.append(f"frozen hash inventory differs: missing={missing}, extra={extra}")
        for relative, expected_hash in declared.items():
            target = ROOT / relative
            if target.is_file():
                actual_hash = hashlib.sha256(target.read_bytes()).hexdigest()
                if actual_hash != expected_hash:
                    errors.append(f"frozen content hash mismatch: {relative}")
    return errors


def validate_ddl() -> list[str]:
    errors: list[str] = []
    ddl_root = ROOT / "docs/database"
    sql_files = sorted(ddl_root.glob("code-phase-*/*/*.sql"))
    if len(sql_files) < 7:
        errors.append("expected at least seven phased DDL files")
    tenant_exempt_tables = {"iam_tenant", "iam_file_object"}
    create_pattern = re.compile(
        r"CREATE\s+TABLE\s+([a-z0-9_]+)\s*\((.*?)\)\s*ENGINE=InnoDB",
        re.IGNORECASE | re.DOTALL,
    )
    for path in sql_files:
        text = path.read_text(encoding="utf-8")
        upper = text.upper()
        if "CREATE DATABASE" in upper or re.search(r"^\s*USE\s+", text, re.MULTILINE | re.IGNORECASE):
            errors.append(f"{path.name}: migrations must not create/select databases")
        if " REFERENCES " in upper:
            errors.append(f"{path.name}: cross-boundary FK risk; references are frozen out")
        statements = create_pattern.findall(text)
        if not statements:
            errors.append(f"{path.name}: no InnoDB CREATE TABLE statements found")
        for table, body in statements:
            if table.lower() not in tenant_exempt_tables and "tenant_id" not in body.lower():
                errors.append(f"{path.name}: tenant-owned table {table} lacks tenant_id")
    return errors


def main() -> int:
    errors: list[str] = []
    required_paths = [SECURITY, MANIFEST, TRACE, *OPENAPI_FILES, *ASYNCAPI_FILES, *ACCEPTANCE_FILES]
    if len(OPENAPI_FILES) < 3:
        errors.append("at least three phased OpenAPI contracts are required")
    if len(ASYNCAPI_FILES) < 2:
        errors.append("at least two phased AsyncAPI contracts are required")
    if len(ACCEPTANCE_FILES) < 2:
        errors.append("at least two phased acceptance catalogs are required")
    for path in required_paths:
        if not path.exists():
            errors.append(f"required artifact missing: {path.relative_to(ROOT)}")
    if errors:
        print("\n".join(f"ERROR: {value}" for value in errors))
        return 1

    all_operation_ids: list[str] = []
    for path in OPENAPI_FILES:
        openapi = load_yaml(path)
        errors.extend(f"{path.name}: {value}" for value in validate_openapi(openapi))
        for path_item in openapi.get("paths", {}).values():
            for method, operation in path_item.items():
                if method in {"get", "post", "put", "patch", "delete"} and operation.get("operationId"):
                    all_operation_ids.append(operation["operationId"])
    duplicate_operations = sorted(value for value, count in Counter(all_operation_ids).items() if count > 1)
    if duplicate_operations:
        errors.append(f"operation IDs duplicated across phase contracts: {duplicate_operations}")
    for path in ASYNCAPI_FILES:
        asyncapi = load_yaml(path)
        errors.extend(f"{path.name}: {value}" for value in validate_asyncapi(asyncapi))
    errors.extend(validate_traceability())
    errors.extend(validate_freeze())
    errors.extend(validate_ddl())
    if errors:
        print("\n".join(f"ERROR: {value}" for value in errors))
        return 1
    ddl_owner_roots = {
        "organization", "authorization", "sharing", "file"
    }
    ddl_root = ROOT / "docs/database/code-phase-02"
    existing_owners = {path.name for path in ddl_root.iterdir() if path.is_dir()} if ddl_root.exists() else set()
    missing_owners = sorted(ddl_owner_roots - existing_owners)
    if missing_owners:
        print(f"ERROR: missing CODE PHASE 02 DDL owners: {missing_owners}")
        return 1
    print("CODE-READY SPEC 1.10.1 validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
