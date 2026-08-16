# 41 — Authentication and Delegation Crypto Implementation Freeze — SPEC 1.5

## 1. Authority and scope

SPEC 41 is authoritative for the implemented authentication-core and internal
delegation-crypto slice. SPEC 38 remains authoritative for authorization
semantics, SPEC 39 for the Reactor/toolchain and SPEC 40 for the earlier
Phase-01 authorization core. A conflict is resolved toward the more restrictive
security behavior.

This slice implements a real ES256 compact-JWT signer and verifier, a
fail-closed downstream servlet filter, an Argon2id PHC adapter and an
enumeration-resistant login application use case. It does not claim that these
components are wired to production key custody, JWKS, persistence adapters,
HTTP controllers or a deployed end-to-end flow.

## 2. Internal delegation trust boundary

External bearer tokens and client-supplied identity headers are not trusted by
downstream services. The intended path is:

```text
external request
→ Gateway authenticates external credential
→ Gateway constructs authoritative tenant/user/session context
→ Gateway signs audience-bound ES256 delegation JWT
→ Downstream verifies signature and claims
→ TrustedRequestContext is published to the protected request only
```

`Es256DelegationTokenSigner` emits only P-256 / ES256 tokens with:

- `typ=iam-delegation+jwt` and a required `kid`;
- `iss`, a single downstream-service `aud`, `sub`, `jti`, `iat`, `nbf`, `exp`;
- positive `tid` and `sid`, plus correlation `rid`;
- a constructor-enforced TTL in the range `(0, 30 seconds]`.

No private key or production default is stored in source. The signer accepts an
injected `ECPrivateKey`; production creation of that key from an external KMS or
HSM boundary remains an adapter task.

## 3. Verification order and algorithm-confusion fence

`Es256DelegationTokenDecoder` applies the following order before any identity
context is trusted:

| Order | Check | Failure behavior |
|---:|---|---|
| 1 | Token exists and compact length ≤ 4096 | DENY |
| 2 | Compact JWT parses | DENY |
| 3 | Header algorithm is exactly ES256 | DENY before key lookup |
| 4 | Header type is exactly `iam-delegation+jwt` | DENY |
| 5 | `kid` matches the 1–128 character safe identifier grammar and resolves through the public-key port | invalid/unknown key DENY; resolver outage unavailable |
| 6 | Resolved EC key exactly matches secp256r1 curve, base point, order and cofactor | DENY |
| 7 | ECDSA signature verifies | DENY |
| 8 | Verified claims map to the frozen policy | DENY on parse/type failure |
| 9 | Issuer, audience, time, TTL and required context pass | publish trusted context |

HMAC, `none`, embedded JWK trust and algorithm negotiation are absent from the
main implementation. `DelegationPublicKeyResolver` is the only key-resolution
port. Cache policy, refresh-on-unknown-`kid`, rotation overlap and remote JWKS
transport are not implemented in this slice. Resolver exceptions are retained
as `KEY_RESOLUTION_UNAVAILABLE`; they are not mislabeled as malformed tokens.

## 4. Downstream fail-closed request fence

`TrustedDelegationFilter` protects only paths selected by an injected
`RequestMatcher`. On a missing or invalid token it:

- stops the filter chain;
- returns HTTP 401 and `Cache-Control: no-store`;
- returns the stable public code `IAM_AUTHENTICATION_REQUIRED`;
- never includes the signature/claim validation reason.

A public-key resolver outage also stops the chain but returns generic HTTP 503 /
`IAM_AUTHENTICATION_DEPENDENCY_UNAVAILABLE`. The response does not expose the
key source or internal exception. This preserves fail-closed behavior while
distinguishing infrastructure failure from caller authentication failure.

On success it publishes `TrustedRequestContext` as a request attribute and then
continues. It never constructs context from tenant, subject or session headers.
The filter is a reusable component; per-service registration, protected path
selection, issuer/audience configuration and key-resolver wiring remain open.

## 5. Password verification contract

`Argon2idPasswordVerifier` is frozen to the normative security parameters:

| Parameter | Value |
|---|---:|
| Algorithm / storage | Argon2id / PHC |
| Unicode normalization | NFC |
| Salt | 16 bytes |
| Output | 32 bytes |
| Memory | 19,456 KiB |
| Iterations | 2 |
| Parallelism | 1 |

Spring Security's Argon2 encoder is backed by Bouncy Castle. A random dummy
secret is hashed once per verifier instance. Unknown identities and missing
credentials verify against that dummy PHC. A malformed or non-Argon2id stored
value fails closed and also executes the dummy verification path.

Minimum password length is a credential-creation rule, not a login-existence
oracle. Structurally valid login attempts of length 1–128 enter the same core
verification path; credential creation must separately enforce the normative
minimum of 12.

## 6. Enumeration-resistant login core

For every structurally valid `LoginCommand`, `AuthenticateLoginUseCase` performs:

1. one tenant/type/identity directory lookup;
2. one real credential lookup, or the repository's bounded dummy lookup;
3. one real Argon2id verification, or one dummy Argon2id verification;
4. status/lock/password decision after the expensive verification;
5. internal attempt recording with a non-public reason;
6. one frozen public rejection: `IAM_AUTHENTICATION_FAILED`, without a session;
7. zeroing of its password copy and destruction of the command buffer.

The command is one-shot: reading its password after consumption throws rather
than returning a zeroed buffer. Clearing Java `char[]` values is best-effort;
Spring's `PasswordEncoder` API and Unicode normalization still create temporary
immutable strings that remain until garbage collection. Logs, heap-dump access
and process isolation therefore remain part of the production secret boundary.

Only an active identity, matching tenant/user credential, active and unlocked
credential, and matching password may reach `LoginSessionIssuer`. Identity and
credential repositories are ports: MyBatis adapters, rate limiting, lock-update
transactions, session persistence, token issuance and a REST response mapping
are not implemented here.

Dependency failures are not converted into a false “bad password” success path.
The future interface adapter must map unavailable dependencies to a generic
service failure without revealing identity existence.

## 7. Executable and static evidence

CI now runs `tools/validate_auth_crypto.py` before Maven. JUnit source covers:

- valid P-256 ES256 sign/verify with audience binding;
- exact-curve rejection for a non-P-256 EC key;
- signature tampering, unknown `kid`, wrong audience and HS256 substitution;
- oversized/malformed token rejection;
- downstream generic 401 and context publication only after success;
- `SEC-AUTHN-001` equal public result, one credential path and one password path;
- inactive/cross-tenant credential denial and session issuance only after all checks;
- Argon2id PHC parameters, NFC equivalence and malformed-hash fail-closed behavior.

The packaging environment has no Maven, JDK 21 compiler or Docker. Therefore
these Java tests are CI-wired source evidence, not an executed pass claim.

The root Reactor and publishable `iam-dependencies` BOM manage Nimbus JOSE + JWT
`10.9.1` and Bouncy Castle `bcprov-jdk18on` `1.85.2`; child modules do not carry
local versions. These coordinates are explicit because Spring Boot's dependency
management does not manage either direct artifact.

## 8. Remaining release blockers

Gate B remains open. The next implementation slice must provide:

1. production KMS/HSM signer adapter and rotating JWKS resolver/cache;
2. Gateway external access-token verifier and delegation issuance filter;
3. downstream service filter registration with exact audience/path configuration;
4. tenant-scoped identity/credential MyBatis adapters and dummy-query fixture;
5. login rate limits, lock transaction, session/token persistence and REST mapping;
6. authorization fact repository and internal decision endpoint;
7. a real MySQL/Redis grant → ALLOW → revoke → immediate DENY golden test.

No component may silently fall back to a shared secret, source key, permissive
decoder, placeholder identity or ALLOW-on-error behavior.

## 9. Implementation references

- Nimbus JOSE + JWT ES256 example: https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-ec-signature
- Spring Security password storage: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- Spring Security `Argon2PasswordEncoder` API: https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.html
