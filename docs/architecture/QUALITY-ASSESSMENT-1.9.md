# CODE-READY Quality Assessment — 1.9

## Result

| Dimension | 1.8 | 1.9 | Evidence status |
|---|---:|---:|---|
| Architecture/business design | 95% | 96% | SPEC 45 freezes transaction, relay, retry, poison and operations contracts |
| Core authorization contract readiness | 94–95% | 94–95% | Authorization decision core is unchanged |
| Reactor structural completeness | 98% | 98% | Outbox starter is concrete and wired structurally |
| Executed Java/Maven evidence | 0% local | 0% local | Syntax/static evidence only; CI remains required |
| Phase-01 security implementation | 78–82% | 82–85% | Durable projection delivery foundation and consumer added |
| Production key/session integration | ~60% | ~68% | Durable path exists; business session transaction call sites remain |
| Production readiness | ~57% | ~61% | Recovery/metrics contracts improved; deployed E2E proof absent |

Percentages are bounded engineering estimates, not coverage or release metrics.

## Verified locally

- The writer refuses append without an active, non-read-only business transaction.
- Claims use a short `REQUIRES_NEW`, `READ_COMMITTED`, `FOR UPDATE SKIP LOCKED`
  transaction; handlers run after locks are released.
- All completion updates require the exact claim owner and state; lost leases do
  not trigger blind writes.
- Retry count, batch, lease and backoff are bounded; jitter is deterministic.
- Unsupported schemas and invalid event payload/metadata are dead-lettered without
  retry storms; temporary Redis failures remain retryable.
- The strict event codec rejects unknown/duplicate fields, coercion, fractional
  integers and trailing tokens. Metadata is matched to payload before Redis.
- Redis delivery is idempotent through the existing monotonic terminal-safe Lua
  publisher; the relay correctly claims only at-least-once semantics.
- Auth canonical/runtime V2 migrations match byte-for-byte and include relay,
  expired-claim and aggregate indexes plus state checks.
- Metrics use only a bounded outcome tag; payload/tenant/event/error text is not a
  tag or durable failure value.

## CI and runtime execution required

- Java 21 Maven/JUnit/application-context execution.
- MySQL 8.4 migration and multi-worker claim/lease/crash integration tests.
- Redis Testcontainers duplicate, stale, terminal-state and outage tests.
- Real login/revoke/disable/expiry transaction-to-outbox tests.
- Oldest-pending/dead/retry/lease-loss/convergence dashboards and alerts.
- Audited DEAD inspection/replay workflow and revoke-to-DENY load evidence.

## Remaining blockers

- Implement real session persistence/token-pair issuance and invoke the appender in
  each owning transaction.
- Implement revoke, user-disable and expiry application services with the same
  atomic append boundary.
- KMS/HSM signer lifecycle and downstream delegation JWKS transport.
- Authorization repositories/endpoints and grant/revoke golden path.
- Infrastructure DNS/egress, Redis TLS/ACL and immutable CI evidence.

## Release statement

Version 1.9 closes the durable delivery foundation between an auth transaction and
the already implemented Redis projection consumer. It does not close the business
producer transaction because that persistence implementation does not exist yet.
The artifact is a stronger implementation seed, not a runnable or production-ready
IAM backend.
