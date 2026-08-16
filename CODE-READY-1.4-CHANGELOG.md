# CODE-READY 1.4 Changelog

## Added

- Pure domain `DefaultAuthorizationEngine` with fail-closed precedence and
  DENY-over-ALLOW semantics.
- Immutable request/fact/grant/result models and focused JUnit evidence.
- ES256 delegation-token claim policy with issuer/audience/type/time/context
  validation.
- Highest-priority Gateway spoofable identity-header sanitization filter.
- Required datasource/Flyway configuration for Identity, Auth and Authorization.
- Three MySQL Testcontainers `FlywayMigrationIT` suites and Failsafe execution.
- CODE PHASE 01 static validator and implementation-evidence catalog.
- SPEC 40 implementation freeze.

## Changed

- MySQL runtime validation baseline moved from EOL 8.0 to 8.4 LTS; CI image is
  pinned to `mysql:8.4.9`.
- Security parameters now define delegation JWT invariants.
- Backend CI publishes Surefire and Failsafe reports.

## Not claimed

- Login, access-token verification, delegation signing/JWKS decoding,
  authorization repositories/controller, grant/revoke or the golden end-to-end
  path are not implemented.
- Maven, Java 21, MySQL container and runtime evidence remain unexecuted on the
  packaging host.
