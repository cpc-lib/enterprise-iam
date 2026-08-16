# Local Validation Record — CODE-READY 1.9

Date: 2026-08-12 UTC

## Passed on the packaging host

| Check | Result |
|---|---|
| Eight deterministic Python validators | PASS |
| Java grammar parse | PASS — 186 source/test files |
| Maven POM XML parse | PASS — 31 POMs |
| JSON/YAML/CSV and UTF-8 text parse | PASS |
| Canonical/runtime auth V2 byte identity | PASS |
| Redocly CLI 2.46.1 | PASS — 3 OpenAPI 3.1 contracts |
| AsyncAPI CLI 6.0.2 | PASS — 2 AsyncAPI 3.1 contracts; no governance issues |
| Inventory | 133 main Java, 53 test Java, 4 runtime migrations, 45 SPECs |

The eight deterministic validators are:

1. `validate_build_foundation.py`
2. `validate_code_ready_spec.py`
3. `validate_phase01_core.py`
4. `validate_auth_crypto.py`
5. `validate_delegation_wiring.py`
6. `validate_access_authentication.py`
7. `validate_trust_adapters.py`
8. `validate_session_projection_outbox.py`

## Not executed on the packaging host

- `mvn -B -ntp verify`
- Java 21 compilation and JUnit/application-context execution
- MySQL/Redis Testcontainers and Flyway runtime execution
- Docker Compose, live HTTPS, concurrency, crash/lease and end-to-end tests

The host has a Java 17 runtime only and does not provide Maven, `javac` or
Docker. Therefore this record is structural/contract evidence, not a Gate B
build pass or production-readiness certificate.
