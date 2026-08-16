# Enterprise IAM & Dynamic Authorization Platform
## 36 — Final Consistency Review & CODE PHASE Entry Freeze 1.0

> Scope: review SPEC 01~35 and the physical project skeleton before CODE PHASE.
>
> This document is the final conflict-resolution authority for V1.0/V1.x implementation. When an older SPEC contains wording that conflicts with this freeze, SPEC 36 wins.

---

# 1. Review Result

The architecture is ready to enter CODE PHASE after the corrections applied by this review.

Review dimensions:

```text
Repository layout
Service boundaries
Java packages
Framework modules
Authorization semantics
Share revoke consistency
Database ownership
File service integration
API/security boundaries
Flyway ownership
Backlog
Testing/release gates
```

---

# 2. Final Monorepo

```text
enterprise-iam/
├── backend/
├── frontend/
├── deploy/
├── docs/
├── scripts/
├── tools/
├── tests/
└── .github/
```

`backend/pom.xml` is the Maven backend reactor root.

---

# 3. Final Backend Service Set

```text
iam-gateway
iam-auth-service
iam-identity-service
iam-organization-service
iam-authorization-service
iam-sharing-service
iam-file-service
iam-audit-service
iam-job-service
```

`iam-test-support` is a test-support module, not a runtime microservice.

---

# 4. Final Java Package Set

```text
com.enterprise.iam.gateway
com.enterprise.iam.auth
com.enterprise.iam.identity
com.enterprise.iam.organization
com.enterprise.iam.authorization
com.enterprise.iam.sharing
com.enterprise.iam.file
com.enterprise.iam.audit
com.enterprise.iam.job
```

Obsolete package placeholders such as:

```text
com.enterprise.iam.iam_auth_service
```

are removed.

---

# 5. Final Framework Set

Core:

```text
iam-common-core
iam-common-web
iam-common-tenant
iam-common-security
iam-common-mybatis
iam-common-redis
iam-common-mq
iam-common-lock
iam-common-transaction
iam-common-observability
```

Starters:

```text
iam-authorization-client-spring-boot-starter
iam-security-spring-boot-starter
iam-api-discovery-spring-boot-starter
iam-data-permission-spring-boot-starter
iam-field-permission-spring-boot-starter
iam-idempotent-spring-boot-starter
iam-outbox-spring-boot-starter
iam-audit-spring-boot-starter
```

The ambiguous old name:

```text
iam-client-spring-boot-starter
```

is retired.

---

# 6. Permission Model Freeze

Authoritative rule:

```text
Permission As Data
```

Business Java code must not contain permission identifiers such as:

```text
customer:update
system:user:update
ROLE_ADMIN
```

Runtime authorization uses IDs and trusted API mapping context.

---

# 7. API Mapping Freeze

A newly discovered business API that requires protection but has no mapping:

```text
DENY
```

`PUBLIC` must be an explicit dynamic security policy and is audited as a high-risk change.

---

# 8. Gateway / Downstream PEP Freeze

Gateway performs:

```text
authentication
trusted context
API mapping
coarse authorization
```

Business services still perform:

```text
fine-grained operation
instance/data
field
```

authorization.

Gateway authorization is never the only protection.

---

# 9. Tenant Freeze

No cross-tenant share in V1.

No tenant header supplied by a client is trusted.

Tenant predicates remain mandatory at SQL boundaries.

---

# 10. Share Create vs Revoke Consistency Freeze

## Create / Expand

```text
Sharing local TX
+ Outbox
+ projection
```

Propagation delay may temporarily under-grant.

This is safe.

## Revoke / Reduce

Security-critical:

```text
short selective Seata transaction
```

atomically coordinates:

```text
Sharing DB:
  share mutation
  iam_share_projection_epoch++

Authorization DB:
  iam_share_security_epoch++
  relevant permission/security version bump
```

If this transaction cannot commit, the command does not report success.

---

# 11. Share Runtime Fence

Authorization decision/data plan carries:

```text
expectedShareEpoch
```

Business local ACL has:

```text
iam_acl_projection_checkpoint.last_contiguous_epoch
```

Rules:

```text
checkpoint < expected
=> SHARED branch fail closed

checkpoint == expected
=> projection may be used

checkpoint > expected
=> authorization plan is stale; refresh
=> refresh unavailable => deny
```

This closes the stale-ACL revoke window.

---

# 12. Share Expiration Freeze

Expiration safety does not depend on PowerJob.

Every request enforces:

```text
start <= now < expire
```

PowerJob only converges persisted status.

---

# 13. Database Ownership Freeze

```text
iam_auth          → iam-auth-service
iam_identity      → iam-identity-service
iam_organization  → iam-organization-service
iam_authorization → iam-authorization-service
iam_sharing       → iam-sharing-service
iam_file          → iam-file-service
iam_audit         → iam-audit-service
iam_job           → iam-job-service
```

No cross-service JOIN and no cross-service writes.

---

# 14. Authorization Database Addition

Final authorization DB includes:

```text
iam_share_security_epoch
```

Purpose:

```text
security fence for share revoke/reduction
```

Planned Flyway:

```text
V11__authorization_share_security_epoch.sql
```

---

# 15. Sharing Database Epoch

Sharing owns:

```text
iam_share_projection_epoch
```

It tracks share projection state changes and is emitted with sharing events.

---

# 16. File Service Freeze

`iam-file-service` is now a first-class platform service.

It owns:

```text
logical file metadata
physical object metadata
multipart upload session
part state
instant upload
business reference
download/preview policy
scan state
retention/reconcile
```

MinIO is storage, never the authorization source.

---

# 17. File Security Freeze

```text
Physical Object Exists != Authorized
Upload Complete != Available
Available != Previewable
Previewable != Downloadable
Deleted != Purged
```

File access always passes tenant + IAM + state + scan + storage policy.

---

# 18. File Download Freeze

Two modes:

```text
PRESIGNED
PROXY
```

High-security resources use PROXY or very short-lived constrained URLs.

A presigned URL is not treated as permanent permission.

---

# 19. File V1 Scope

V1 core:

```text
multipart
resume
instant upload
file reference
download authorization
preview policy baseline
scan state machine/quarantine
retention/delete/purge
storage reconcile
```

V1.1 candidates:

```text
Office preview conversion
advanced PDF sanitization
video transcoding
CDN
advanced watermark
full DLP
```

---

# 20. Persistence Freeze

```text
Domain Repository for writes/invariants
Query Port for read models
MyBatis adapters in infrastructure only
```

Forbidden:

```text
Controller -> Mapper
Application -> Mapper
Domain -> MyBatis
Java post-filter as authorization
```

---

# 21. Transaction Freeze

Default:

```text
local transaction + outbox
```

Then:

```text
Saga
```

Selective Seata is allowed only for short security-critical cross-DB atomicity, especially share revoke/reduction.

Do not wrap slow remote RPC inside long database transactions.

---

# 22. Idempotency Freeze

Critical writes use:

```text
Idempotency-Key
request hash
DB unique boundary
lease/recovery
```

Same key + different request:

```text
409
```

---

# 23. MQ Freeze

```text
at-least-once
+ consumer dedup
+ aggregate version
+ state-carrying projection events
```

No consumer assumes exactly-once broker delivery.

---

# 24. Authentication Freeze

Access JWT:

```text
short-lived
identity/session only
```

No role/team/permission expansion in JWT.

Refresh Token:

```text
random secret
hash at rest
rotation
reuse detection
family revoke
```

Redis session failure does not fall back to signature-only ALLOW.

---

# 25. React Freeze

React remains:

```text
UX enforcement
```

not security authority.

No role hardcoding.

Dynamic navigation and PageSchema/operation IDs come from server metadata.

File uploader uses Web Worker hashing and direct multipart object upload.

---

# 26. Testing Freeze

Release-blocking:

```text
cross-tenant
immediate revoke
data scope
field write deny
share stale projection revoke
refresh reuse
internal auth
idempotency
MQ duplicate/out-of-order
file cross-tenant
file quarantine/download
```

---

# 27. Observability Freeze

Security correctness has zero error budget for unauthorized ALLOW.

Metrics must distinguish:

```text
normal DENY
vs
fail-closed because safe decision unavailable
```

---

# 28. Backlog Freeze

Backlog now includes:

```text
E01~E15
```

E15 is Enterprise File Management.

New V1 work must map to a Story before code generation.

---

# 29. CODE PHASE Entry Gate

Required before starting code:

```text
[✓] Physical repository structure frozen
[✓] Backend service boundaries frozen
[✓] Java packages normalized
[✓] Framework modules normalized
[✓] Database ownership frozen
[✓] API behavior frozen
[✓] Authorization merge/data/field semantics frozen
[✓] Share revoke security fence frozen
[✓] File service integrated
[✓] Threat model exists
[✓] SLO/runbooks exist
[✓] Story backlog exists
```

Therefore:

```text
CODE PHASE may start.
```

---

# 30. First CODE PHASE Boundary

CODE PHASE 01 must not try to implement all SPECs.

It starts with:

```text
Build reactor
BOM
core framework
Docker infra
Identity schema
Auth schema
Tenant/User/Role
Resource/Operation
RolePermission
Login
Gateway trusted context
Authorization engine
Demo protected API
Grant -> ALLOW
Revoke -> immediate DENY
```

Only after this closed loop is green should Team/Data/Field/Share/File advanced phases proceed.

---

# 31. Change Control After Freeze

After SPEC 36:

P0/P1 architecture/security corrections are allowed.

Feature expansion requires:

```text
new Story
scope impact
schedule impact
security impact
```

Do not modify foundational semantics ad hoc during code generation.

---

# 32. Final Conclusion

SPEC 01~35 are now implementation input.

SPEC 36 is the conflict-resolution freeze.

The project is ready to transition from:

```text
SPEC PHASE
```

to:

```text
CODE PHASE 01
```

with closed-loop implementation and test-first security gates.
