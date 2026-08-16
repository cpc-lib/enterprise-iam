# Open Decision Register

An item in this register is not permission to choose ad hoc during coding.
When its due gate is reached, the owner records the decision in an ADR and
updates every affected machine-readable contract.

| ID | Decision | Safe default until decided | Owner | Due gate | Blocking |
|---|---|---|---|---|---|
| DEC-001 | Production access/refresh TTL | 5 minutes / 14 days, 30-day absolute session | Security owner | Production readiness | Yes |
| DEC-002 | JWT signing key custody | External KMS/HSM; local PEM forbidden in production | Platform owner | Staging | Yes |
| DEC-003 | Password hashing cost | Argon2id baseline in security parameters; benchmark upward | Security owner | Beta | Yes |
| DEC-004 | Tenant and account lockout thresholds | Frozen baseline; tune only with monitored evidence | Product + Security | Beta | No |
| DEC-005 | Authorization SLO and peak QPS | Fail closed; use SPEC 32 baseline only for development | SRE owner | Performance gate | Yes |
| DEC-006 | Audit/authorization-log retention | Retain minimum required by law and contract | Compliance owner | Production readiness | Yes |
| DEC-007 | Recovery RPO/RTO | SPEC 35 baseline is provisional | Business owner | DR gate | Yes |
| DEC-008 | Browser refresh-token deployment profile | Same-site cookie by default; BFF if cross-site is required | Architecture owner | Frontend integration | Yes |
| DEC-009 | Seata in CODE PHASE 01 | Not on create/grant hot paths; selective revoke only when sharing starts | Architecture owner | Sharing epic | No |
| DEC-010 | FAPI 2.0 conformance | Optional unless external OAuth/OIDC authorization-server scope is approved | Product owner | Scope review | No |
| DEC-011 | Production maximum file and tenant quota | 50 GiB per file; tenant quota must be explicitly provisioned | Product + SRE | File beta | Yes |
| DEC-012 | Malware scanning engine and SLA | Pluggable scanner; failure quarantines and denies | Security + SRE | File staging | Yes |
| DEC-013 | Presigned versus proxy download classification | High-security and immediate-revoke resources use proxy | Security owner | File staging | Yes |
| DEC-014 | Custom condition DSL production enablement | Disabled until parser fuzzing and complexity bounds pass | Security owner | Policy beta | Yes |

Closed decisions must move to an ADR; do not delete their history.
