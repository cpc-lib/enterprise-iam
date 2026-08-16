# Runbook — Login Failure Spike

## Immediate actions
1. Check attack vs dependency issue
2. Check Identity DB/Redis
3. Check password hash CPU
4. Apply rate-limit/risk controls
5. Inspect security events

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Record incident timeline.
