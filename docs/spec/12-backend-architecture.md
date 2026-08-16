# SPEC 12 — Backend Architecture

## V1.0 Frozen Baseline

Java 21，DDD/Clean-ish：interfaces → application → domain；infrastructure 实现 domain ports。
公共 framework 禁止包含业务实体。
Gateway 粗授权，下游 Starter 强制 Instance/Data/Field 检查。
---

## Final Module Freeze Addendum

Backend services:

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

Final framework modules additionally include:

```text
iam-common-transaction
iam-authorization-client-spring-boot-starter
iam-api-discovery-spring-boot-starter
```

Final Java root packages:

```text
com.enterprise.iam.gateway
com.enterprise.iam.auth
com.enterprise.iam.identity
com.enterprise.iam.organization
com.enterprise.iam.authorization
com.enterprise.iam.sharing
com.enterprise.iam.file
com.enterprise.iam.audit
com.enterprise.iam.job
```

The older underscore package placeholders are obsolete.
