# Runbook — Presigned URL Leak

## Immediate actions
1. Assess resource sensitivity and remaining TTL
2. Block issuance of new URLs
3. Switch affected policy to proxy if needed
4. Investigate access logs
5. Escalate security incident for sensitive resources

## Verification
- Confirm file authorization invariants.
- Confirm object/file state consistency.
- Confirm audit/security events are recorded.
