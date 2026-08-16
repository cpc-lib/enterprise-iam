# CODE-READY Quality Assessment — 1.5

## Result

| Dimension | 1.4 | 1.5 | Evidence status |
|---|---:|---:|---|
| Architecture/business design | 91% | 92% | SPEC 41 freezes login and delegation trust boundaries |
| Core authorization contract readiness | 92–94% | 93–95% | Previous fail-closed engine retained; internal auth boundary is clearer |
| Reactor structural completeness | 96% | 97% | 31 POMs at one version; crypto/auth dependencies and CI gate added |
| Executed Java/Maven build evidence | 0% local | 0% local | Static validation passes; compilation/runtime require CI |
| Phase-01 security implementation | 35–40% | 50–55% | Real ES256 codec/filter and Argon2id/login core added |
| Production key/login integration | ~5% | ~20% | Ports exist; KMS/JWKS, adapters, controllers and session flow remain |
| Production readiness | ~46% | ~47% | Security core improved; golden path and operational proof absent |

Percentages are bounded engineering assessments, not release metrics. A source
test or CI workflow is not a passed test until an immutable run report exists.

## Verified locally

- Prior Reactor, contract, DDL and authorization static gates remain intact.
- ES256 is fixed before key resolution; P-256, `typ`, `kid`, issuer, audience,
  signature, TTL and required context are all fail-closed.
- Downstream context is published only after decoder success; invalid input gets
  one generic no-store 401 without the internal validation reason; resolver
  outages remain fail-closed and surface as a non-leaking 503.
- Argon2id constants match `SECURITY-PARAMETERS.yaml`, require PHC and normalize
  raw passwords to NFC.
- Unknown identities and missing/mismatched credentials use explicit dummy
  database/password paths; public login results do not carry internal reasons.
- Password `char[]` copies are zeroed on all use-case exits and commands are
  one-shot; temporary immutable strings created by the encoder remain a stated
  JVM limitation rather than a full-memory-erasure claim.
- No production private key, password, database credential or signing secret was
  added to source.

## CI execution required

- Java 21 compilation and all JUnit/ArchUnit suites.
- Nimbus/Bouncy Castle dependency resolution through the complete Maven Reactor.
- MySQL 8.4.9 Testcontainers first-migrate/second-no-op/validate suites.
- Published Surefire/Failsafe reports and OpenAPI/AsyncAPI workflow URL.

## Remaining blockers

- Gateway access-token verification and actual delegation issuance are absent.
- External KMS/HSM signing and rotating JWKS resolution/cache are absent.
- Downstream filters are not yet registered per service/audience/path.
- Identity/credential MyBatis adapters and their enumeration-resistant dummy SQL
  are absent.
- Login rate limiting, lock updates, session/refresh/access token persistence and
  REST mapping are absent.
- Authorization repositories/endpoints, grant/revoke/outbox and the MySQL/Redis
  golden path are absent.
- Docker Compose health, scanners, SLO/capacity and DR evidence remain open.

## Release statement

Version 1.5 is a higher-quality implementation seed with real cryptography and a
carefully bounded login core. It is still not a working or production-ready IAM
backend.
