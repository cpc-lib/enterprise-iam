# CODE-READY Quality Assessment — 1.8

## Result

| Dimension | 1.7 | 1.8 | Evidence status |
|---|---:|---:|---|
| Architecture/business design | 94% | 95% | SPEC 44 freezes concrete trust-adapter boundaries and residual risk |
| Core authorization contract readiness | 94–95% | 94–95% | Authorization decision core is unchanged |
| Reactor structural completeness | 97% | 98% | Redis dependencies and cross-module projection contract are closed structurally |
| Executed Java/Maven evidence | 0% local | 0% local | Syntax/static evidence only; CI remains required |
| Phase-01 security implementation | 70–75% | 78–82% | HTTPS JWKS transport and Redis reader/writer adapters added |
| Production key/session integration | ~45% | ~60% | Real adapters exist; KMS and transaction/outbox wiring remain |
| Production readiness | ~52% | ~57% | Network/data boundaries improved; deployed E2E and operations proof absent |

Percentages are bounded engineering estimates, not coverage or release metrics.

## Verified locally

- Auth-service and Gateway share one versioned Redis hash codec, generated key
  format, strict field order, status set and time invariants.
- The Lua publisher performs schema/identity validation, two-dimensional
  monotonic version checks, terminal-state anti-reactivation, full HSET and
  absolute PEXPIREAT atomically.
- The reactive reader distinguishes an absent hash from partial/corrupt state;
  Redis and decode failures propagate without an in-memory fallback.
- The default access-JWKS adapter accepts one exact allowlisted HTTPS hostname,
  rejects unsafe URI components and non-global/mixed DNS answers, disables
  ambient proxy and redirects, and bounds TLS versions, timeouts, response
  metadata, streaming bytes and UTF-8.
- Enabled default wiring fails when JWKS URI/allowlist is missing. Custom
  resolver and verifier ports remain replaceable for deployment.
- The DNS preflight versus connection re-resolution TOCTOU limitation is
  explicit; network egress enforcement is still mandatory.
- No private key, Redis credential, default production secret or in-memory
  production session fallback was introduced.

## CI and runtime execution required

- Java 21 compilation and all JUnit/application-context suites.
- Redis Testcontainers execution of Lua concurrency, duplicates, stale versions,
  terminal-state races, TTL and outage behavior.
- Live HTTPS certificate/hostname, redirect, timeout, rotation, DNS rebinding and
  infrastructure egress tests.
- Transactional outbox publication, projection-lag metrics and revoke convergence.
- Maven Reactor, Flyway, Testcontainers and published immutable reports.

## Remaining blockers

- Login/revoke/disable/expiry transaction and idempotent outbox integration with
  the Redis projection publisher.
- KMS/HSM access and delegation signer lifecycle.
- Profile-specific downstream delegation JWKS network adapter.
- Login persistence and atomic access/refresh issuance.
- Infrastructure DNS/egress, Redis TLS/ACL, metrics/alerts and real routes.
- Authorization repository/endpoint and grant/revoke golden path.

## Release statement

Version 1.8 replaces the two explicit 1.7 trust-adapter placeholders with
production-shaped code and freezes their security contracts. It is materially
closer to an implementable backend, but remains a high-quality implementation
seed rather than a runnable or production-ready IAM system.
