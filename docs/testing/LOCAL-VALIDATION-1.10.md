# Local Validation Record — CODE-READY 1.10

Date: 2026-08-12 UTC

## Passed on the packaging host

| Check | Result |
|---|---|
| Nine deterministic Python validators | PASS |
| Java grammar parse | PASS — 206 source/test files |
| Maven POM XML parse | PASS — 31 POMs |
| JSON/YAML/CSV and UTF-8 text parse | PASS |
| Canonical/runtime migration byte identity | PASS |
| Redocly CLI | NOT RE-RUN — host tool-quota policy rejected CLI execution; YAML and deterministic contract checks pass |
| AsyncAPI CLI | NOT RE-RUN — same host restriction; contracts are unchanged from the 1.9 local pass |
| Inventory | 147 main Java, 59 test Java, 4 runtime migrations, 46 SPECs |

The nine deterministic validators are:

1. `validate_build_foundation.py`
2. `validate_code_ready_spec.py`
3. `validate_phase01_core.py`
4. `validate_auth_crypto.py`
5. `validate_delegation_wiring.py`
6. `validate_access_authentication.py`
7. `validate_trust_adapters.py`
8. `validate_session_projection_outbox.py`
9. `validate_session_issuance.py`

## Not executed on the packaging host

- `mvn -B -ntp verify`
- Redocly/AsyncAPI CLI rerun for the 1.10 package (host tool-quota restriction)
- Java 21 compilation and JUnit/application-context execution
- MySQL/Redis Testcontainers and Flyway runtime execution
- Docker Compose, live HTTPS, KMS/HSM, concurrency and end-to-end tests

The host has a Java 17 runtime only and does not provide Maven, `javac` or
Docker. Therefore this record is structural/contract evidence, not a Gate B
build pass or production-readiness certificate.
