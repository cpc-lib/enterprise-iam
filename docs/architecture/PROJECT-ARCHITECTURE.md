# Enterprise IAM — Project Directory & Runtime Architecture

## 1. Monorepo
```text
enterprise-iam/
├── backend/
│   ├── iam-dependencies/
│   ├── iam-framework/
│   │   ├── iam-common-core/
│   │   ├── iam-common-web/
│   │   ├── iam-common-tenant/
│   │   ├── iam-common-security/
│   │   ├── iam-common-mybatis/
│   │   ├── iam-common-redis/
│   │   ├── iam-common-mq/
│   │   ├── iam-common-lock/
│   │   ├── iam-common-observability/
│   │   ├── iam-authorization-client-spring-boot-starter/
│   │   ├── iam-security-spring-boot-starter/
│   │   ├── iam-data-permission-spring-boot-starter/
│   │   ├── iam-field-permission-spring-boot-starter/
│   │   ├── iam-idempotent-spring-boot-starter/
│   │   ├── iam-outbox-spring-boot-starter/
│   │   └── iam-audit-spring-boot-starter/
│   ├── iam-gateway/
│   ├── iam-auth-service/
│   ├── iam-identity-service/
│   ├── iam-organization-service/
│   ├── iam-authorization-service/
│   ├── iam-sharing-service/
│   ├── iam-audit-service/
│   ├── iam-job-service/
│   └── iam-test-support/
│
├── frontend/
│   └── iam-admin-ui/
│       └── src/
│           ├── app/
│           ├── api/
│           ├── stores/
│           ├── layouts/
│           ├── pages/
│           ├── features/
│           ├── components/
│           ├── hooks/
│           ├── utils/
│           ├── types/
│           └── tests/
│
├── docs/
│   ├── spec/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── database/
│   ├── testing/
│   ├── deployment/
│   └── diagrams/
│
├── deploy/
│   ├── docker/
│   ├── env/
│   └── scripts/
│
├── tests/
│   ├── e2e/
│   ├── security/
│   ├── performance/
│   └── chaos/
├── scripts/
├── tools/
└── .github/
```

## 2. Runtime
```text
React Admin
    |
    v
IAM Gateway
    |
    +--> Auth Service
    |
    +--> Authorization Service (PDP)
              ^
              |
      Read Models / Projection
       ^       ^        ^
       |       |        |
   Identity   Org    Sharing
       \       |       /
        \------RabbitMQ
                |
             Audit
                |
             Job/PowerJob
```

## 3. Security enforcement
```text
Gateway PEP
  +
Business Service PEP
  +
MyBatis Data Permission
  +
Field Write/Response Filter
```

## Final Consistency Freeze

Authoritative backend service set:

```text
iam-gateway
iam-auth-service
iam-identity-service
iam-organization-service
iam-authorization-service
iam-sharing-service
iam-file-service
iam-audit-service
iam-job-service
```

Authoritative Java packages use semantic package names (`com.enterprise.iam.auth`, `...identity`, `...authorization`, etc.), not service-directory names with underscores.

Authoritative dynamic authorization client module is `iam-authorization-client-spring-boot-starter`.
