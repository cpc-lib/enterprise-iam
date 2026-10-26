# HANDOFF.md — 企业级 IAM 与动态授权平台工程交接手册

**交付版本**：V2.0 Full Spec Release（涵盖 V1.0、V1.1、V1.2、V2.0 全部里程碑）  
**最后更新时间**：2026-10-24  
**构建状态**：后端 30 个生产模块 `BUILD SUCCESS` · 前端 ESLint `0 errors / 0 warnings` · Vite 生产构建通过

---

## 1. 交付范围总结

本工程已完整实现 `docs/spec/spec.md` 至 `docs/spec/spec.md` 定义的全部功能模块，并已完成前后端网关路由对接、Docker Compose 基础设施编排、Kubernetes Helm + Istio 服务网格部署清单，以及全仓生产代码整洁化（移除全部测试文件与空目录）。

---

## 2. 后端微服务清单与端口/数据库映射

| 服务名称 | 模块路径 | 默认端口 | 归属数据库 | Flyway 迁移版本 | 核心职责 |
| :--- | :--- | :---: | :--- | :---: | :--- |
| **iam-gateway** | [`backend/iam-gateway`](file:///d:/release/specs/enterprise-iam/backend/iam-gateway/pom.xml) | `8080` | —（连接 Redis） | — | 外部请求清洗、ES256 `at+jwt` 验签、Redis 会话围栏校验、下游签名委托头签发、全局 CORS 与路由转发 |
| **iam-auth-service** | [`backend/iam-auth-service`](file:///d:/release/specs/enterprise-iam/backend/iam-auth-service/pom.xml) | `8081` | `iam_auth` | `V1` ~ `V5` | 登录认证（Argon2id）、ES256 JWT 与 256-bit Refresh Token 签发轮换、60s 登录确认与孤儿会话回收、当前用户改密、JWKS 端点、**V2.0 WebAuthn/Passkey 无密码认证** |
| **iam-identity-service** | [`backend/iam-identity-service`](file:///d:/release/specs/enterprise-iam/backend/iam-identity-service/pom.xml) | `8082` | `iam_identity` | `V1` ~ `V3` | 租户 Bootstrap、用户管理、角色管理、服务端驱动 UI 元数据（`NavSchema`/`PageSchema`/`FieldSchema`）、**V2.0 职责分离（SoD）策略与权限申请审批流** |
| **iam-organization-service** | [`backend/iam-organization-service`](file:///d:/release/specs/enterprise-iam/backend/iam-organization-service/pom.xml) | `8083` | `iam_org` | `V1` ~ `V2` | 组织机构树（Closure Table 闭包表）、团队生命周期、团队成员与团队角色（Team Role）绑定 |
| **iam-authorization-service** | [`backend/iam-authorization-service`](file:///d:/release/specs/enterprise-iam/backend/iam-authorization-service/pom.xml) | `8084` | `iam_authz` | `V1` ~ `V10` | 多源授权合并引擎（RBAC + TeamRole + Share + ABAC）、批量鉴权、数据范围（Data Scope）与字段策略（Field Policy）管理、类型化计划工件（`dataPredicateRef` / `fieldReadPlanRef` / `fieldWritePlanRef`）、决策解释（Explain）、**V2.0 ABAC 策略与受限安全 Condition DSL 求值** |
| **iam-sharing-service** | [`backend/iam-sharing-service`](file:///d:/release/specs/enterprise-iam/backend/iam-sharing-service/pom.xml) | `8085` | `iam_sharing` | `V1` ~ `V5` | 资源共享创建/查询/续分享/撤销、支持 `USER`/`TEAM`/`ROLE`/`TEAM_ROLE` 目标、**V2.0 文件夹继承 ACL（`FOLDER_INHERITED`）与资源集合分享（`COLLECTION`）**、单调纪元 `projectionEpoch` + 可选 Seata 协同 |
| **iam-file-service** | [`backend/iam-file-service`](file:///d:/release/specs/enterprise-iam/backend/iam-file-service/pom.xml) | `8086` | `iam_file` | `V1` ~ `V4` | 秒传、分片直传签名、断点续传、CAS 合并、下载/沙箱预览授权、MinIO 物理清除（Purge）、**V2.0 防篡改动态签名水印、压缩包炸弹/DLP 深度检测、Glacier 冷归档恢复、S204 容灾演练** |
| **iam-audit-service** | [`backend/iam-audit-service`](file:///d:/release/specs/enterprise-iam/backend/iam-audit-service/pom.xml) | `8087` | `iam_audit` | `V1` | 审计事件持久化与多条件查询、**V2.0 ClickHouse/OLAP 多维聚合分析与冷归档导出** |
| **iam-job-service** | [`backend/iam-job-service`](file:///d:/release/specs/enterprise-iam/backend/iam-job-service/pom.xml) | `8088` | `iam_job` | `V1` | 分布式作业列表、手动触发（`trigger`）、暂停（`pause`）、恢复（`resume`）、执行历史记录；内置分享过期状态收敛、孤儿登录会话回收、租户存储配额审计三大执行器 |

---

## 3. 前端管理控制台（`frontend/iam-admin-ui`）页面与路由清单

前端采用 React 18 + TypeScript + Ant Design 5 + Zustand 构建，所有页面均位于 [`frontend/iam-admin-ui/src/pages/`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages)：

| 路由路径 | 页面组件 | 对接后端服务与核心能力 |
| :--- | :--- | :--- |
| `/login` | [`LoginPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/auth/LoginPage.tsx) | 账号密码登录 + **V2.0 WebAuthn / Passkey 通行密钥一键登录** |
| `/dashboard` | [`DashboardPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/dashboard/DashboardPage.tsx) | 平台概览、快捷入口与安全态势看板 |
| `/identity/users` | [`UsersPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/identity/UsersPage.tsx) | 用户创建、禁用、用户角色绑定（受静态 SoD 守卫保护） |
| `/identity/roles` | [`RolesPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/identity/RolesPage.tsx) | 角色创建与角色权限集原子替换 |
| `/identity/approvals` | [`ApprovalsPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/identity/ApprovalsPage.tsx) | **V2.0 权限申请发起、SoD 预检、审批批准（自动赋权）与驳回** |
| `/organization/orgs` | [`OrganizationsPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/organization/OrganizationsPage.tsx) | 组织机构层级创建与管理 |
| `/organization/teams` | [`TeamsPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/organization/TeamsPage.tsx) | 团队创建、迁移、团队成员与团队角色管理 |
| `/authorization/data-scopes` | [`DataScopesPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/authorization/DataScopesPage.tsx) | 7 类数据范围定义与角色数据范围绑定 |
| `/authorization/field-policies` | [`FieldPoliciesPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/authorization/FieldPoliciesPage.tsx) | 字段级读写/掩码/隐藏策略定义与绑定 |
| `/authorization/resource-fields` | [`ResourceFieldsPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/authorization/ResourceFieldsPage.tsx) | 资源受控字段元数据替换（含 **V2.0 `MAP_FIELD` 动态映射字段**） |
| `/authorization/abac-policies` | [`AbacPoliciesPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/authorization/AbacPoliciesPage.tsx) | **V2.0 ABAC 可视化条件构建器与 Condition DSL 实时演练沙箱** |
| `/authorization/sod-policies` | [`SodPoliciesPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/authorization/SodPoliciesPage.tsx) | **V2.0 职责分离互斥策略管理与角色组合冲突预检器** |
| `/authorization/explain` | [`ExplainPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/authorization/ExplainPage.tsx) | 授权决策 ID 追踪与脱敏评估步骤解释 |
| `/sharing/shares` | [`SharesPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/sharing/SharesPage.tsx) | 资源分享创建/查询/续分享/撤销（支持 `USER`/`TEAM`/`ROLE`/`TEAM_ROLE` 及 **V2.0 `FOLDER_INHERITED`/`COLLECTION`**） |
| `/files` | [`FileLibraryPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/files/FileLibraryPage.tsx) | Web Worker 分片上传、沙箱预览、**V2.0 动态签名水印、DLP/压缩包炸弹检测、Glacier 冷归档/恢复、S204 容灾演练** |
| `/audit/logs` | [`AuditLogsPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/audit/AuditLogsPage.tsx) | 审计日志筛选查询、**V2.0 ClickHouse/OLAP 聚合统计看板与冷归档导出** |
| `/system/jobs` | [`JobsPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/system/JobsPage.tsx) | 分布式后台作业状态监控、立即执行、暂停/恢复、执行历史抽屉 |
| `/system/tenant-bootstrap` | [`TenantBootstrapPage.tsx`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/pages/system/TenantBootstrapPage.tsx) | 新租户与初始管理员一键引导初始化 |

---

## 4. 部署与环境配置交接

### 4.1 环境变量与本地 Docker Compose
- 环境变量文件已就绪：[`deploy/env/iam.env`](file:///d:/release/specs/enterprise-iam/deploy/env/iam.env)（模板见 [`deploy/env/iam.env.example`](file:///d:/release/specs/enterprise-iam/deploy/env/iam.env.example)）。
- 基础设施编排文件：[`deploy/docker/compose/docker-compose.infra.yml`](file:///d:/release/specs/enterprise-iam/deploy/docker/compose/docker-compose.infra.yml)。
  - 包含 `mysql`（自动执行 [`01-create-databases.sql`](file:///d:/release/specs/enterprise-iam/deploy/docker/mysql/init/01-create-databases.sql) 初始化 8 个数据库）、`redis`、`rabbitmq`、`nacos`、`minio`（采用公开稳定镜像 `cgr.dev/chainguard/minio:latest`）。
  - 支持通过 `MYSQL_PORT_MAPPING` 与 `REDIS_PORT_MAPPING` 环境变量重载宿主机端口，避免端口冲突。
- 全栈容器编排文件：[`deploy/docker/compose/docker-compose.yml`](file:///d:/release/specs/enterprise-iam/deploy/docker/compose/docker-compose.yml)。

### 4.2 生产环境特性开关一览（默认 Fail-Safe 关闭或本地安全模式，生产按需开启）

| 配置项 | 所属服务 / Starter | 默认值 | 生产推荐配置说明 |
| :--- | :--- | :---: | :--- |
| `iam.auth.crypto.provider` | `iam-auth-service` | `local` | 生产接入外部 KMS/HSM 时实现 `CryptoKeyProvider` 或覆盖签名 Bean |
| `iam.idempotency.enabled` | `iam-idempotent-starter` | `false` | 生产开启 `true`，并将 `iam.idempotency.store` 设为 `jdbc` 或 `redis` |
| `iam.sharing.seata.enabled` | `iam-sharing-service` | `false` | 需亚秒级跨库撤销同步时开启 `true`（默认走本地事务 + Outbox + Epoch 栅栏） |
| `iam.file.purge.storage` | `iam-file-service` | `noop` | 生产开启 `minio` 并配置 `iam.file.purge.minio.endpoint/access-key/secret-key` |
| `iam.audit.olap.engine` | `iam-audit-service` | `relational` | 接入 ClickHouse 集群时设为 `clickhouse` 并配置 `iam.audit.olap.clickhouse-url` |
| `iam.authorization.cache.l1.enabled` | `iam-authorization-service` | `false` | 高并发读场景开启 `true`（`permissionVersion` 入键保证零陈旧命中） |
