# CODE PHASE 02–05 Acceptance Catalog

Every security case uses two tenants and deliberately colliding IDs/codes.
Failure-path cases use real service containers unless the dependency is the
fault target.

| ID | Scenario | Required result |
|---|---|---|
| SEC-TREE-001 | Move a Team beneath itself or one of its descendants | `422`; path and descendants remain unchanged. |
| SEC-TREE-002 | Use a parent Organization/Team from another tenant | `404` or deny without existence disclosure. |
| IT-TEAM-001 | Add the same active member repeatedly | One active membership and one logical permission-version effect. |
| SEC-TEAMROLE-001 | Bind a TeamRole owned by another Team/Tenant | Deny; no binding/event/version change. |
| IT-TEAMROLE-002 | Remove a member with active TeamRoles | Membership and dependent bindings revoke atomically; permission version advances. |
| SEC-SQL-001 | Submit raw SQL/function/identifier through custom policy input | Schema/DSL validation rejects it before persistence or compilation. |
| SEC-SQL-002 | Compile scopes for SELECT count exists aggregate and export | Every query includes the same tenant and effective-scope predicate. |
| SEC-SQL-003 | Use unsupported join/subquery/query shape | Fail closed with no unscoped query execution. |
| PT-SCOPE-001 | Generate random union/intersection scope sets | Effective rows never exceed explicit union and never escape explicit intersection. |
| SEC-SCOPE-002 | SHARED checkpoint is below required projection epoch | SHARED branch under-grants/denies until contiguous catch-up. |
| SEC-FIELD-001 | Write hidden unknown nested or explicit-null protected property | Request denies before entity mapping; stored data is unchanged. |
| SEC-FIELD-002 | Read hidden and masked fields through list/detail/export | Hidden fields omitted and masks applied consistently in every channel. |
| SEC-FIELD-003 | Sort/filter/group by hidden or masked source field | Deny unless an explicit operation policy permits it. |
| PT-FIELD-001 | Merge conflicting field policies at equal priority | Hidden and deny dominate; result never becomes less restrictive. |
| SEC-SHARE-001 | Grant operation or field beyond creator rights | `403` escalation error; no share/history/outbox entry. |
| SEC-SHARE-002 | Reshare beyond parent duration/depth/rights | `403` or `422`; child share is absent. |
| SEC-SHARE-003 | Revoke parent with active descendants and warm ACL cache | After success no parent/descendant path returns ALLOW. |
| IT-SHARE-004 | Duplicate share create or revoke command/event | One active share/effect; history and projection remain logically idempotent. |
| IT-SHARE-005 | Expiry job races with manual revoke | One terminal outcome; epoch advances monotonically; audit is explainable. |
| SEC-FILE-001 | Tenant A probes Tenant B file/upload/reference IDs | `404` or deny; no object/hash/filename metadata leaks. |
| SEC-FILE-002 | Hash matches an existing cross-tenant physical object | Instant path returns only a new tenant-local file/reference; no ownership leak. |
| SEC-FILE-003 | Client reports wrong part size/ETag/hash or missing part | Completion rejects; file never becomes AVAILABLE. |
| IT-FILE-004 | Two completion requests race with same/different idempotency keys | Exactly one logical file result and one object reference increment. |
| SEC-FILE-005 | File scan is pending failed infected or unavailable | Preview/download denies and bytes are not issued. |
| SEC-FILE-006 | Malware scanner returns INFECTED | File/object quarantined and security event/audit emitted without exposing bytes. |
| SEC-FILE-007 | Revoke instance access before proxy download | Subsequent byte/range request denies immediately. |
| SEC-FILE-008 | Reuse or alter a presigned part/download scope | Storage rejects wrong part/object/method/expiry; application never broadens it. |
| SEC-FILE-009 | Filename contains path traversal bidi control CRLF or unsafe Unicode | Storage key unaffected; display/download name safely normalized/encoded. |
| SEC-FILE-010 | Delete/purge file with live reference legal hold or retention | Logical delete/purge transition is rejected as applicable. |
| IT-FILE-011 | Abort/expire upload after quota reservation | Multipart remnants cleaned asynchronously and reservation released exactly once. |
| PT-FILE-001 | Random part order duplicate reports and retry counts | Completion is deterministic and never trusts client-only state. |
| CT-PHASE2-001 | Proposed phase contract removes or narrows a released field/status | Compatibility gate fails without approved migration plan. |

## Exit evidence

CI evidence includes requirement/test ID contract version migration checksum
image digest seed for property tests and immutable build ID. P0/P1 skips fail
the release. Each phase publishes evidence before the next phase is enabled.
