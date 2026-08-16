# Runbook — Outbox Backlog

## Immediate actions
1. Keep the session-security fence enabled; do not bypass Redis validation.
2. Query counts and oldest `created_at` by `event_status`; separate ready
   `PENDING`, future retries, active claims, expired claims and `DEAD`.
3. Compare `iam.outbox.claimed` and `iam.outbox.delivery` outcomes with MySQL,
   Redis and relay-instance health. Durable `last_error_code` is a classification,
   not an exception message.
4. Let expired leases be reclaimed by the normal `SKIP LOCKED` poll. Do not
   clear a live claim or bulk-edit statuses.
5. Repair the dependency or poison schema cause before scaling workers. Keep
   worker count within measured database and Redis capacity.
6. Verify oldest-ready age, retry rate and lease-loss rate return to baseline,
   then prove a new session projection reaches Redis and Gateway evaluation.

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Confirm no terminal session was reactivated and no reviewed `DEAD` row was
  replayed implicitly.
- Record incident timeline.
