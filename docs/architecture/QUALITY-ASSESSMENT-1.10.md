# CODE-READY Quality Assessment — 1.10

## Result

| Dimension | 1.9 | 1.10 | Evidence status |
|---|---:|---:|---|
| Architecture/business design | 96% | 96% | SPEC 46 closes issuance semantics; HTTP/refresh/revoke flows remain |
| Core authorization contract readiness | 94–95% | 94–95% | Authorization decision core is unchanged |
| Reactor structural completeness | 98% | 98% | Issuer/config/security components are structurally wired |
| Executed Java/Maven evidence | 0% local | 0% local | Syntax/static evidence only; CI remains required |
| Phase-01 security implementation | 82–85% | 86–88% | Login credential transaction is now concrete |
| Production key/session integration | ~68% | ~76% | Atomic issuance exists; KMS and HTTP adapters remain |
| Production readiness | ~61% | ~65% | Partial-commit/token handling improved; deployed proof absent |

Percentages are bounded engineering estimates, not coverage or release metrics.

## Verified locally

- Issuance owns a commit-before-return transaction and rejects inactive identity,
  missing security state and invalid token version.
- A locked per-user security row serializes the valid-session count boundary.
- Session, 32-byte refresh HMAC and matching projection Outbox use one transaction.
- Signing failure rolls back every durable write in the wired MySQL integration
  test source; the generated refresh buffer is destroyed on the failure path.
- Refresh credentials contain 256 random bits, a version/key-ID prefix and are
  never stored in plaintext.
- Access/refresh-bearing domain results redact `toString()` output.
- Signing and HMAC keys are injected capabilities with no production default.
- Session issuance remains disabled and lacks a source-default deployment node.

## CI and runtime execution required

- Java 21 Maven/JUnit/application-context execution.
- MySQL 8.4 issuer transaction, Flyway and multi-login concurrency execution.
- Redis Outbox delivery and Gateway acceptance of the issued access context.
- KMS/HSM outage/rotation and HMAC-key overlap tests.
- Login HTTP `Set-Cookie` attribute, CSRF/origin and buffer-destruction tests.
- Refresh rotation/reuse and revoke-to-DENY convergence tests.

## Remaining blockers

- Production KMS/HSM signing and refresh HMAC-key providers.
- Identity/credential JDBC adapters and login REST/Cookie response.
- Durable successful-authentication audit without a post-commit sink gap.
- Refresh rotation/reuse family revocation and logout/disable/expiry commands.
- Authorization repositories/endpoints and complete golden path.

## Release statement

Version 1.10 closes the database transaction between a successful credential
check and creation of session/access/refresh/projection state. It does not yet
expose that transaction through a runnable login endpoint and is therefore a
stronger implementation seed, not a production-ready IAM backend.
