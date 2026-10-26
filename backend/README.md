# Backend Build Foundation + Phase-01 Security Core

Java 21 + Spring Boot 3.5 + Spring Cloud 2025.0 + Spring Cloud Alibaba
2025.0 microservice Reactor.

## Runtime services

| Module | Application | Default port |
|---|---|---:|
| `iam-gateway` | `GatewayApplication` | 8080 |
| `iam-auth-service` | `AuthApplication` | 8081 |
| `iam-identity-service` | `IdentityApplication` | 8082 |
| `iam-organization-service` | `OrganizationApplication` | 8083 |
| `iam-authorization-service` | `AuthorizationApplication` | 8084 |
| `iam-sharing-service` | `SharingApplication` | 8085 |
| `iam-file-service` | `FileApplication` | 8086 |
| `iam-audit-service` | `AuditApplication` | 8087 |
| `iam-job-service` | `JobApplication` | 8088 |

Every service exposes only Actuator `health` and `info` by default, supports
graceful shutdown and accepts its port from `SERVER_PORT`. No database,
registry or broker is auto-connected in this foundation stage.

## Verification

Prerequisites: JDK 21+ and Maven 3.9+.

```bash
python ../tools/validate_build_foundation.py --require-jdk21
python ../tools/validate_phase01_core.py
python ../tools/validate_session_projection_outbox.py
python ../tools/validate_session_issuance.py
mvn -B -ntp verify
```

The structural validators check the complete module graph, parent resolution,
centrally managed dependency versions, frozen service packages, launchers,
smoke tests, ports, service-config secret defaults, authorization precedence,
delegation policy, Gateway header sanitization and canonical migration copies.

`mvn verify` also runs six Failsafe integration suites (27 tests) against
pinned MySQL 8.4.9 Testcontainers: three Flyway suites, JDBC idempotency,
transactional login/password change and refresh rotation. Docker is a required
test dependency; the integration suite is not silently skipped when Docker is
unavailable. Windows Docker 29/npipe hosts may require
`mvn verify -Dapi.version=1.44` for client API negotiation.

## Boundary

Framework modules contain only reusable technical capabilities. Business
aggregates, repositories and policies remain owned by their service bounded
context. This package implements the Phase-01 security core, token/JWKS/session
trust adapters, session-projection outbox, transactional login/password change
and refresh flows, authorization repositories/endpoints, and DDD persistence
adapters backed by MyBatis-Plus with XML SQL. Production KMS/HSM signing,
refresh-key provisioning and deployed Gateway/broker/cache end-to-end evidence
remain open.
