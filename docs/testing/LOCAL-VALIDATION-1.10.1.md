# Local Validation Record — CODE-READY 1.10.1

Date: 2026-08-14 UTC

## Passed on this review host

| Check | Result |
|---|---|
| Nine deterministic Python validators | PASS |
| Redocly CLI 2.46.1 | PASS — all three OpenAPI 3.1 contracts |
| AsyncAPI deterministic parse and semantic checks | PASS — both AsyncAPI 3.1 contracts |
| AsyncAPI CLI 6.0.2 | PASS — both AsyncAPI 3.1 contracts, no governance issues |
| Maven POM XML parse | PASS — 31 POMs at `1.10.1-SNAPSHOT` |
| Java 21 / Maven full Reactor | PASS — all 31 modules, 160 Surefire tests, zero failures/errors/skips |
| Shared MySQL fixture delta | PARTIAL — JDK 21 compilation against API boundary stubs and a pure resolver harness passed; five new JUnit cases await Maven/CI execution |
| Full source compilation | PASS — 147 main and 60 test source files compiled with Java 21.0.12 |
| Runtime-service context smoke tests | PASS — Gateway plus all eight backend services |
| Full Reactor packaging | PASS — `mvn -DskipITs verify`; all 31 modules and nine executable Spring Boot JARs, integration tests explicitly skipped |
| Failsafe discovery and class path | PARTIAL — the prior auth run discovered all six tests and reached the database connection boundary; the current three-service/eight-test rerun remains open |
| JSON/YAML/CSV and UTF-8 text parse | PASS |
| Markdown local-link resolution | PASS — no broken local links |
| Canonical/runtime migration byte identity | PASS |
| Frozen normative-content SHA-256 inventory | PASS |

## Hardening evidence

- Access tokens are modeled as required response values, not `writeOnly` input fields.
- OpenAPI and AsyncAPI IDs freeze positive signed-64-bit decimal semantics.
- Login and refresh freeze the exact `IAM_REFRESH` cookie shape.
- The login session limit maps to HTTP `409` and
  `IAM_AUTH_SESSION_LIMIT_REACHED`.
- Login generic idempotent replay is explicitly excluded and `requestId` is
  correlation-only.
- Surefire and Failsafe use an explicit Mockito Java agent instead of depending
  on dynamic agent attachment.
- Failsafe uses the compiled classes directory instead of attempting to load
  application classes from the Spring Boot executable JAR layout.
- Gateway tests use typed attribute assertions, the current JWKS transport
  contract and a reactive mock application context, eliminating three
  full-Reactor compile/context defects.
- All nine application context tests use the current Actuator endpoint-access
  property; the deprecated boolean property is validator-forbidden.
- Auth, identity and authorization MySQL integration tests share one fixture
  supporting pinned `mysql:8.4.9` Testcontainers, service-specific
  `IAM_TEST_<SERVICE>_MYSQL_JDBC_URL` values, or one
  `IAM_TEST_MYSQL_JDBC_URL_TEMPLATE` containing exactly one `{database}`.
  `IAM_TEST_MYSQL_JDBC_URL` remains a backward-compatible auth-only alias.
- The simultaneous one-session-limit integration test compiles, is discovered
  by Failsafe and reaches the database connection boundary.

## Runtime evidence still open

- A current Maven/CI run of all eight integration tests: six auth tests plus
  identity and authorization Flyway repeatability. The prior auth run reached
  only the database connection boundary; no MySQL pass is claimed.
- Restoration of external Maven artifacts for the five new shared-fixture unit
  tests; source compilation against API boundary stubs and the independent JDK
  21 harness passed locally.
- Redis Testcontainers and multi-worker Outbox convergence tests.
- Docker Compose, live HTTPS, KMS/HSM and end-to-end login-to-Gateway tests.

This record is local build, unit-test and contract evidence. It does not close
the integration/CI portions of Gate B or certify production readiness.
