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

`mvn verify` also runs three Failsafe integration suites against pinned MySQL
8.4.9 Testcontainers. Docker is a required test dependency; the integration
suite is not silently skipped when Docker is unavailable.

## Boundary

Framework modules contain only reusable technical capabilities. Business
aggregates, repositories and policies remain owned by their service bounded
context. This package implements a Phase-01 security core, token/JWKS/session
trust adapters, a reusable session-projection outbox and a concrete opt-in JDBC
login-session issuer. Production KMS/HSM signing, refresh-key provisioning,
login HTTP/Cookie delivery, refresh/revoke/disable/expiry commands,
authorization repositories/endpoints and the golden authorization path remain
open.
