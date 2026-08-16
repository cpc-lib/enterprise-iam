# 43 — Gateway Access Authentication and Session Fence Freeze — SPEC 1.7

## 1. Authority and scope

SPEC 43 is authoritative for the implemented external access-token profile,
Gateway identity-establishment filter and authoritative session-version fence.
SPEC 42 remains authoritative for downstream delegation and route audience
binding; SPEC 41 remains authoritative for login/password and the underlying
ES256 primitives.

This slice closes the component-level gap between an external bearer token and
the existing internal delegation issuer. SPEC 44 / 1.8 subsequently supplies
the HTTPS JWKS and Redis projection adapters, SPEC 45 supplies the durable
Outbox, and SPEC 46 supplies the opt-in JDBC login-session producer. Production
KMS/HSM signing, HTTP/Cookie delivery and real deployed routes remain outside
this SPEC.

## 2. Access-token profile

The IAM access-token profile is deliberately distinct from the internal
delegation token:

| Constraint | Frozen value / behavior |
|---|---|
| JOSE algorithm | ES256 only |
| `typ` | exactly `at+jwt` |
| issuer | exactly `iam-auth-service` |
| audience | exactly one value: `iam-gateway` |
| compact-token maximum | 8,192 bytes |
| lifetime | at most 300 seconds |
| clock skew | at most 30 seconds |
| required identity | positive `tid`, `sub`, `sid` |
| revocation versions | positive `tver`, `sver` |
| token identifier | safe non-blank `jti` |
| key selection | safe required `kid`; P-256 public JWKS key |
| remote/embedded key headers | `jwk`, `jku`, `x5u`, `x5c`, `b64`, `crit` rejected |

`Es256AccessTokenDecoder` verifies header policy and signature before creating
`VerifiedAccessToken`. It then verifies issuer, singleton audience, issue/not-
before/expiry ordering, future `iat`, TTL and all positive integer identity and
version claims. Fractional numeric claims are rejected rather than truncated.

`Es256AccessTokenSigner` produces the same profile, fixes its audience at
construction rather than accepting it per issuance request, and receives its
private key through its constructor. The source tree includes no production
key, default secret or ephemeral production fallback. SPEC 46 adds a signing
capability and real session transaction; production KMS/HSM acquisition remains
required before enabling it.

## 3. Shared bounded JWKS cache

Access and delegation token verification now share
`BoundedRefreshingJwkSetPublicKeyResolver`. Profile-specific loader and resolver
types prevent accidental wiring of auth-service and Gateway trust stores even
though cache mechanics are reused.

The existing 65,536-byte document, 32-key, five-second refresh cooldown,
30-second per-key negative TTL and 1,024-entry negative-cache limits remain
authoritative. Only public P-256 `use=sig`, `alg=ES256` keys with absent or
`verify` key operations are accepted. Loader/parse failure after expiry never
serves stale keys.

## 4. Authoritative session fence

A valid signature is necessary but not sufficient. Every protected-route
request must load an authoritative session snapshot containing:

- tenant, subject and session identifiers;
- user `tokenVersion`;
- login `sessionVersion`;
- session status;
- idle and absolute expiry instants.

`AuthoritativeReactiveSessionStateVerifier` returns ACTIVE only when every
identifier and version exactly matches the verified token, the status is
exactly `ACTIVE`, idle expiry does not exceed absolute expiry, and both expiry
instants are in the future. A missing snapshot,
revoked status, mismatch or expiry returns INVALID. Reader exceptions propagate
as dependency failure; they never become ACTIVE.

`ReactiveSessionSnapshotReader` is a deployment port. SPEC 44 supplies the
strict Redis reader and atomic monotonic projection writer. SPEC 45 supplies the
transactional Outbox, and SPEC 46 wires login creation into it. Revoke, disable
and expiry producers are still required.

## 5. Gateway trust sequence

The fixed order is:

1. external identity/delegation header sanitizer — highest precedence;
2. access authentication — highest precedence + 1,000;
3. downstream delegation issuance — highest precedence + 2,000.

Startup wiring verifies this order. Authentication always removes any existing
authenticated-principal exchange attribute first. For a protected route it
requires exactly one syntactically valid Bearer header, validates the access
token on a bounded-elastic worker, validates session state, generates an
authoritative request ID and only then publishes
`AuthenticatedGatewayPrincipal`.

The delegation filter consumes that attribute, removes the external bearer and
any pre-existing delegation header, and inserts a new exact route-audience
delegation token. No tenant, user, session, audience or request identity is read
from client-controlled identity headers.

Explicit public routes do not invoke token or session dependencies and do not
create a principal. The subsequent delegation filter still removes external
credentials before forwarding. Missing or unregistered route policy fails
closed before authentication.

## 6. Error and reactive boundaries

| Condition | Public result |
|---|---|
| missing/malformed/invalid token | generic 401 + `WWW-Authenticate: Bearer` |
| unknown `kid` | generic 401 |
| revoked/missing/version-mismatched session | generic 401 |
| JWKS or session dependency unavailable | generic 503 |
| missing route security policy | generic 503 |

Validation reasons and dependency exception messages are not returned. Error
recovery is scoped to decoder and session-reader stages. After successful
authentication, downstream application errors propagate normally and are not
rewritten as authentication failures.

## 7. Startup behavior

`iam.gateway.access-authentication.enabled=true` is opt-in. Enabled startup
requires all of the following:

- validated issuer/audience/time/cache configuration;
- an access-token public-key resolver or access JWKS loader;
- a session-state verifier or session-snapshot reader;
- the explicit route security registry;
- the sanitizer and delegation filter.

SPEC 44 supplies default HTTPS loader and Redis reader implementations. Default
enablement now requires explicit JWKS URI/host configuration and a reactive
Redis template; otherwise wiring still fails closed. Explicit custom adapters
remain supported.

## 8. Evidence

Focused test sources cover:

- ES256 access signing/verification and versioned identity round trip;
- wrong/multiple audience, future `iat`, fractional versions, algorithm
  substitution, unknown key, key outage, oversized and tampered tokens;
- exact session snapshot acceptance and missing/revoked/expired/version-
  mismatch denial;
- Redis/read failure propagation;
- missing/malformed bearer, session invalid and dependency-outage responses;
- public-route dependency bypass without identity creation;
- unregistered-route denial and downstream error-boundary preservation;
- verified access → active session → exact route-audience delegation chain;
- disabled-by-default and enabled fail-closed application context.

`tools/validate_access_authentication.py` freezes these markers and runs in both
backend and contract CI workflows.

The packaging host still lacks Maven, a Java 21 compiler and Docker. The Java
sources have syntax evidence only; JUnit/context/Testcontainers results remain
CI-required.

## 9. Remaining blockers

1. Wire revoke/disable/expiry mutations through the transactional Outbox and
   add outage/lag observability; SPEC 46 already wires login creation.
2. Integrate KMS/HSM-backed access and delegation signing capability.
3. Supply production session-issuance capabilities and expose the SPEC 46
   transaction through the login HTTP/Cookie boundary.
4. Define all real Gateway routes and explicit protected/public policy.
5. Enable access authentication and delegation in deployed environments.
6. Execute Java 21 Maven, reactive context, MySQL/Redis and end-to-end revoke
   evidence in CI.
7. Complete authorization repositories/endpoints and the grant → ALLOW →
   revoke → immediate DENY golden path.

## 10. References

- RFC 8725 JWT Best Current Practices: https://www.rfc-editor.org/rfc/rfc8725
- RFC 9068 JWT Access Token Profile: https://www.rfc-editor.org/rfc/rfc9068
- Spring Security reactive JWT resource server: https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html
- Spring Cloud Gateway global filters: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html
