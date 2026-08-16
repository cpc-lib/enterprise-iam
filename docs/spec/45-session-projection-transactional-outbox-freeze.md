# 45 — Session Projection Transactional Outbox Freeze — SPEC 1.9

## 1. Authority and scope

SPEC 45 is authoritative for the reusable JDBC outbox relay and the auth-service
session-security projection event introduced after SPEC 44. SPEC 44 remains
authoritative for the Redis projection and HTTPS JWKS adapters; SPEC 43 remains
authoritative for request-time access-token and session decisions.

This slice implements the durable table, transaction-enforcing append API,
leased relay, strict session event codec and idempotent Redis consumer. SPEC 46
subsequently provides a concrete `LoginSessionIssuer` that appends login-created
state in the same physical JDBC transaction. Revoke, disable and expiry business
transactions must still call `SessionProjectionOutboxAppender` transactionally.
The presence of these components alone does not prove end-to-end revocation
convergence.

## 2. Producer transaction boundary

`JdbcOutboxWriter` only inserts into `sys_outbox_event` when Spring reports an
already-active, non-read-only transaction. It never creates a transaction and
never accepts a post-commit callback as a substitute. The owning application
service must perform this sequence in one database transaction:

1. lock/read the authoritative session and user security state;
2. persist the session/security mutation and increment its monotonic version;
3. allocate a positive unique event ID;
4. append exactly one matching outbox row; and
5. commit both writes or roll back both writes.

For the session projection event, `id` and `event_id` use the same allocated ID,
`aggregate_type` is exactly `LOGIN_SESSION`, `aggregate_id` is the session ID,
and `aggregate_version` is the session version. Event type is exactly
`iam.auth.session-security-projection` with schema version `1`.

## 3. Durable schema and state invariants

Auth migration `V2__session_projection_outbox.sql` creates the service-owned
table. IDs are caller supplied; no auto-increment or cross-service foreign key
is introduced. `event_id` is unique. Relay and lease indexes are distinct:

| Index | Ordered fields | Purpose |
|---|---|---|
| `idx_outbox_relay` | status, next retry, ID | ready/retry scan |
| `idx_outbox_claim` | status, claim expiry, ID | abandoned lease recovery |
| `idx_outbox_aggregate` | tenant, type, aggregate, version | incident tracing/order evidence |

Allowed states are `PENDING`, `CLAIMED`, `PUBLISHED`, and `DEAD`. A CLAIMED row
must have both claim owner and claim expiry; every other state must have neither.
Only PUBLISHED may have `published_at`. Retry count is bounded by 20. Stored
errors are bounded safe codes, never exception messages or payload fragments.

## 4. Claim and delivery protocol

Each poll opens a short `REQUIRES_NEW`, `READ_COMMITTED` transaction, selects up
to 100 ready rows using `FOR UPDATE SKIP LOCKED`, assigns a bounded owner and
lease, reloads the owned rows, and commits. Handlers run outside that claim
transaction so Redis latency cannot hold database row locks.

Every terminal update includes `id`, state `CLAIMED`, and exact `claim_owner`.
An update count other than one is lease loss; the worker records it and must not
blindly overwrite the new owner. Expired CLAIMED rows are reclaimable.

Delivery is deliberately at-least-once. A crash after Redis success but before
`PUBLISHED` may deliver the same event again. Therefore handlers must be
idempotent; this handler relies on SPEC 44's atomic Redis script to ignore stale
or duplicate versions and prevent terminal-session reactivation.

## 5. Failure classification and backoff

| Failure | Transition | Retry behavior |
|---|---|---|
| Handler success, including stale duplicate | `PUBLISHED` | none |
| Temporary handler/dependency exception | `PENDING` | exponential delay with deterministic ±20% event jitter |
| Maximum attempt reached | `DEAD` | none |
| Unknown event type/schema | `DEAD` | immediate, `UNSUPPORTED_EVENT_SCHEMA` |
| Invalid JSON/schema/metadata | `DEAD` | immediate, safe validation code |
| Lease lost during transition | unchanged by old owner | reclaim/new owner decides |

Defaults are disabled relay, batch 50, maximum 10 attempts, one-second poll,
30-second lease, one-second initial backoff, and five-minute maximum backoff.
Production must choose a lease longer than its measured high-percentile handler
latency or implement lease renewal before supporting slower handlers.

## 6. Strict event and Redis boundary

The event payload includes schema version, tenant, subject, session, token and
session versions, status, idle expiry and absolute expiry. Decoding rejects:

- unknown or duplicate JSON fields;
- null primitive values, string-to-number coercion and fractional integers;
- trailing JSON tokens, unsupported schema/status and invalid time ordering;
- non-positive identifiers/versions/epochs; and
- metadata that does not exactly match payload tenant, session and version.

Malformed data is poison data and dead-lettered immediately. Payload text is
never copied to exception messages or the durable `last_error_code` field.

## 7. Observability and recovery

The starter exports `iam.outbox.claimed`, `iam.outbox.delivery`, and
`iam.outbox.delivery.duration`. The only delivery tag is the bounded outcome
enum; tenant IDs, event IDs, exception text, routing keys and payload values are
forbidden metric tags.

Operations must alert on oldest PENDING age, DEAD growth, lease-loss rate,
retry rate and session-projection convergence lag. DEAD replay is an explicit
operator workflow: repair the deterministic cause, create an auditable replay
decision, and move/copy only the exact reviewed event. Direct bulk status edits
and infinite retries are not permitted.

## 8. Enablement sequence

1. Run Java 21 Maven tests and MySQL 8.4 Flyway integration in CI.
2. Deploy V2 while the relay remains disabled.
3. Enable the SPEC 46 login producer only after supplying production keys; add
   revoke/disable/expiry producers with the same transaction rule.
4. Verify rows, metadata, metrics, Redis idempotence and bounded failure codes.
5. Enable one relay instance, then canary multiple instances and exercise lease
   recovery, duplicates, outages and poison events.
6. Prove login/revoke/disable/expiry convergence SLO before release.

## 9. Evidence and remaining blockers

Focused source tests freeze payload strictness, metadata matching, appender
mapping, duplicate route rejection, bounded retry, permanent/unsupported dead
letter, lease loss and error-message redaction. Static validation also compares
canonical/runtime V2 DDL byte-for-byte and freezes CI wiring.

Still required before this path is production complete:

1. implement revoke/disable/expiry application services, each calling the
   appender transactionally; SPEC 46 already supplies the JDBC login producer;
2. run Maven/JUnit and MySQL/Redis Testcontainers on Java 21;
3. add multi-worker crash/lease-expiry and Redis outage integration tests;
4. implement audited DEAD inspection/replay tooling and lag alerts;
5. prove revoke-to-Gateway-DENY convergence under load; and
6. finish KMS/HSM signing, authorization repositories/endpoints and the full
   grant/revoke golden path.

## 10. References

- Spring transaction propagation: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- Spring programmatic transactions: https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html
- Spring scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Boot metrics: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- MySQL locking reads: https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html
