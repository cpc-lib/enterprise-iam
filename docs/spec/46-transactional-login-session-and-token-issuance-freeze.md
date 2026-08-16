# 46 — Transactional Login Session and Token Issuance Freeze — SPEC 1.10.1

## 1. Authority and scope

SPEC 46 is authoritative for creating a new login session, access token,
refresh token and session-projection event as one commit-before-return unit.
SPEC 45 remains authoritative for Outbox delivery; SPEC 44 remains
authoritative for the Redis projection; SPEC 43 remains authoritative for the
access-token profile and Gateway session fence.

This slice implements a concrete `TransactionalJdbcLoginSessionIssuer` and
security primitives. It does not yet implement the login HTTP controller,
refresh rotation endpoint, credential/identity JDBC adapters, KMS/HSM provider,
success-audit transaction, logout/revoke/disable/expiry commands or deployed
Gateway path.

The 1.10.1 hardening patch is authoritative for the signed-64-bit external ID
range, login response/cookie schema, session-limit error mapping and explicit
login retry boundary. It does not upgrade any open runtime gate to PASS.

## 2. Startup and capability boundary

Session issuance is disabled by default. Enabling
`iam.auth.session-issuance.enabled=true` requires:

- `JdbcTemplate` and `PlatformTransactionManager`;
- an `AccessTokenSigner` capability, normally backed by KMS/HSM;
- a `RefreshTokenHashKey` using HMAC-SHA-256;
- the SPEC 45 `SessionProjectionOutboxAppender`; and
- an explicit deployment-unique node ID from 0 through 1023.

There is no generated signing key, source-controlled HMAC secret or in-memory
database fallback. Missing capabilities or invalid TTL/session limits fail
application startup when issuance is enabled.

## 3. Commit-before-return transaction

The issuer uses one `REQUIRES_NEW`, `READ_COMMITTED`, five-second transaction.
It performs these operations in order:

1. load and lock the exact tenant/user row from `iam_user_security_state` using
   `FOR UPDATE`;
2. reject missing/non-positive `token_version`;
3. count still-valid ACTIVE sessions while the per-user security row remains
   locked, enforcing the configured maximum of ten by default;
4. allocate distinct session-row, session, refresh-token, token-family and
   projection-event IDs;
5. generate the opaque refresh credential and compute its keyed hash;
6. insert one ACTIVE `iam_login_session` at session version 1;
7. insert one ACTIVE `iam_refresh_token` containing only the 32-byte HMAC;
8. append the matching ACTIVE session projection Outbox row;
9. request an audience-bound signed access token containing the same tenant,
   subject, session, token version and session version; and
10. commit before returning the credential pair.

Database, Outbox, HMAC, ID or signing failure rolls back all durable rows. A
refresh credential generated before failure is destroyed and is never returned.
The signed token issuance time must be within 30 seconds of the transaction
clock and its expiry cannot exceed session absolute expiry.

### 3.1 Login retry and success-audit boundary

Login is explicitly excluded from generic `Idempotency-Key` response replay.
The successful response contains refresh-token plaintext that is deliberately
non-recoverable after delivery; the platform MUST NOT persist it merely to
replay a response. `requestId` is correlation-only and is not a deduplication
key. A client retry is a new authentication attempt and may create a new
session, subject to the locked concurrent-session limit.

This exception must be visible in OpenAPI through
`x-iam-idempotency-policy=EXPLICITLY_EXCLUDED_NON_REPLAYABLE_SECRET`. Before
production, the HTTP adapter must also close the ambiguous-result window with a
durable success-audit path and orphan-session reconciliation. Until then, a
post-commit delivery or audit failure remains a release blocker rather than an
implicitly successful retry contract.

## 4. Session and ID invariants

The default idle lifetime is one day, absolute session lifetime 30 days,
refresh-token lifetime 14 days and maximum concurrent valid sessions ten.
Durations are positive whole seconds and refresh/idle lifetime cannot exceed
absolute lifetime.

`TimeOrderedPositiveIdGenerator` uses a frozen 2024-01-01 UTC epoch:

| Bits | Value |
|---:|---|
| 41 | millisecond timestamp delta |
| 10 | explicit deployment node ID |
| 12 | per-millisecond sequence |

Clock regression, pre-epoch time, sequence exhaustion and range exhaustion fail
closed. The locked security-state row serializes concurrent issuance for one
tenant/user; different users can proceed independently.

All externally serialized identifiers use positive signed-64-bit decimal
semantics with a maximum value of `9223372036854775807`. MySQL
`BIGINT UNSIGNED` columns are a storage superset and do not widen the API or
Java domain range.

## 5. Refresh-token protection

Each refresh token has the exact shape `rt1.{keyId}.{base64urlRandom}`. The
random component is 32 bytes from `SecureRandom`, base64url-encoded without
padding, providing 256 random bits. The key ID is validated safe metadata that
supports future hashing-key rotation; it is not a secret.

The complete ASCII token is HMAC-SHA-256 hashed. Exportable keys must contain at
least 256 bits; non-exportable HSM keys are supported. The database stores only
the 32-byte hash. Plain refresh tokens are held in a clone-owning,
destroyable `SensitiveRefreshToken` buffer. Production code does not convert
that buffer into an immutable `String` for hashing.

`IssuedLoginSession`, `LoginResult`, `SignedAccessToken` and the refresh wrapper
redact token values from `toString()`. The future HTTP adapter must copy the
refresh buffer exactly once into `IAM_REFRESH`, set Secure, HttpOnly,
SameSite=Strict and path `/api/v1/auth` in the exact frozen order
`Path; Secure; HttpOnly; SameSite`, then destroy the buffer in `finally`.
The OpenAPI login and refresh success responses freeze the complete
`Set-Cookie` shape. The raw refresh token is never part of JSON. Access tokens
are sensitive response values and MUST NOT be annotated `writeOnly`.

`SessionLimitExceededException` maps to HTTP `409` with
`IAM_AUTH_SESSION_LIMIT_REACHED`; authentication rate limiting remains the
separate HTTP `429` / `IAM_AUTH_RATE_LIMITED` condition.

## 6. Access signing boundary

`AccessTokenSigner` returns a `SignedAccessToken` containing compact JWS text
and authoritative issue/expiry instants. Compact syntax, 8,192-character limit,
positive whole-second lifetime and five-minute maximum are checked before the
credential can leave the transaction.

`Es256AccessTokenSigner` implements the capability for externally supplied
P-256 keys. A deployment may instead provide an HSM/KMS signer without
exporting private key bytes. There is no automatic local-key fallback.

## 7. Evidence

Focused tests cover:

- ID ordering, node extraction, invalid node and pre-epoch clock;
- 256-bit refresh randomness, version/key prefix and HMAC determinism;
- short/wrong-algorithm hashing keys;
- refresh destruction and all credential-bearing `toString()` redaction;
- disabled-by-default and enabled fail-closed configuration;
- MySQL 8.4 commit of session + refresh hash + Outbox before return;
- rollback of all three rows when signing fails; and
- missing authoritative security state denial; and
- two simultaneous issuers for one user with limit one, proving the locked row
  permits exactly one committed session and rejects the competing transaction.

`tools/validate_session_issuance.py` freezes implementation, contract, test and
CI markers. The MySQL integration suite is wired to Maven Failsafe and is not
considered passed until Java 21 + Docker CI publishes its report.

## 8. Remaining blockers

1. Supply production KMS/HSM `AccessTokenSigner` and HMAC-key capabilities.
2. Implement identity/credential repositories, login REST adapter and exact
   Secure/HttpOnly/SameSite cookie write + buffer destruction.
3. Make successful login audit delivery durable without a post-commit sink gap.
   Add orphan-session reconciliation for ambiguous response delivery before
   treating login retry behavior as production complete.
4. Implement refresh rotation/reuse family revocation per RFC 9700.
5. Implement logout, user-disable and expiry transactions with projections.
6. Run Java 21 Maven/JUnit, MySQL/Redis Testcontainers and end-to-end login →
   Gateway evidence.
7. Complete authorization repositories/endpoints and the grant/revoke golden
   path.

## 9. References

- Spring programmatic transactions: https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html
- OAuth 2.0 Security Best Current Practice: https://www.rfc-editor.org/rfc/rfc9700.html
- Java 21 `SecureRandom`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html
- Java 21 `Mac`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html
