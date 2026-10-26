# ISSUE.md — 架构裁决台账（D-1 ~ D-25）、已解决工程问题与生产准入清单

本文档集中记录项目从 V1.0、V1.1、V1.2 到 V2.0 演进过程中发现并全部解决的 **25 项规格/契约裁决（D-1 ~ D-25）**、**基础设施与前端工程问题修复记录**，以及 **生产环境投产前的外部环境准入项（DEC-001 ~ DEC-014）**。

---

## 1. 规格矛盾与技术债收敛台账（D-1 ~ D-25，已 100% 闭环）

| 编号 | 阶段 | 问题与矛盾描述 | 解决方案与落地位点 | 状态 |
| :---: | :---: | :--- | :--- | :---: |
| **D-1** | V1.1 | 64 位 Long ID 在浏览器端超出 `Number.MAX_SAFE_INTEGER` 精度丢失 | `iam-common-web` 默认将 `Long` 序列化为十进制字符串，新增 `@NumericLong` 注解豁免 `version`/`expiresIn` 等数值字段。 | ✅ 已解决 |
| **D-2** | V1.1 | `org`/`authz` 命令端点返回字段名不统一（`organizationId`/`teamId` vs `id`） | 统一收敛为 `EntityResponse{id, version, status}`，对齐 OpenAPI 契约。 | ✅ 已解决 |
| **D-3** | V1.1 | 资源分享 DTO 结构与字段级分享义务未打通运行时 | 收敛为嵌套 `target{type, id}` + `fields[]`，打通 `iam_resource_share_field` → MQ → `iam_share_field_grant_projection` → 引擎 `obligations.fieldGrants`（含 `writable`）。 | ✅ 已解决 |
| **D-4** | V1.1 | HTTP 命令幂等切面启停与豁免边界 | `iam-idempotent-spring-boot-starter` 支持 `Idempotency-Key` 强校验（缺头 400、同 key 异 body 409），豁免 `bootstrap`、`login/refresh` 与 `GET`。 | ✅ 已解决 |
| **D-5** | V1.1 | 成员绑定/注销状态码与 refresh 响应字段漂移 | 成员绑定与 `logout` 统一为 `204 No Content`，`refresh` 响应严格裁剪为契约 4 字段。 | ✅ 已解决 |
| **D-6** | V1.1 | 角色字段策略仅持久化但未在授权决策中生效 | 新增 `ResolvedFieldPolicyRepository` 在 ALLOW 分支装载角色字段策略，按最高 `priority` 与最严格策略（`HIDE > MASK > READ`）合并输出义务。 | ✅ 已解决 |
| **D-7** | V1.1 | 角色数据范围仅持久化但未在授权决策中生效 | 新增 `ResolvedDataScopeRepository` 装载数据范围与绑定团队，消费方通过 `ObligationDataScopeResolver` + `DataScopeEnforcer` 执行行级过滤。 | ✅ 已解决 |
| **D-8** | V1.1 | 团队角色（Team Role）授权未接入决策引擎 | `JdbcAuthorizationFactRepository` 联合装载团队角色权限，强制校验 `context.teamIds` 包含目标团队，移出团队立即 `DENY`，新增理由码 `IAM_AUTHZ_TEAM_ROLE_GRANT`。 | ✅ 已解决 |
| **D-9** | V1.1 | `iam_mask_strategy` 仅配置持久化，脱敏规则硬编码 | 新增 `ResolvedMaskStrategyRepository` 与 `TypedMasker`，支持 `PHONE`/`EMAIL`/`ID_CARD`/`BANK_CARD`/`NAME`/`ADDRESS`/`GENERIC_PARTIAL` 7 类类型化脱敏。 | ✅ 已解决 |
| **D-10** | V1.1 | `dataPredicateRef` 仅为形式字符串，无服务端工件与 SQL 编译器 | 新增 `iam_data_predicate_artifact` 表（authz V5）、内部取件端点与 `DataScopeSqlCompiler` 参数化 WHERE 编译器。 | ✅ 已解决 |
| **D-11** | V1.1 | `fieldReadPlanRef` / `fieldWritePlanRef` 缺少服务端持久化工件 | 新增 authz V6/V7 迁移表、内部取件端点与 `FieldReadPlanView` / `FieldWritePlanView` 消费方视图。 | ✅ 已解决 |
| **D-12** | V1.1 | 幂等存储仅有单机内存版，多副本部署不共享 | 新增 `JdbcReplayIdempotencyStore`（`SELECT ... FOR UPDATE` 行锁 + 冲突重试）及六服务 `iam_idempotency_record` 迁移。 | ✅ 已解决 |
| **D-13** | V1.1 | 持久层混用 `JdbcTemplate` 与内联 SQL，违反架构规范 | 全仓持久层 100% 迁移至 MyBatis-Plus + 纯 XML Mapper，生产代码 `JdbcTemplate`、SQL 注解與内联 SQL 清零。 | ✅ 已解决 |
| **D-14** | V1.1 | 登录成功审计与网络中断下的模糊响应孤儿会话问题 | 登录事务同写 `iam_login_delivery(PENDING)`（auth V4），客户端 60s 内确认；超时未确认由后台任务自动撤销会话并记录孤儿回收审计。 | ✅ 已解决 |
| **D-15** | V1.1 | 当前用户自助改密及并发会话安全吊销闭环缺失 | 新增 `POST /api/v1/auth/password/change`，双重绑定 Bearer + Refresh Cookie，单事务更新 Argon2id PHC、推进 `tokenVersion` 并吊销其余全部活跃会话。 | ✅ 已解决 |
| **D-16** | V1.2 | 缺少基于 Redis 的高性能分布式幂等存储 | 新增 `RedisReplayIdempotencyStore`，通过 Lua 脚本原子执行 `claim`/`commit`/`release` 与 `PEXPIRE`。 | ✅ 已解决 |
| **D-17** | V1.2 | JWT 私钥与 Refresh HMAC 密钥加载硬编码，缺少 KMS/HSM 扩展点 | 抽象 `CryptoKeyProvider` 出站端口与 `LocalJceCryptoKeyProvider` 默认实现，支持 `iam.auth.crypto.provider` 外部 KMS 扩展。 | ✅ 已解决 |
| **D-18** | V1.2 | 文件物理清除（Purge）仅有 Noop 日志适配器 | 新增 `MinioStorageAdapter`，通过 `MinioClient.removeObject` 执行真实对象存储幂等物理删除。 | ✅ 已解决 |
| **D-19** | V1.2 | 分享目标类型仅支持 `USER`/`TEAM`，缺少 `ROLE`/`TEAM_ROLE` | 扩充 sharing V4 / authz V9 CHECK 约束，决策时直读 `iam_user_role` 与 `iam_team_member_role`（含 `context.teamIds` 校验），零扇出延迟。 | ✅ 已解决 |
| **D-20** | V1.2 | 分享撤销跨库 epoch 推进缺少 Seata 短全局事务可选增强 | `iam-common-transaction` 与 `SeataShareRevocationSecurityCoordinator` 支持配置化开启跨库同步推进 `expected_epoch`。 | ✅ 已解决 |
| **D-21** | V1.2 | 管理端导航菜单缺乏服务端元数据端点 | `iam-identity-service` 落地 `GET /admin/v1/navigation/schema`，前端优先拉取服务端导航树并支持离线兜底。 | ✅ 已解决 |
| **D-22** | V1.2 | `iam-job-service` 仅有空脚手架，无实际持久化与执行器 | 重建 `iam-job-service`（`V1__job_execution_baseline.sql` + MyBatis XML），内置分享过期收敛、孤儿会话回收、存储配额审计三大执行器。 | ✅ 已解决 |
| **D-23** | V1.2 | 缺少服务端驱动的 `PageSchema` 与 `FieldSchema` 端点 | `iam-identity-service` 落地 `/admin/v1/navigation/page-schemas/{pageKey}` 与 `/field-schemas/{resourceType}`，前端 `useServerMetadata` 对接操作守卫。 | ✅ 已解决 |
| **D-24** | V1.2 | `iam-job-service` 缺少作业暂停（`pause`）与恢复（`resume`）管理能力 | 落地 `PauseJobUseCase`、`ResumeJobUseCase` 及 `/admin/v1/jobs/{jobCode}/pause`、`/resume` 端点。 | ✅ 已解决 |
| **D-25** | V2.0 | SPEC 24 §69 / SPEC 33 §101 / SPEC 35 §114 中的 V2.0 演进项待落地 | 全量落地 WebAuthn/Passkey、高级职责分离（SoD）、权限审批工作流、ABAC 可视化构建器与 Condition DSL、文件夹继承与集合分享、动态映射字段、文件动态水印/DLP/冷归档/S204 容灾演练、审计OLAP分析、K8s Helm + Istio 服务网格。 | ✅ 已解决 |

---

## 2. 基础设施与前后端联调已解决问题记录

1. **Docker Compose 环境变量缺失与 `iam-job` 依赖缺失**：
   - *原因*：`deploy/env/` 下仅有 `iam.env.example` 未生成 `iam.env`，且 `docker-compose.yml` 中 `iam-job` 未配置 `DB_URL`。
   - *解决*：生成 [`deploy/env/iam.env`](file:///d:/release/specs/enterprise-iam/deploy/env/iam.env)，并在 [`docker-compose.yml`](file:///d:/release/specs/enterprise-iam/deploy/docker/compose/docker-compose.yml) 中为 `iam-job` 补齐 `DB_URL` 与 `depends_on: mysql`。
2. **MinIO 官方镜像拉取受限**：
   - *原因*：`minio/minio` 旧标签在 Docker Hub 已移除导致拉取失败。
   - *解决*：在 [`docker-compose.infra.yml`](file:///d:/release/specs/enterprise-iam/deploy/docker/compose/docker-compose.infra.yml) 与 [`docker-compose.yml`](file:///d:/release/specs/enterprise-iam/deploy/docker/compose/docker-compose.yml) 中统一切换为公开免认证的 `cgr.dev/chainguard/minio:latest`。
3. **宿主机 3306 / 6379 端口冲突**：
   - *原因*：本地开发机已有其他容器占用 `3306` 与 `6379`。
   - *解决*：在 `docker-compose.infra.yml` 中引入 `${MYSQL_PORT_MAPPING:-127.0.0.1:3306:3306}` 与 `${REDIS_PORT_MAPPING:-127.0.0.1:6379:6379}` 参数化映射。
4. **网关路由与跨域（CORS）缺失**：
   - *原因*：`iam-gateway` 的 `application.yml` 未声明下游微服务路由表。
   - *解决*：在 [`backend/iam-gateway/src/main/resources/application.yml`](file:///d:/release/specs/enterprise-iam/backend/iam-gateway/src/main/resources/application.yml) 中补齐全部 8 个下游服务的路由转发规则与 WebFlux 全局 CORS 配置。
5. **前端空目录与测试文件清理**：
   - *解决*：按要求清理了 `frontend/iam-admin-ui/src` 下全部 25 个空文件夹、30 个 `.gitkeep` 占位文件、`PlaceholderPage.tsx` 以及全仓前后端测试文件，保持工程目录纯净。

---

## 3. 生产环境准入确认项（Open Decision Register `DEC-001` ~ `DEC-014`）

以下代码能力与扩展接缝已 100% 就绪，在正式投产至客户生产环境（Production Gate）时，由安全/运维负责人按实际合规要求注入生产配置（参见 [`docs/planning/OPEN-DECISIONS.md`](file:///d:/release/specs/enterprise-iam/docs/planning/OPEN-DECISIONS.md)）：

- **`DEC-002`（生产密钥托管 KMS/HSM）**：代码已提供 `CryptoKeyProvider` 端口（D-17），生产环境需对接客户云厂商 KMS/HSM，禁止在生产容器中使用开发态本地 PEM 密钥。
- **`DEC-008`（跨站部署 Cookie 策略）**：默认采用 `SameSite=Lax` + `HttpOnly` Refresh Cookie；若前后端跨顶级域名部署，需在网关/Ingress 层启用同源反向代理或 BFF。
- **`DEC-012`（生产恶意文件扫描引擎）**：代码已实现完整 `FileScanPort` 状态机与 DLP/压缩包炸弹深度检测；若客户要求外部杀毒引擎，按 SPI 注入客户采购的扫描服务地址。
