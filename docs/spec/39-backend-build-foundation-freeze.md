# 39 — Backend Build Foundation Freeze — SPEC 1.3

## 1. Decision

SPEC 39 freezes the first implementation-grade backend engineering baseline.
It is authoritative for Maven structure, Java/tool versions, service launchers,
build validation and CI. SPEC 38 remains authoritative for authorization
behavior and machine contracts.

This baseline is deliberately thin: it proves module and startup structure
before business implementation. It does not claim databases, Nacos, Redis,
RabbitMQ, Flyway execution or the end-to-end authorization path are running.

## 2. Frozen toolchain and release train

| Concern | Frozen version | Reason |
|---|---:|---|
| Java release | 21 | Existing architecture and backlog authority |
| Maven | 3.9+ | Supported baseline for current Maven plugins |
| Spring Boot | 3.5.0 | Compatible with Spring Cloud Alibaba 2025.0.x |
| Spring Cloud | 2025.0.0 | Release train matched to Boot 3.5.x |
| Spring Cloud Alibaba | 2025.0.0.0 | Official Boot 3.5.0-compatible line |
| MyBatis-Plus BOM | 3.5.16 | Centralized future persistence dependency |
| Redisson | 4.6.1 | Centralized future lock/cache dependency |
| ArchUnit | 1.4.2 | Architecture-test baseline |
| Compiler plugin | 3.15.0 | Uses `maven.compiler.release=21` |
| Surefire plugin | 3.5.4 | Stable non-milestone test runner |
| Enforcer plugin | 3.6.3 | Rejects Java <21 and Maven <3.9 |

The parent POM and the publishable `iam-dependencies` BOM own versions.
Runtime and framework child modules must not declare external versions.

Compatibility sources:

- https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/
- https://spring.io/projects/spring-cloud
- https://maven.apache.org/download.cgi
- https://maven.apache.org/plugins/maven-compiler-plugin/

## 3. Reactor closure

The backend Reactor contains 31 artifacts:

- one root aggregator/parent;
- one publishable dependency BOM;
- one framework aggregator and 18 framework children;
- nine runtime services;
- one test-support artifact.

Every declared module owns a valid `pom.xml`. Every child resolves its parent
through an explicit relative path. Internal dependencies use
`${project.version}` and every referenced internal artifact is part of the
same Reactor.

## 4. Runtime skeleton contract

The nine package names and application classes are:

| Module | Package | Launcher | Port |
|---|---|---|---:|
| iam-gateway | `com.enterprise.iam.gateway` | `GatewayApplication` | 8080 |
| iam-auth-service | `com.enterprise.iam.auth` | `AuthApplication` | 8081 |
| iam-identity-service | `com.enterprise.iam.identity` | `IdentityApplication` | 8082 |
| iam-organization-service | `com.enterprise.iam.organization` | `OrganizationApplication` | 8083 |
| iam-authorization-service | `com.enterprise.iam.authorization` | `AuthorizationApplication` | 8084 |
| iam-sharing-service | `com.enterprise.iam.sharing` | `SharingApplication` | 8085 |
| iam-file-service | `com.enterprise.iam.file` | `FileApplication` | 8086 |
| iam-audit-service | `com.enterprise.iam.audit` | `AuditApplication` | 8087 |
| iam-job-service | `com.enterprise.iam.job` | `JobApplication` | 8088 |

Each service has a Spring context smoke test, an ArchUnit layering test and an
environment-overridable port. Only Actuator health/info is exposed by default.
No default credential is present and infrastructure auto-connection is
deferred to its owned story.

## 5. First executable core contract

`iam-common-core` contains the technical `TraceId` value object and executable
JUnit tests for trimming, blank rejection and maximum length. It has no Spring
or persistence dependency in main scope and is not a home for business-domain
entities.

## 6. Deterministic validation

`tools/validate_build_foundation.py` fails on:

- missing, extra, malformed or duplicate Reactor POMs;
- broken parent paths or inconsistent parent versions;
- unresolved internal dependencies;
- external dependency versions scattered into child modules;
- missing/mispackaged service launchers or smoke tests;
- changed or duplicate default ports;
- production-like secret literals in service application YAML;
- a strict CI host below JDK 21.

`.github/workflows/backend-build.yml` provisions JDK 21, runs the strict
validator, executes `mvn -B -ntp verify` from `backend/`, and publishes Surefire
reports even when the build fails.

`.github/workflows/contract-quality.yml` runs the internal consistency validator
plus pinned Redocly CLI 2.46.1 and AsyncAPI CLI 6.0.2 checks; a configured
workflow is not itself pass evidence until it has a successful run URL.

`iam-test-support` implements the S05 rules: domain code may not depend on
infrastructure, Spring or MyBatis; REST/controller and application packages may
not depend directly on persistence mappers. Every runtime service executes the
same baseline against its frozen root package.

## 7. Evidence classification

At SPEC 1.3 packaging time:

- structural Reactor validation: PASS;
- service launcher/config/test closure: PASS;
- contract structural validation: PASS;
- local Redocly 2.46.1 / AsyncAPI CLI 6.0.2 validation: PASS;
- local `mvn verify`: NOT RUN — packaging host has no Maven;
- Java 21 compile/test: NOT RUN — packaging host contains only Java 17 JRE;
- container/Flyway/infrastructure tests: NOT RUN — no container runtime.

Therefore only the first Gate B checkbox may be closed locally. Maven, CI,
Flyway, container, security-scan and golden authorization evidence remain open.

## 8. Next implementation slice

The next slice must run the CI workflow and attach an immutable build URL, then
implement one vertical CODE PHASE 01 path in this order:

1. service-owned Flyway migration execution on clean MySQL;
2. identity/auth minimal persistence and login;
3. gateway trusted-context stripping and signing;
4. authorization DENY-by-default decision path;
5. grant/revoke and audit golden tests against real MySQL and Redis.

No later document may mark Gate B complete without the corresponding CI and
runtime evidence.
