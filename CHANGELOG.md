# Changelog

## 1.10.0-session-issuance

- Added commit-before-return JDBC login-session issuance with a locked
  authoritative security version and serialized concurrent-session limit.
- Added atomic session, refresh-token HMAC and session-projection Outbox writes.
- Added 256-bit opaque refresh credentials, key-ID rotation prefix, destroyable
  buffers and credential-bearing model redaction.
- Added KMS/HSM-compatible access signing capability and strict enablement gates.
- Added SPEC 46, MySQL rollback/commit evidence and CI-wired structural validation.

## 1.9.0-session-outbox

- Added reusable transaction-enforcing JDBC Outbox writer and a disabled-by-default
  leased MySQL `SKIP LOCKED` relay.
- Added bounded retry, deterministic jitter, lease fencing, immediate poison-event
  dead-lettering and low-cardinality delivery metrics.
- Added strict auth session-projection event codec/handler and canonical/runtime
  auth V2 migration.
- Added SPEC 45, focused tests and CI-wired structural validation.
- Kept the missing login/revoke/disable/expiry transactional call sites explicit.

## 1.8.0-trust-adapters

- Added exact-host, HTTPS-only, bounded access-JWKS transport with explicit DNS,
  redirect, proxy, TLS, timeout and response controls.
- Added shared versioned Redis session projection, strict reactive reader and
  atomic monotonic terminal-safe publisher.
- Added SPEC 44, focused tests and CI-wired structural validation.

## 1.7.0-access-authentication

- Added strict ES256 `at+jwt` access signing/verification with singleton Gateway
  audience and security-version claims.
- Added fail-closed authoritative session snapshot verification.
- Added ordered Gateway access authentication before route-bound delegation.
- Added SPEC 43, focused tests and CI-wired static validation.

## 1.6.0-delegation-wiring

- Added bounded rotating JWKS key resolution with unknown-key refresh control.
- Added opt-in Servlet delegation auto-configuration and internal path binding.
- Added explicit Gateway public/protected route policy and audience-bound
  delegation issuance while removing external bearer credentials downstream.

## 1.5.0-auth-crypto-core

- Added real P-256 ES256 delegation signing and verification components.
- Added fail-closed downstream trusted-context filtering with distinct key
  dependency-unavailable handling.
- Added Argon2id PHC verification and enumeration-resistant login core.
- Added SPEC 41, focused security tests and CI-wired static validation.

## 1.4.0-security-core

- Added fail-closed authorization precedence, delegation claim policy and
  Gateway identity-header sanitization.
- Added MySQL 8.4 Flyway runtime migrations and Testcontainers gates.

## 1.3.0-build-foundation

- Added the complete Maven Reactor, Java 21 launchers, CI and structural build
  validation.

## 1.2.0-code-ready

- Added SPEC 38 core V1 authorization machine-contract freeze.
- Added phased policy sharing and file OpenAPI contracts.
- Upgraded and validated AsyncAPI contracts at 3.1.0.
- Added Organization Authorization Sharing and File physical DDL baselines.
- Expanded traceability to 45 requirements and phase acceptance cases.
- Added coverage and quality matrices with explicit remaining blockers.

## 1.1.0-code-ready

- Added SPEC 37 first vertical-slice implementation freeze.
- Added OpenAPI AsyncAPI security parameters initial DDL and contract CI.

## 0.1.0-spec-freeze
- 冻结 Enterprise IAM V1.0 项目架构
- 冻结 SPEC 01~24
- 建立 Backend + React Admin + Deploy + Test + Docs Monorepo
