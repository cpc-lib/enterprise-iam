# CODE-READY Quality Assessment — 1.7

## Result

| Dimension | 1.6 | 1.7 | Evidence status |
|---|---:|---:|---|
| Architecture/business design | 93% | 94% | SPEC 43 freezes external-to-internal trust transition |
| Core authorization contract readiness | 94–95% | 94–95% | Decision core unchanged; caller identity is stronger |
| Reactor structural completeness | 97% | 97% | 31 POMs remain closed; common crypto stays web-neutral |
| Executed Java/Maven evidence | 0% local | 0% local | Syntax/static evidence only; CI remains required |
| Phase-01 security implementation | 60–65% | 70–75% | Access JWT and authoritative session fence added |
| Production key/session integration | ~35% | ~45% | Ports and exact comparison exist; adapters/deployment do not |
| Production readiness | ~48% | ~52% | Major fail-open gap closed at component level; E2E proof absent |

Percentages are bounded engineering estimates, not coverage or release metrics.

## Verified locally

- Access and delegation token profiles are cryptographically and semantically
  separated by `typ`, audience and profile-specific key resolver types.
- Access validation pins ES256/P-256, `at+jwt`, issuer, singleton audience,
  TTL/time ordering and positive identity/security-version claims.
- Remote/embedded key headers, fractional versions and random `kid` resource
  amplification are bounded or rejected.
- Gateway publishes a trusted principal only after authoritative session status,
  identity, versions and expiries match.
- Missing/revoked/version-mismatched state denies; JWKS/session outage becomes
  generic 503.
- Filter ordering is checked and external bearer credentials do not reach
  protected downstream services.
- Downstream errors after authentication preserve their original boundary.
- No source private key, default secret or in-memory production session fallback
  was introduced.

## CI execution required

- Java 21 compilation and all JUnit/application-context suites.
- Nimbus codec/JWKS rotation and Reactor filter runtime tests.
- Maven Reactor, Testcontainers and published reports.
- Redis session projection integration, outage and revocation convergence tests.

## Remaining blockers

- Hardened HTTPS access JWKS transport.
- Redis session projection reader/writer and consistency/lag evidence.
- KMS/HSM access and delegation signer lifecycle.
- Login persistence and access/refresh issuance transaction.
- Real route policy and enabled deployments.
- Authorization repository/endpoint and grant/revoke golden path.

## Release statement

Version 1.7 closes the largest component-level external-authentication
fail-open gap. It remains a high-quality implementation seed rather than a
runnable or production-ready IAM system.
