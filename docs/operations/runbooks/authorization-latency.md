# Runbook — Authorization Latency High

## Immediate actions
1. Check P99 by cache level
2. Check cache miss ratio
3. Check Redis latency
4. Check DB fallback/slow SQL
5. Scale Authorization if needed

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Record incident timeline.
