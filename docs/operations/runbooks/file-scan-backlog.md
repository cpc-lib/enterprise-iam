# Runbook — File Scan Backlog

## Immediate actions
1. Check scan queue lag and scanner availability
2. Keep scan-required files unavailable
3. Scale scan workers or recover scanner
4. Verify backlog decreases and CLEAN files become AVAILABLE

## Verification
- Confirm file authorization invariants.
- Confirm object/file state consistency.
- Confirm audit/security events are recorded.
