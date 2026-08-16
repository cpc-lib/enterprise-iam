# CODE-READY 1.10 Changelog

## Added

- Concrete `TransactionalJdbcLoginSessionIssuer` using a short
  `REQUIRES_NEW`/`READ_COMMITTED` transaction.
- Per-user `FOR UPDATE` security-state lock, positive token-version requirement
  and serialized maximum-concurrent-session enforcement.
- Atomic session, refresh-token hash and session-projection Outbox inserts before
  returning an access/refresh credential pair.
- 256-bit opaque `rt1.{keyId}.{base64url}` refresh credentials and
  HMAC-SHA-256-only persistence.
- Destroyable refresh-token buffer and redacted login/access token models.
- Time-ordered positive ID generator with explicit 10-bit deployment node and
  clock-regression/sequence-exhaustion failure.
- `AccessTokenSigner` capability suitable for non-exportable KMS/HSM signing.
- MySQL integration evidence for successful commit, signing rollback, missing
  security state and concurrent-session limit.
- `validate_session_issuance.py` and SPEC 46.

## Hardened

- A signing, hashing, ID, database or Outbox failure cannot leave a partial new
  login session.
- A missing/invalid user security version fails before credentials are created.
- Refresh token plaintext is not persisted and production hashing does not create
  an immutable token `String`.
- Enabling issuance without signing/hash capabilities or an explicit node ID
  fails startup; source configuration remains disabled by default.
- Login OpenAPI success now requires a Secure HttpOnly refresh `Set-Cookie`
  boundary instead of returning refresh plaintext in JSON.

## Changed

- Maven Reactor version advanced to `1.10.0-SNAPSHOT` across all 31 POMs.
- SPEC inventory advanced to 46 and deterministic validator count to nine.

## Not claimed

- No production KMS/HSM signer or HMAC-key provider is bundled.
- Login HTTP/Cookie delivery, identity/credential JDBC adapters and durable
  success-audit wiring remain open.
- Refresh rotation/reuse, logout, disable and expiry commands remain open.
- Java 21 Maven/JUnit/MySQL/Redis/Testcontainers execution was not possible on
  the packaging host.
- Authorization repositories/endpoints and the full grant/revoke golden path
  remain open.
