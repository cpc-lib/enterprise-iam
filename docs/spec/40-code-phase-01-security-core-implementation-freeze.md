# 40 — CODE PHASE 01 Security Core Implementation Freeze — SPEC 1.4

## 1. Authority and scope

SPEC 40 is authoritative for the implemented CODE PHASE 01 security-core
slice. SPEC 38 remains the business/machine-contract authority and SPEC 39
remains the Reactor/toolchain authority. A conflict is resolved toward the more
restrictive security behavior.

This slice implements deterministic authorization precedence, trusted-context
claim policy, gateway spoof-header removal and executable Flyway integration
tests. It does not yet implement login/JWT issuance, delegation signing/JWKS
decoding, authorization repositories, grants/revokes, the HTTP decision
endpoint or an end-to-end protected business API.

## 2. Authorization input and boundary

`AuthorizationRequest` implements the Phase-01 request dimensions:

```text
tenantId, subjectId, sessionId, resourceId, operationId,
resourceInstanceId?, permissionVersion, requestId, context
```

It rejects non-positive identifiers, negative permission versions, oversized
request/instance identifiers, more than 32 context properties and non-scalar
context values. Context is defensively copied and immutable.

Repositories will provide `AuthorizationFacts`. The domain engine never reads
MyBatis, Spring, Redis or HTTP state and rechecks tenant, subject, resource,
operation, time and status dimensions even when facts were pre-resolved.

## 3. Frozen decision precedence

Evaluation is ordered and fail-closed:

| Order | Condition | Decision/reason |
|---:|---|---|
| 1 | Authoritative facts unavailable | `DENY / IAM_AUTHZ_DEPENDENCY_UNAVAILABLE` |
| 2 | Fact tenant differs from request | `DENY / IAM_AUTHZ_TENANT_MISMATCH` |
| 3 | Resource/operation fact differs | `DENY / IAM_AUTHZ_RESOURCE_CONTEXT_MISMATCH` |
| 4 | Presented version below authoritative | `DENY / IAM_AUTHZ_STALE_PERMISSION_VERSION` |
| 5 | Presented version above authoritative | `DENY / IAM_AUTHZ_PERMISSION_VERSION_MISMATCH` |
| 6 | Resource-operation disabled | `DENY / IAM_AUTHZ_RESOURCE_OPERATION_DISABLED` |
| 7 | Any exact active DENY grant | `DENY / IAM_AUTHZ_EXPLICIT_DENY` |
| 8 | One or more exact active ALLOW grants | `ALLOW / IAM_AUTHZ_GRANT_ALLOW` |
| 9 | No exact active grant | `DENY / IAM_AUTHZ_NO_MATCHING_GRANT` |

An exact grant matches tenant, subject, resource and operation and must be
active within its time window. DENY evidence is returned without mixing ALLOW
grant IDs. Evidence IDs are unique, sorted and capped at the OpenAPI limit of
100.

## 4. Trusted delegation policy

`SECURITY-PARAMETERS.yaml` now freezes downstream delegation tokens as:

- ES256 only; `none` and algorithm substitution deny;
- explicit `typ=iam-delegation+jwt`;
- issuer and per-service audience validation;
- required `kid` and cryptographically verified signature input;
- maximum TTL 30 seconds and clock skew 5 seconds;
- positive tenant, subject and session plus `jti` and request ID;
- external user access tokens are never accepted as downstream delegation;
- missing or invalid claims always deny.

The implemented `DelegationTokenPolicy` validates claims only after a decoder
has asserted cryptographic signature verification. Compact-token parsing,
JWKS/key rotation and gateway signing remain open and must not be replaced with
a fixed shared secret.

## 5. Gateway spoofing fence

`ExternalIdentityHeaderSanitizingFilter` runs at the highest Gateway order and
removes client-controlled tenant, user, subject, session, resource, forwarded
identity, trusted-context, delegation and service-token headers. It preserves
the external `Authorization` header for the future authentication filter and
preserves correlation headers.

This closes only the header-removal half of `SEC-TEN-001`. The same acceptance
case remains partial until gateway authentication signs a delegation JWT and
downstream services verify it.

## 6. Database runtime baseline

MySQL 8.0 reached end of life in April 2026. New Phase-01 runtime validation is
therefore upgraded to MySQL 8.4 LTS. The pinned integration-test image is
`mysql:8.4.9`.

Identity, Auth and Authorization services now include JDBC, Flyway MySQL and
MySQL Connector/J dependencies. Runtime configuration requires `DB_URL`,
`DB_USERNAME` and `DB_PASSWORD` without source defaults and enables:

```text
clean-disabled=true
validate-on-migrate=true
out-of-order=false
baseline-on-migrate=false
```

Each service packages a byte-identical copy of its reviewed canonical V1 DDL.
The validator rejects drift. Each `FlywayMigrationIT` starts its own MySQL
8.4.9 container, verifies the first migration executes, the second migration
executes zero scripts, and Flyway validation succeeds.

Reference:

- https://dev.mysql.com/doc/relnotes/mysql/8.0/en/
- https://hub.docker.com/_/mysql/tags

## 7. Executable evidence

Implemented unit evidence:

- `SEC-TEN-001`: external identity/internal headers stripped — partial case;
- `SEC-FAILCLOSED-001`: unavailable authoritative facts deny — domain portion;
- `PROP-AUTHZ-001`: same input/version yields identical decision semantics;
- `PROP-AUTHZ-003`: cross-tenant facts never allow;
- explicit DENY precedence, stale/ahead version, disabled resource-operation,
  expired grant, context bounds and delegation claim validation.

CI-wired but not yet executed in the packaging environment:

- Java 21 compilation and all JUnit/ArchUnit tests;
- three MySQL 8.4.9 Flyway integration tests;
- complete Maven Reactor `verify` and Surefire/Failsafe reports.

## 8. Gate state and next slice

Gate B remains open. Static Phase-01 validation and migration byte-equivalence
pass locally; the packaging environment still lacks Maven, JDK 21 and Docker.

The next implementation slice is:

1. Identity/Auth repository ports and tenant-scoped MyBatis adapters;
2. login with Argon2id PHC verification and enumeration-resistant response;
3. ES256 access-token verification and gateway delegation signing;
4. downstream JWKS decoder/filter and per-service audience;
5. Authorization fact repository and `/internal/v1/authorization/check`;
6. transactional grant/revoke + permission version + outbox;
7. real MySQL/Redis golden grant → ALLOW → revoke → immediate DENY flow.

No endpoint may return ALLOW from a placeholder, demo profile or unavailable
dependency.
