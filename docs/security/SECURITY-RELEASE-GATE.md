# Security Release Gate

## Release Blocking
- [ ] Tenant header spoofing rejected
- [ ] User header spoofing rejected
- [ ] JWT forgery rejected
- [ ] Refresh token reuse detected
- [ ] Cross-tenant IDOR denied
- [ ] Same-tenant IDOR denied
- [ ] Data Scope bypass tests green
- [ ] Hidden/forbidden field mass assignment denied
- [ ] Backend masking verified
- [ ] Share operation escalation denied
- [ ] Share field escalation denied
- [ ] Revoked share cannot survive stale ACL projection
- [ ] Unknown API defaults to DENY
- [ ] Internal API rejects external caller
- [ ] Authorization outage does not fail open
- [ ] Redis session outage does not fail open
- [ ] Session mutation and projection outbox append commit or roll back together
- [ ] Login session refresh hash and projection event commit before tokens return
- [ ] Raw refresh token appears only in a Secure HttpOnly SameSite cookie
- [ ] Login and refresh responses emit the exact frozen IAM_REFRESH cookie shape
- [ ] Ambiguous login delivery cannot leave an unreconciled orphan session
- [ ] Concurrent login issuance commits no more than the configured session limit
- [ ] Duplicate, expired-lease and poison projection events remain fail closed
- [ ] Out-of-order MQ event cannot restore stale ALLOW
- [ ] Audit records high-risk permission changes
- [ ] Secret scan green
- [ ] Production default-secret check green

## High Risk Review
- [ ] PUBLIC API changes audited
- [ ] Step-up path available for critical admin actions
- [ ] Projection rebuild is authorized and audited
- [ ] DLQ replay is authorized and audited
- [ ] Explain SECURITY level restricted
