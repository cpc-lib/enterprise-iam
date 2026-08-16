# SPEC 11 — Database Design

## V1.0 Frozen Baseline

MySQL 8、InnoDB、utf8mb4、DATETIME(3)、无跨服务物理 FK。
数据库按 Auth/Identity/Organization/Authorization/Sharing/Audit/Job 拆分。
Outbox/Idempotency/Consume Record 均为各服务本地基础设施表。
