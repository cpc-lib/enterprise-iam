# Runbook — RabbitMQ Outage

## Immediate actions
1. Confirm business local TX still works
2. Inspect outbox growth
3. Recover broker
4. Verify publisher confirms
5. Watch projection catch-up

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Record incident timeline.
