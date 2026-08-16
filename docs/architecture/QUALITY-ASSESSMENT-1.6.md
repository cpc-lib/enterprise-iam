# CODE-READY Quality Assessment — 1.6

## Result

| Dimension | 1.5 | 1.6 | Evidence status |
|---|---:|---:|---|
| Architecture/business design | 92% | 93% | SPEC 42 freezes key rotation and service trust wiring |
| Core authorization contract readiness | 93–95% | 94–95% | Existing decision core retained; internal caller trust is stronger |
| Reactor structural completeness | 97% | 97% | 31 POMs remain closed; starter boundary is corrected |
| Executed Java/Maven evidence | 0% local | 0% local | Static/syntax evidence only; CI execution remains required |
| Phase-01 security implementation | 50–55% | 60–65% | JWKS cache, auto-configuration and Gateway issuance filter added |
| Production key/service integration | ~20% | ~35% | Component wiring exists; KMS/JWKS transport and deployment remain |
| Production readiness | ~47% | ~48% | Fail-open risks reduced; end-to-end and operations proof absent |

Percentages are bounded engineering estimates, not release or coverage metrics.

## Verified locally

- Rotating JWKS resolver enforces size/key-count limits and explicit P-256,
  signing-use, ES256 and verification-operation metadata.
- Duplicate usable `kid`, private/wrong-algorithm-only sets and malformed or
  oversized documents fail closed.
- Unknown keys share a global refresh budget and rotation-safe negative cache;
  its memory is capped and an expired cache does not mask loader failure.
- Common crypto code has no Servlet/Spring Web coupling.
- Servlet auto-configuration is opt-in, exact-audience and restricted to
  `/internal` path patterns.
- Downstream delegation accepts exactly one expected audience and strictly
  parses numeric identity claims without truncation.
- Gateway routes must be explicitly protected or public; missing policy denies.
- External access tokens and forged delegation headers are removed before
  downstream forwarding.
- No private signing key or source default secret was added.

## CI execution required

- Java 21 compilation and all JUnit/ArchUnit suites.
- Spring Boot auto-configuration context-runner execution.
- Nimbus JWKS and Gateway reactive filter runtime tests.
- Maven Reactor, MySQL 8.4.9 Testcontainers and published test reports.

## Remaining blockers

- Gateway external JWT verification and session-state checks.
- KMS/HSM-backed signer lifecycle and key rotation.
- Hardened HTTPS JWKS transport with SSRF/DNS/redirect/time/size controls.
- Real Gateway routes and fully enumerated route security policy.
- Deployment-enabled downstream filters and key loader configuration.
- Login persistence/rate/session/token/HTTP integration.
- Authorization repository/endpoint, grant/revoke/outbox and golden path.

## Release statement

Version 1.6 materially improves service-level trust wiring and removes two
important fail-open classes. It remains an implementation seed, not a runnable
or production IAM system.
