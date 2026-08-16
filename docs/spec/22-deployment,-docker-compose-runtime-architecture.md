# SPEC 22 — Deployment, Docker Compose & Runtime Architecture

## V1.0 Frozen Baseline

Windows + Docker Desktop 友好；基础设施 MySQL/Redis/RabbitMQ/Nacos/PowerJob/MinIO，Seata 可选。
本地 IDEA 启 Java，React Vite；同时交付 full compose。
生产 Gateway/Authz 多实例、数据层 HA、Health Check、Graceful Shutdown、Backup/Restore。
---

## Final Deployment Addendum — File Service

V1.x adds:

```text
iam-file-service
iam_file database
```

Suggested development port:

```text
iam-file-service: 8108
```

External clients still access it only through Gateway. MinIO remains private; browsers may receive narrowly scoped, short-lived presigned URLs only after IAM authorization.

MySQL development database set is now:

```text
iam_auth
iam_identity
iam_organization
iam_authorization
iam_sharing
iam_file
iam_audit
iam_job
```
