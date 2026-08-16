# CODE-READY 1.8 Changelog

## Added

- Shared versioned Redis session-security projection schema and strict codec.
- Gateway reactive HMGET projection reader with absent/corrupt distinction and
  fail-closed error propagation.
- Auth-service Lua projection publisher with atomic HSET + PEXPIREAT,
  monotonic token/session versions and terminal-state anti-reactivation.
- Exact-host HTTPS access-JWKS loader using the Java 21 HTTP client.
- DNS/private-range, URI, proxy, redirect, TLS, timeout, response metadata,
  streaming size and strict UTF-8 controls.
- Focused projection, Redis adapter, JWKS transport and application-context tests.
- `validate_trust_adapters.py` and SPEC 44.

## Hardened

- A partial or unsupported Redis projection can no longer masquerade as an
  absent session or be repaired silently by the writer.
- Duplicate/out-of-order updates cannot decrease either security version, and a
  terminal session ID cannot become ACTIVE again.
- Default JWKS fetching cannot use HTTP, ambient proxies, redirects, IP literals,
  unsafe paths, request-derived destinations, private/mixed DNS answers,
  compressed bodies or unbounded response allocation.
- Enabled default access-authentication wiring requires explicit JWKS URI and
  host allowlist while retaining explicit custom-adapter ports.

## Changed

- Maven Reactor version advanced to `1.8.0-SNAPSHOT` across all 31 POMs.
- Gateway and auth-service now include Spring Data Redis reactive/imperative
  dependencies respectively.

## Not claimed

- DNS preflight does not pin the connection IP; infrastructure DNS and egress
  policy remain required.
- Projection adapters are not yet connected to login/revoke/disable/expiry
  transactions through an outbox, and no convergence SLO is proven.
- KMS/HSM, downstream delegation-JWKS network wiring, login
  persistence/token-pair transaction, real routes and deployment enablement
  remain open.
- Maven/JDK 21/JUnit/Redis/Testcontainers/live HTTPS evidence was not run on the
  packaging host.
- This artifact remains an implementation seed, not a production IAM backend.
