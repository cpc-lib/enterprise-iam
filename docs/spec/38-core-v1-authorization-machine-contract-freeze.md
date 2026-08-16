# Enterprise IAM & Dynamic Authorization Platform
## 38 — Core V1 Authorization Machine Contract Freeze 1.2

Status: `NORMATIVE`  
Supersedes SPEC 37 only where this document or its referenced V1.2 contracts
are more specific. SPEC 37 remains normative for cross-cutting security,
authentication, RBAC and change-control rules.

## 1. V1.2 objective and boundary

V1.2 closes the machine-contract gap for the remaining V1 authorization
capabilities:

```text
organization/team role
→ data scope
→ field policy
→ resource share and revoke fence
→ file upload, scan, reference, preview and download
```

The phrase `Core V1 Authorization` is intentional. This freeze does not claim
machine-complete CRUD coverage for every administration screen, operational
endpoint, file DR command, reporting query or future direct/temporary grant.
Those remain governed by the prose Specs and must receive an OpenAPI/DDL/test
slice before their implementation Story becomes `IN_PROGRESS`.

## 2. Normative artifacts

- `docs/api/openapi-code-phase-02-policy.yaml`
- `docs/api/openapi-code-phase-03-sharing-file.yaml`
- `docs/events/asyncapi-code-phase-02-v1.yaml`
- `docs/database/code-phase-02/`
- `docs/testing/CODE-PHASE-02-05-ACCEPTANCE.md`
- `docs/architecture/REQUIREMENTS-TRACEABILITY.csv`

Conflict precedence is:

```text
SPEC 38 and V1.2 machine contracts
> SPEC 37 and CODE PHASE 01 machine contracts
> SPEC 36
> SPEC 24–35
> SPEC 01–23
```

## 3. Organization and team invariants

- Organization and Team form tenant-local acyclic trees.
- A parent change MUST reject self-parenting, descendant-parenting, cross-tenant
  parenting and a materialized path exceeding the frozen maximum depth.
- Membership and member-role bindings use active uniqueness. Repeated bind is
  idempotent; revoke preserves history.
- A TeamRole is valid only inside its owning Team. Binding a TeamRole from a
  different Team or Tenant MUST deny.
- Team hierarchy, membership or role changes MUST monotonically advance the
  affected subject permission version before success.

## 4. Data-scope invariants

- Data scope is compiled from typed metadata and a bounded AST; clients cannot
  submit raw SQL, identifiers, functions, joins or fragments.
- The compiler uses an allowlist for table/column identifiers registered in
  `iam_resource_data_schema`.
- Every generated predicate is parenthesized and combined with the mandatory
  trusted tenant predicate using `AND`.
- Unsupported query shapes, missing schema metadata, unknown scope types and
  stale ACL checkpoints fail closed.
- Count, existence, aggregate, export and pagination queries apply the same
  effective predicate as row-list queries.
- `SPECIFIED_TEAM` bindings validate tenant and team existence at command time.
- Scope union/intersection is explicit in `merge_mode`; no implicit widening is
  permitted.

## 5. Field-policy invariants

- Unknown protected fields deny writes and are omitted from reads.
- Write authorization occurs on the submitted property paths before mapping to
  an entity, including explicit `null`, nested objects and collection members.
- Read shaping occurs on the server after data-scope filtering and before
  serialization.
- `hidden=true` takes precedence over readable/masking; deny takes precedence
  over allow; the most restrictive result wins at equal priority.
- A mask strategy is a predefined typed implementation with validated config;
  it cannot contain code, expressions or templates that execute dynamically.
- Sorting, filtering, grouping and export by a hidden or masked source field
  require an explicit operation-level policy; UI visibility is not authority.

## 6. Sharing invariants

- V1 sharing is tenant-local and targets only frozen subject types.
- A share can contain only operations/fields the grantor currently holds with
  delegable authority. Reshare is the intersection of the parent share, the
  resharer's current rights and the resource sharing policy.
- Create/expand may temporarily under-grant through outbox projection.
- Revoke/reduce MUST update the Sharing projection epoch and Authorization
  security epoch in the short security transaction defined by SPEC 36.
- A business ACL projection with checkpoint lower than the required epoch MUST
  not produce `ALLOW` through the SHARED branch.
- Parent revoke, expiry, resource delete and owner transfer invalidate all
  affected descendants according to the frozen policy before success.
- Share history is append-only and the original grant basis remains explainable.

## 7. File invariants

- Physical object, logical file and business reference are separate entities.
- Instant upload never reveals whether another tenant owns identical content;
  it creates a tenant-local logical file/reference only after authorization and
  quota reservation.
- Client hashes, MIME types, part sizes, ETags and upload-complete claims are
  untrusted until verified against storage and server policy.
- File bytes are unavailable while verification or malware scan is pending,
  failed, infected or quarantined.
- Preview and download are distinct authorization operations. File reference
  inheritance, instance authorization and field policy are all evaluated.
- High-security download uses the proxy path. A presigned URL is short-lived,
  audience/object scoped and cannot satisfy immediate revocation guarantees.
- Object keys are generated, immutable and tenant-independent; user filenames
  never become storage paths.
- Logical deletion, legal hold, retention and physical purge are distinct state
  transitions. Purge requires zero live references and zero legal hold.
- Upload completion is idempotent and concurrency-safe; exactly one logical
  completion result is observable.

## 8. Policy decision obligations

The authorization response may include typed obligations:

```text
dataPredicateRef
fieldReadPlanRef
fieldWritePlanRef
maskPlanRef
shareEpoch
downloadMode
auditLevel
```

References are short-lived, tenant/subject/resource/operation/version-bound
server artifacts. Clients cannot alter or replay them across decision context.

## 9. Release sequence

Capabilities enter implementation only in this order:

1. Organization and TeamRole closed loop.
2. Data-scope compiler plus SELECT/count/export parity tests.
3. Field read/write shaping plus mass-assignment tests.
4. Sharing create/revoke/expiry plus projection-gap tests.
5. File upload/scan/reference/download closed loop.

Each phase must satisfy its traceability rows and acceptance cases before the
next phase starts. A P0/P1 security test cannot be deferred behind a feature
flag that permits the affected capability in production.

## 10. Core V1 authorization CODE-READY exit

The core V1 authorization scope may be called CODE-READY only when:

- every public/internal command in the five phases has an OpenAPI operation;
- every cross-service security fact has a versioned event contract;
- every owned persistent fact has reviewed Flyway DDL and repository contract;
- each requirement maps to positive, negative and failure-path verification;
- contract validator and compatibility checks pass;
- unresolved production decisions remain explicit and block the corresponding
  production gate, not implementation of unrelated phases.
