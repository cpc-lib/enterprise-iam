# Runbook — File Object Missing

## Immediate actions
1. Block access to affected logical files
2. Check MinIO replica/backup
3. Restore object if available
4. Verify SHA-256 against metadata
5. Reconcile file state and re-enable only after verification

## Verification
- Confirm file authorization invariants.
- Confirm object/file state consistency.
- Confirm audit/security events are recorded.
