# CODE PHASE Entry Gate — V1.10.1

The gate is split deliberately. Architecture readiness does not imply that the
repository already builds or is ready for production.

## Gate A — Design Ready

- [x] Monorepo layout frozen
- [x] Runtime microservice set frozen
- [x] Java package naming frozen
- [x] Framework module naming frozen
- [x] Database ownership frozen
- [x] Share immediate-revoke security fence frozen
- [x] File service integrated
- [x] API/Domain/Repository boundaries frozen
- [x] Threat model and security release gate present
- [x] Observability/SLO/runbooks present
- [x] Implementation backlog present
- [x] CODE PHASE 01 OpenAPI contract present
- [x] CODE PHASE 01 event contract present
- [x] Security parameters frozen for development and test
- [x] CODE PHASE 01 physical DDL present
- [x] Requirement traceability matrix present
- [x] Phase 02 policy OpenAPI and DDL present
- [x] Phase 03 sharing/file OpenAPI and DDL present
- [x] Phase 02–05 event and acceptance contracts present

## Gate B — Build Ready

- [x] Every reactor module has a valid child `pom.xml`
- [ ] `mvn -B -ntp verify` succeeds from `backend/`
- [ ] OpenAPI and AsyncAPI lint pass in CI
- [ ] Flyway migrations run twice: first applies, second is a no-op
- [ ] Docker Compose dependencies report healthy
- [ ] No production-default secret exists in source or images
- [ ] Golden authorization tests run against real MySQL and Redis
- [ ] Dependency, secret and container scans meet the release policy

No document may mark Gate B complete before CI provides the evidence link.

### Current evidence

- `python tools/validate_build_foundation.py`: PASS — 31 artifacts and nine
  service launchers are structurally closed.
- `python tools/validate_code_ready_spec.py`: PASS — machine-contract structure
  remains consistent.
- Redocly CLI 2.46.1 passed all three OpenAPI 3.1 contracts and AsyncAPI CLI
  6.0.2 passed both AsyncAPI 3.1 contracts locally. The CI checkbox remains open
  until a workflow run URL proves both commands.
- `mvn -B -ntp test`: PASS on Java 21.0.12 and Maven 3.9.11 — all 31 Reactor
  modules and 160 Surefire tests, with zero failures, errors or skips. This run
  includes the reactive Gateway context and all eight backend service contexts.
- `mvn -B -ntp -DskipITs verify`: PASS — all 31 modules completed and all nine
  runtime services produced executable Spring Boot JARs with the expected start
  class. Because integration tests were explicitly skipped, the unchecked
  `mvn verify` Gate B item remains open.
- The auth `verify` run discovered all six Failsafe integration tests and loaded
  application classes from the compiled output directory. Execution stopped at
  the MySQL connection boundary because this host denies Unix domain socket
  creation and has no Docker runtime. This is wiring evidence, not an
  integration-test pass or a closed Gate B.
- The current shared-fixture delta compiles under JDK 21 against API boundary
  stubs, and its independent harness covers service precedence, URL-template
  substitution, legacy auth
  isolation and invalid-input rejection. Five corresponding JUnit cases were
  added, but the current environment could not restore external Maven artifacts;
  a current full-Reactor and eight-test Failsafe rerun therefore remains open.
- Flyway, Docker Compose, golden authorization and scan items remain open.

### Phase-01 core implementation evidence

- `python tools/validate_phase01_core.py`: PASS — fail-closed authorization
  precedence, delegation policy, highest-order Gateway header sanitization and
  four runtime/canonical migration pairs are structurally verified.
- `python tools/validate_auth_crypto.py`: PASS — ES256 signing/verification,
  downstream rejection filter, Argon2id PHC parameters and enumeration-resistant
  login paths are structurally verified.
- `python tools/validate_delegation_wiring.py`: PASS — bounded rotating JWKS,
  Servlet auto-configuration and explicit Gateway route/audience policy are
  structurally verified.
- `python tools/validate_access_authentication.py`: PASS — strict external
  `at+jwt`, authoritative session/version fencing and ordered Gateway trust
  establishment are structurally verified.
- `python tools/validate_trust_adapters.py`: PASS — exact-host bounded HTTPS
  JWKS transport and shared strict/monotonic Redis session projection adapters
  are structurally verified.
- `python tools/validate_session_projection_outbox.py`: PASS — active-transaction
  append, leased relay, strict auth event, V2 migration and recovery markers are
  structurally verified.
- `python tools/validate_session_issuance.py`: PASS — commit-before-return JDBC
  issuance, locked security version/session limit, refresh HMAC, atomic Outbox,
  strict opt-in configuration, exact HTTP contract semantics, simultaneous
  issuance test source and rollback evidence are structurally verified.
- Three `FlywayMigrationIT` suites use one service-isolated fixture with pinned
  MySQL 8.4.9 containers, service-specific
  `IAM_TEST_<SERVICE>_MYSQL_JDBC_URL` values, or a single
  `IAM_TEST_MYSQL_JDBC_URL_TEMPLATE` containing exactly one `{database}`. The
  original `IAM_TEST_MYSQL_JDBC_URL` remains an auth-only compatibility alias;
  none of these modes is pass evidence until a successful MySQL run completes.
- Local static inventory: 148 main Java sources, 60 unit/integration test sources,
  four runtime Flyway migrations and 46 SPEC documents. This inventory is
  completeness evidence, not compilation evidence.
- `SEC-TEN-001` and `SEC-FAILCLOSED-001` remain PARTIAL. The full-Reactor Java
  21 unit report is local evidence only; production/runtime gates still require
  immutable CI and live dependency proof.
- Detailed status: `docs/testing/CODE-PHASE-01-IMPLEMENTATION-EVIDENCE.md`.

## CODE PHASE 01 Goal

```text
Tenant/User/Role/Resource/Operation
→ Login
→ Gateway
→ Authorization
→ Protected Demo API
→ Grant ALLOW
→ Revoke Immediate DENY
→ Audit Trace
```

Exit evidence is defined in `docs/spec/37-code-ready-implementation-contract-freeze.md`.
Core V1 authorization extension gates are defined in
`docs/spec/38-core-v1-authorization-machine-contract-freeze.md`.
Backend build-foundation gates are defined in
`docs/spec/39-backend-build-foundation-freeze.md`.
Implemented Phase-01 security-core boundaries are defined in
`docs/spec/40-code-phase-01-security-core-implementation-freeze.md`.
Authentication/delegation crypto boundaries are defined in
`docs/spec/41-authentication-and-delegation-crypto-implementation-freeze.md`.
Delegation key-rotation/service-wiring boundaries are defined in
`docs/spec/42-delegation-key-rotation-and-service-wiring-freeze.md`.
Gateway access-authentication/session-fence boundaries are defined in
`docs/spec/43-gateway-access-authentication-and-session-fence-freeze.md`.
HTTPS JWKS and Redis session-projection adapter boundaries are defined in
`docs/spec/44-https-jwks-and-redis-session-projection-freeze.md`.
Session projection transactional-outbox boundaries are defined in
`docs/spec/45-session-projection-transactional-outbox-freeze.md`.
Transactional login-session/token issuance boundaries are defined in
`docs/spec/46-transactional-login-session-and-token-issuance-freeze.md`.
