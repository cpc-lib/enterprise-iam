# CODE-READY 1.10.1 Contract Hardening Changelog

## Fixed

- Removed `writeOnly` from access-token response fields and made `tokenType`
  required in login and refresh responses.
- Unified OpenAPI and AsyncAPI identifiers on positive signed-64-bit decimal
  semantics with an explicit maximum of `9223372036854775807`.
- Froze the complete `IAM_REFRESH` `Set-Cookie` shape for login and refresh.
- Mapped the concurrent-session limit to HTTP `409` and
  `IAM_AUTH_SESSION_LIMIT_REACHED`, separate from authentication rate limiting.
- Renamed the traceability `status` column to `contract_status` so READY cannot
  be mistaken for implementation or release evidence.
- Raised the single-factor password provisioning minimum from 12 to 15.
- Fixed Gateway test compilation by removing generic-attribute assertion
  ambiguity and adapting the JWKS loader fixture to the transport interface.
- Fixed the Gateway application-context smoke test so it exercises a reactive
  mock web context instead of disabling the web application type.
- Replaced the deprecated Actuator endpoint-enable test property in all nine
  runtime-service context tests.

## Added

- Explicit login retry semantics: generic response replay is excluded because
  refresh plaintext is non-recoverable; `requestId` is correlation-only.
- A simultaneous MySQL issuance integration-test source proving a locked
  one-session limit permits exactly one commit.
- Semantic regression checks for response direction, ID range, cookie shape,
  login retry metadata, session-limit mapping and concurrent test evidence.
- A SHA-256 inventory for normative contracts and hardening artifacts.
- Maven Reactor version `1.10.1-SNAPSHOT` across all 31 POMs.
- Explicit Mockito Java-agent resolution for Java 21 Surefire and Failsafe runs.
- A Failsafe compiled-classes override compatible with Spring Boot executable
  JAR packaging.
- Dual-mode MySQL integration-test provisioning: pinned Testcontainers by
  default or an externally supplied JDBC URL for restricted CI runners.
- A centralized, service-isolated MySQL integration fixture shared by auth,
  identity and authorization, with service-specific URLs, a `{database}` URL
  template, credential precedence checks and five resolver unit tests.
- Local Java 21 evidence: the complete 31-module Reactor compiled and all 160
  Surefire tests passed with zero failures, errors or skips.
- Local packaging evidence: `mvn -DskipITs verify` completed all 31 modules and
  produced nine executable Spring Boot JARs with the expected start classes.

## Still open

- A current Maven/CI rerun of the centralized fixture delta and successful
  MySQL execution of all eight integration tests across auth, identity and
  authorization; Redis, Docker Compose, full-Reactor `verify` and immutable CI
  evidence.
- Production KMS/HSM and refresh-HMAC key providers.
- Login HTTP/Cookie delivery, durable success audit and orphan-session
  reconciliation.
- Refresh rotation/reuse, logout, disable, expiry and end-to-end Gateway proof.
