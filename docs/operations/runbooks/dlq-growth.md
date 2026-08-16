# Runbook — DLQ Growth

## Immediate actions
1. Identify exact `event_id`, event type/schema and safe `last_error_code`; do
   not export raw session payloads to tickets or chat.
2. Classify unsupported schema, invalid payload, metadata mismatch or exhausted
   dependency retry. Preserve the original row and incident evidence.
3. Fix and deploy the deterministic cause before replay.
4. Require an authorized, auditable replay decision per reviewed event range.
   Never bulk-change every `DEAD` row to `PENDING`.
5. Replay in a bounded canary batch and verify the monotonic Redis consumer
   treats duplicates/stale versions safely.
6. Confirm retry/dead growth and projection convergence lag recover before the
   next batch.

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Record incident timeline.
