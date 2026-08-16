# CODE PHASE 01 — Implementation Evidence 1.10.1

| Evidence ID | Implemented evidence | State | Remaining proof |
|---|---|---|---|
| `SEC-TEN-001` | Gateway strips spoofable headers, verifies strict external access JWT/session state, removes bearer downstream, and creates only route-audience-bound delegation | PARTIAL | Integrate production KMS and execute real Gateway → service path |
| `SEC-FAILCLOSED-001` | Authorization core denies when authoritative facts are unavailable | PARTIAL | Unknown API mapping and remote dependency failure through real Gateway/HTTP path |
| `PROP-AUTHZ-001` | Fixed-clock repeated evaluation preserves decision/reason/version/grant evidence | LOCAL UNIT PASS | Property/fuzz suite against repository facts |
| `PROP-AUTHZ-003` | Cross-tenant facts containing ALLOW still produce tenant-mismatch DENY | LOCAL UNIT PASS | MySQL two-tenant repository integration fixture |
| `DB-FLYWAY-001` | Three service suites assert first migrate, second no-op and validate through one service-isolated dual-mode fixture; the prior auth run was discovered by Failsafe | LOCAL PARTIAL | Current Maven discovery and successful MySQL 8.4 execution for all three suites plus immutable CI URL |
| `SEC-DELEGATION-001` | Real P-256 ES256 signing/verification, rotating JWKS cache, exact audience auto-configuration and generic downstream rejection | LOCAL UNIT PASS | HTTPS loader, KMS/HSM and deployed rotation/replay test |
| `SEC-JWKS-001` | JWKS size/count/type/use/algorithm/operation bounds, duplicate rejection, refresh cooldown and 1,024-entry negative-cache cap | LOCAL UNIT PASS | Live dual-key rotation test and immutable CI report |
| `SEC-JWKS-TRANSPORT-001` | Exact-host HTTPS-only URI policy, private/mixed DNS rejection, no proxy/redirect, TLS 1.2/1.3, timeout, metadata, 65,536-byte streaming and strict UTF-8 controls | LOCAL UNIT PASS | Live TLS, DNS/egress, redirect and rotation evidence |
| `SEC-ROUTE-001` | Each Gateway route must be explicitly protected with audience or public; unknown policy and signing failure return generic 503 | LOCAL UNIT PASS | Real route inventory test and deployment configuration evidence |
| `SEC-ACCESS-001` | Strict P-256 ES256 `at+jwt`, exact issuer/single Gateway audience, bounded JWKS, time and positive identity/version claims | LOCAL UNIT PASS | Live HTTPS rotation evidence and immutable CI report |
| `SEC-SESSION-001` | Gateway requires exact tenant/user/session/tokenVersion/sessionVersion, ACTIVE state and idle/absolute expiry before creating a principal | LOCAL UNIT PASS | Redis outage and revoke-convergence integration evidence |
| `IT-SESSION-PROJECTION-001` | Shared versioned Redis hash codec, strict reactive HMGET reader and atomic Lua publisher with monotonic versions, terminal-state fence and PEXPIREAT | LOCAL UNIT PASS | Redis Testcontainers concurrency plus transactional-outbox lag evidence |
| `IT-OUTBOX-001` | Active-write-transaction-only append, leased SKIP LOCKED claims, bounded retry/dead-letter, strict event metadata and idempotent Redis handler; login creation is transactionally wired | LOCAL UNIT PASS + IT DISCOVERED | Revoke/disable/expiry producers; MySQL/Redis multi-worker crash and convergence evidence |
| `IT-LOGIN-ISSUANCE-001` | Real JDBC issuer locks user security state, enforces the concurrent-session bound and atomically inserts session, refresh HMAC and projection Outbox before returning credentials; signer failure rolls all writes back; simultaneous issuers with limit one are wired to prove exactly one commit | JAVA 21 COMPILED + IT DISCOVERED | Successful MySQL 8.4 execution; KMS/HSM signer and HTTP/Cookie path |
| `SEC-AUTHN-001` | Equal unknown-identity/wrong-password public result; one real/dummy DB path and one real/dummy Argon2id path; buffers destroyed | LOCAL UNIT PASS | Tenant-scoped MyBatis adapter and rate/lock/session/HTTP integration test |
| `SEC-PASSWORD-001` | Argon2id PHC adapter freezes 19,456 KiB / 2 iterations / p=1 / 16-byte salt / 32-byte output and NFC | LOCAL UNIT PASS | Credential provisioning/rehash and breached-password integration |

The frozen local Java 21 baseline covers 160 Surefire tests across all 31
Reactor modules. Five shared-fixture resolver tests were added afterward; their
source compiled against API boundary stubs and the logic passed an independent
JDK 21 harness, but they still require Maven/CI execution.
`PARTIAL` and `IT DISCOVERED` are not runtime pass states. Only
immutable successful CI and runtime evidence may close the matching Gate B item.
