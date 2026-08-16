# Runbook — Share Revoke Inconsistency

## Immediate actions
1. Compare share version/epoch
2. Compare expected epoch vs checkpoint
3. Verify stale ALLOW is blocked
4. Trigger reconcile
5. Escalate SEV-0 if unauthorized ALLOW confirmed

## Verification
- Confirm security invariants.
- Confirm SLO/metrics returning to baseline.
- Record incident timeline.
