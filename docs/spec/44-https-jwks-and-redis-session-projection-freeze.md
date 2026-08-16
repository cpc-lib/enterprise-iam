# 44 — HTTPS JWKS and Redis Session Projection Freeze — SPEC 1.8

## 1. Authority and scope

SPEC 44 is authoritative for the production-shaped trust adapters introduced
after SPEC 43: the Gateway access-JWKS network boundary, the shared Redis
session-security projection, the reactive Gateway reader and the auth-service
monotonic publisher. SPEC 43 remains authoritative for access-token and request
authentication semantics; SPEC 42 remains authoritative for internal
delegation.

This slice supplies real Spring Data Redis and JDK HTTP adapter code. SPEC 45
subsequently supplies the transactional outbox table, relay and consumer, but
login/session mutation call sites are still absent. This document also does not
claim network egress pins DNS answers or that Redis, HTTPS and
revoke-convergence tests executed on this packaging host.

## 2. Frozen Redis projection contract

`SessionProjectionSchema` is shared by auth-service and Gateway. The Redis
Cluster key is generated only from validated positive numeric identifiers:

```text
iam:session-security:{tenantId:sessionId}
```

The hash schema version is exactly `1`; HMGET order is normative:

| Order | Field | Constraint |
|---:|---|---|
| 1 | `schemaVersion` | exactly `1` |
| 2 | `tenantId` | canonical positive base-10 `long` |
| 3 | `subjectId` | canonical positive base-10 `long` |
| 4 | `sessionId` | canonical positive base-10 `long` |
| 5 | `tokenVersion` | canonical positive base-10 `long` |
| 6 | `sessionVersion` | canonical positive base-10 `long` |
| 7 | `status` | `ACTIVE`, `REVOKED` or `EXPIRED` |
| 8 | `idleExpiresAtEpochMs` | positive epoch milliseconds |
| 9 | `absoluteExpiresAtEpochMs` | positive epoch milliseconds |

Instants are millisecond-aligned and idle expiry must not exceed absolute
expiry. Values are length-bounded before parsing. Unknown schema versions,
partial hashes, overflow, non-canonical numbers, unknown/case-folded status and
invalid time ordering are corruption, not an absent session.

## 3. Auth-service monotonic atomic publisher

`RedisSessionSecurityProjectionPublisher` executes one Lua script through
`StringRedisTemplate`. The script atomically:

1. validates any existing hash has all nine fields and schema `1`;
2. rejects an identity mismatch for the same generated Redis key;
3. ignores an incoming lower `tokenVersion` or `sessionVersion`;
4. prevents `REVOKED` or `EXPIRED` from ever returning to `ACTIVE` for the same
   session identifier, even if the incoming version is higher;
5. writes all fields with `HSET`; and
6. applies `PEXPIREAT` using `absoluteExpiresAtEpochMs` in the same script.

Result `1` is `APPLIED`, result `0` is an idempotent/stale
`STALE_IGNORED`. Missing, corrupt, identity-conflicting or indeterminate script
results raise `SessionProjectionPublicationException`; they are never reported
as success.

The publisher is an outbound port adapter. A login, revoke, user-disable and
session-expiry mutation must reach it from an idempotent outbox consumer after
the owning MySQL transaction commits. SPEC 45 adds that delivery foundation;
the business producer must still append in the same owning transaction, so the
adapter and relay alone do not prove revocation convergence.

## 4. Gateway reactive reader and decision boundary

`RedisReactiveSessionSnapshotReader` uses
`ReactiveStringRedisTemplate.opsForHash().multiGet` with the exact shared field
list. A completely absent hash produces an empty result, which SPEC 43 maps to
invalid session/401. A partial or malformed present hash throws a format
exception. Redis/serialization errors propagate. The access filter maps those
dependency failures to generic 503 and has no in-memory or stale-session
fallback.

The reader key uses tenant and session identifiers from a verified access
token. The decoded tenant, subject, session and both versions are still compared
exactly by `AuthoritativeReactiveSessionStateVerifier`; the key alone never
establishes identity.

## 5. HTTPS access-JWKS transport

When access authentication is enabled and no custom
`AccessTokenPublicKeyResolver` or `AccessTokenJwkSetLoader` is supplied, Gateway
constructs `AllowlistedHttpsAccessTokenJwkSetLoader`. External configuration
must provide one immutable `jwks-uri` and one or more exact
`jwks-allowed-hosts`.

The adapter enforces:

| Boundary | Frozen behavior |
|---|---|
| Scheme/authority | HTTPS only; DNS hostname; no userinfo; default/443 port only |
| Host policy | exact normalized allowlist; no wildcard, IP literal or reserved local/test suffix |
| URI remainder | non-empty normalized path; no query, fragment, encoded dot/slash/backslash or traversal |
| DNS preflight | every answer required; mixed answers reject; local, private, link-local, multicast, CGNAT, documentation, benchmark and reserved ranges reject |
| Proxy/redirect | ambient proxy disabled; JDK redirect policy `NEVER` |
| TLS | JVM default trust and hostname verification; TLS 1.2/1.3 only |
| Time | connect timeout positive and at most 5 s; request timeout positive and at most 10 s; defaults 2 s/3 s |
| Response | status exactly 200; one JSON/JWK-set content type; identity/no content encoding; unambiguous bounded content length |
| Body | streaming read capped at 65,536 bytes; strict UTF-8 |

The existing bounded JWKS parser remains the second boundary: at most 32
public P-256 ES256 verification keys, bounded cache/negative cache and no stale
serve after expiry/load failure.

## 6. DNS and network residual risk

DNS validation before the request is not IP pinning. The JDK client can resolve
the hostname again while establishing the TLS connection, which creates a
DNS-rebinding/time-of-check-to-time-of-use residual risk. Production deployment
must also enforce DNS policy and destination egress allowlisting at the
container/network layer. Redirects remain disabled and the URI is not derived
from request data, so a response cannot choose a second destination.

## 7. Configuration and fail-closed startup

- Access authentication remains disabled by default.
- Enabling it with default trust wiring but without `jwks-uri` or an exact host
  allowlist fails application-context creation.
- A deployment may replace the loader/resolver explicitly; custom resolver
  wiring does not require the default URI properties.
- With the reactive Redis template available, the strict reader is installed.
  Without a reader/template or custom verifier, protected authentication wiring
  cannot complete.
- Redis connection details and TLS/credentials are deployment configuration;
  there is no password or local in-memory production fallback in source.

## 8. Evidence and required CI/runtime proof

Focused source tests cover schema round trip/corruption, absent versus partial
hash, Redis failure propagation, Lua monotonic/terminal markers, HTTPS URI and
host policy, mixed/private DNS, redirect/type/encoding/length rejection,
streaming size, strict UTF-8 and default adapter application-context wiring.

`tools/validate_trust_adapters.py` freezes these cross-module markers and runs
in backend and contract workflows. Local validation can prove syntax and
structure only. Release evidence still requires:

1. Java 21 Maven/JUnit execution;
2. Redis Testcontainers execution of the Lua script under concurrent,
   duplicate, stale and terminal-state updates;
3. live HTTPS certificate, timeout, redirect, rotation and DNS/egress tests;
4. outbox-to-Redis lag and revoke-convergence SLO tests; and
5. outage metrics/alerts and a deployed protected-route trace.

## 9. Remaining blockers

1. Wire login, revoke, disable and expiry transactions to the SPEC 45 appender
   in the same local transaction and expose projection-lag telemetry.
2. Integrate KMS/HSM-backed access and delegation signing capability.
3. Reuse the hardened transport boundary behind the profile-specific
   downstream `DelegationJwkSetLoader`; SPEC 44 implements the access profile
   only.
4. Persist login sessions and issue access/refresh token pairs atomically.
5. Configure infrastructure DNS/egress restrictions, Redis TLS/ACLs and real
   Gateway route inventory.
6. Execute Maven, Redis, HTTPS and end-to-end revocation evidence in CI.
7. Complete authorization repositories/endpoints and the grant → ALLOW →
   revoke → immediate DENY golden path.

## 10. References

- Java 21 `HttpClient`: https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html
- Java 21 `HttpRequest.Builder`: https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html
- Java 21 streaming body handler: https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.BodyHandlers.html
- Spring Data Redis scripting: https://docs.spring.io/spring-data/redis/reference/redis/scripting.html
- Spring Data Redis `ReactiveHashOperations`: https://docs.spring.io/spring-data/redis/reference/3.5/api/java/org/springframework/data/redis/core/ReactiveHashOperations.html
