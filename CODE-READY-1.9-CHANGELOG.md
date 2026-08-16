# CODE-READY 1.9 Changelog

## Added

- Reusable `iam-outbox-spring-boot-starter` implementation with transaction-bound
  append, short leased claims and guarded terminal transitions.
- MySQL 8 `FOR UPDATE SKIP LOCKED` relay with expired-lease recovery.
- Bounded attempts, deterministic jitter, safe error codes, immediate poison or
  unsupported-schema dead-letter behavior and low-cardinality Micrometer metrics.
- Auth-service session-projection event schema, strict JSON codec, metadata fence,
  appender port and idempotent Redis handler.
- Canonical/runtime `V2__session_projection_outbox.sql` and disabled-by-default
  relay configuration.
- Focused outbox tests, `validate_session_projection_outbox.py` and SPEC 45.

## Hardened

- Outbox append now rejects missing or read-only business transactions.
- A stale worker cannot publish, retry or dead-letter after losing claim ownership.
- Unknown fields, duplicates, scalar coercion, fractional integers, trailing JSON,
  invalid schema and metadata mismatch cannot reach Redis.
- Exception messages and event payloads are excluded from durable failure codes and
  metric tags.
- Auto-configuration is ordered after JDBC and transaction-manager creation.

## Changed

- Maven Reactor version advanced to `1.9.0-SNAPSHOT` across all 31 POMs.
- Auth-service runtime migration inventory advanced from one to two migrations.
- SPEC inventory advanced to 45 and static validator count to eight.

## Not claimed

- The current `LoginSessionIssuer` is still only a port; login/revoke/disable/expiry
  persistence transactions do not yet call the new appender.
- The relay provides at-least-once delivery, not exactly-once delivery.
- DEAD inspection/replay tooling, lag alerts and multi-worker crash evidence remain.
- Maven/JDK 21/JUnit/MySQL/Redis/Testcontainers execution did not run on the
  packaging host.
- KMS/HSM, authorization repositories/endpoints and the full grant/revoke golden
  path remain open.
