# Runbook — Redis Session Failure

## Immediate actions
1. Do not disable session validation
2. Check Redis health/memory/failover
3. Expect AUTH_REQUIRED 503
4. Restore Redis
5. Run session integrity smoke

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Record incident timeline.
