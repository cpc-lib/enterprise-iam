# 42 — Delegation Key Rotation and Service Wiring Freeze — SPEC 1.6

## 1. Authority and scope

SPEC 42 is authoritative for the implemented internal-delegation key rotation,
Servlet service auto-configuration and Gateway route-to-audience issuance
boundary. SPEC 38 remains authoritative for authorization semantics, SPEC 39
for the Reactor, SPEC 40 for the authorization core and SPEC 41 for the
underlying ES256 and login crypto components.

This slice turns the 1.5 delegation components into opt-in, fail-closed service
wiring. It does not implement external access-token authentication, an HTTP
JWKS client, KMS/HSM key retrieval, production route definitions or a deployed
end-to-end request.

## 2. Module boundary correction

`iam-common-security` is now a framework-independent crypto/policy module. It no
longer imports Servlet, Spring Web or Spring Security Web types. The Servlet
request filter and path matching live in `iam-security-spring-boot-starter`.

This prevents the reactive Gateway from acquiring a Servlet security stack only
because it uses the common ES256 signer. The starter owns:

- conditional Servlet auto-configuration;
- exact issuer/single-audience/time policy construction;
- rotating JWKS resolver construction when a loader port exists;
- protected internal path matching;
- filter registration and generic error mapping.

## 3. Rotating JWKS resolver

`RefreshingJwkSetDelegationPublicKeyResolver` accepts a deployment-owned
`DelegationJwkSetLoader`. The loader is deliberately transport-neutral: file,
configuration service, sidecar or HTTPS clients can implement it without
placing network behavior in the crypto core.

The resolver freezes these bounds:

| Constraint | Value / behavior |
|---|---|
| JWKS document | UTF-8, 1–65,536 bytes |
| Parsed key count | 1–32 |
| Usable key type | public EC P-256 only |
| JOSE metadata | explicit `use=sig`, `alg=ES256` |
| Key operations | absent or includes `verify` |
| `kid` | safe 1–128 character identifier |
| Duplicate usable `kid` | reject entire refresh |
| Normal cache TTL | 300 seconds |
| Unknown-`kid` negative cache | 30 seconds |
| Global unknown-`kid` refresh minimum interval | 5 seconds |
| Negative-cache entry maximum | 1,024 |

An unexpired known key is served from the immutable cache. An unknown `kid`
triggers one synchronized refresh; a repeated attacker-controlled `kid` is
negative-cached to bound refresh amplification. After normal cache expiry, a
loader or parse failure throws and becomes downstream 503. Different random
unknown key IDs also share a five-second global refresh cooldown, preventing ID
spray from causing one loader call per request. If an unknown key arrives during
that cooldown, its negative-cache entry expires no later than the next allowed
refresh; a legitimate rotated key is therefore not hidden for the full
30-second negative-cache window. The negative cache is capped at 1,024 entries
so random key-ID spray cannot create unbounded heap retention. Expired keys are
not silently reused.

Rotation overlap remains an issuer/operator responsibility: the JWKS producer
must publish old and new public keys concurrently for the frozen overlap window.
The resolver accepts both while present and stops accepting a removed key after
the next successful refresh.

## 4. Servlet service auto-configuration

`IamDelegationSecurityAutoConfiguration` activates only when all of these are
true:

1. the application is Servlet-based;
2. `iam.security.delegation.enabled=true` is explicit;
3. issuer, exact service audience, protected internal paths and time bounds are
   valid;
4. a `DelegationPublicKeyResolver` exists, or a `DelegationJwkSetLoader` exists
   from which the resolver can be built.

Enabling without a key source fails application startup. Protected paths are
parsed with Spring `PathPatternParser` and must remain at `/internal` or below;
`/**`, actuator and public API patterns are rejected.

The authorization service consumes the starter with the exact audience
`iam-authorization-service` and `/internal/**` path policy. The packaged default
keeps the feature disabled so this non-production skeleton can start without a
key loader. Production deployment must enable it and inject a trusted loader or
resolver; the current package does not claim that runtime step is complete.

## 5. Gateway route security policy

`GatewayDelegationFilter` runs after the highest-priority spoof-header sanitizer
and after the future external authentication stage. It reads only
`AuthenticatedGatewayPrincipal`, a Gateway exchange attribute intended to be
created by that future verifier.

Every matched Gateway route must have exactly one explicit security policy:

- protected route → exact downstream audience; or
- public route → explicit public-route set membership.

An unknown route, missing route attribute, overlap between public/protected
sets, malformed route/audience name or empty enabled policy fails closed.
Missing authenticated principal returns generic 401. Signing/KMS failure and
missing route policy return generic 503.

Before any downstream forwarding, the filter always removes the external
`Authorization` header and any pre-existing delegation header. A protected
route then receives exactly one newly signed `X-IAM-Delegation` token whose
audience comes from the route registry, never from a request header or query.
Downstream validation requires the audience set to equal that one service;
including the expected service alongside any additional audience is rejected.

## 6. Startup and key custody

Gateway configuration is opt-in through
`iam.gateway.delegation.enabled=true`. Enabling requires:

- a non-empty, validated route-audience/public-route policy;
- an injected `Es256DelegationTokenSigner`;
- therefore an external adapter that obtains signing capability from approved
  KMS/HSM custody.

No source private key, default shared secret, embedded JWK trust, HMAC fallback
or automatic ephemeral production key is supplied. The absent KMS/HSM adapter
is an explicit release blocker, not replaced with a test key.

## 7. Evidence

New test sources cover:

- known-key cache reuse and new-`kid` rotation refresh;
- unknown-`kid` negative caching;
- expired-cache loader failure;
- duplicate key IDs, wrong algorithm and oversized JWKS rejection;
- auto-configuration disabled-by-default and enabled fail-closed startup;
- `/internal/**` path constraint;
- protected route audience binding and cryptographic token verification;
- removal of external bearer/delegation headers;
- missing principal, signing failure and missing route-policy rejection.

`tools/validate_delegation_wiring.py` freezes the implementation-to-contract
markers and is wired into both backend and contract CI workflows.

The packaging host still lacks Maven, a JDK 21 compiler and Docker. Java tests
are syntax-checked and CI-wired source evidence, not an executed-pass claim.

## 8. Remaining blockers

1. Verify external access JWTs at Gateway and create
   `AuthenticatedGatewayPrincipal` only after issuer/audience/type/time/session
   checks.
2. Implement approved KMS/HSM signer acquisition and rotation.
3. Implement an allowlisted HTTPS JWKS loader with TLS, redirect, DNS/SSRF,
   response-size and timeout controls.
4. Define real Gateway routes and explicit public/protected policy for each.
5. Enable delegation enforcement in deployed downstream services.
6. Complete tenant-scoped login repositories, rate limits, lock/session/token
   transactions and REST mapping.
7. Implement authorization repositories/endpoint and the MySQL/Redis
   grant → ALLOW → revoke → immediate DENY golden path.

## 9. References

- Spring Boot auto-configuration: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- Spring Cloud Gateway global filters: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html
- Spring Cloud Gateway route metadata: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/route-metadata-configuration.html
- Nimbus `ECKey`: https://www.javadoc.io/doc/com.nimbusds/nimbus-jose-jwt/latest/com/nimbusds/jose/jwk/ECKey.html
