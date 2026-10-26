# AGENTS.md — AI Coding Agent 协作规范与架构红线

本文档为在 `enterprise-iam` 仓库中工作的 AI 编码助手提供权威架构约束、代码分层铁律、持久化规范、测试约定与构建验证指南。任何代码修改均不得违反以下规则。

---

## 1. 规格权威与决策准则

### 1.1 权威优先级

[`docs/spec/spec.md`](file:///d:/release/enterprise-iam/docs/spec/spec.md)（含 SPEC 01 ~ SPEC 47）具有最高优先级，裁决链为：

```
SPEC 47 > SPEC 46 > SPEC 45 > ... > SPEC 37 > SPEC 36 > SPEC 24~35 > SPEC 01~23
```

已闭环的 25 项架构裁决（D-1 ~ D-25）与生产准入开放决策（DEC-001 ~ DEC-014）见 [`ISSUE.md`](file:///d:/release/enterprise-iam/ISSUE.md)。在生产环境未接入外部 KMS/HSM 或定制阈值前，代码必须保持 **Fail-Closed 安全默认值**。

### 1.2 契约先行（Contract-First）

- 所有对外或内部 HTTP 端点的契约定义于 `docs/spec/spec.md` 的各 SPEC 章节；新增或变更端点必须先在 spec 中明确请求/响应结构、错误码与幂等语义，再落地实现。
- 网关路由表集中维护于 [`backend/iam-gateway/src/main/resources/application.yml`](file:///d:/release/enterprise-iam/backend/iam-gateway/src/main/resources/application.yml)，新增端点须同步登记路由谓词。
- 新增或变更错误码须同步登记至各服务 DTO 层的错误码常量与 spec 文档，不得散落硬编码。

---

## 2. 后端架构与代码分层红线（DDD + 六边形架构）

每个业务微服务（`iam-auth-service`、`iam-identity-service`、`iam-organization-service`、`iam-authorization-service`、`iam-sharing-service`、`iam-file-service`、`iam-audit-service`、`iam-job-service`）与网关均严格划分为四个顶层包：

```text
com.enterprise.iam.<service>
├── domain          # 领域层：聚合根、实体、值对象、枚举、纯内存领域服务（如 ConditionDslEvaluator）
├── application     # 应用层：用例服务 (*UseCase)、入站命令/查询对象、出站端口接口 (port.out.*)
├── infrastructure  # 基础设施层：MyBatis-Plus Mapper 接口、持久化行对象 (*Row)、仓储适配器 (Jdbc*Repository)、MQ/Redis/MinIO 适配器、Spring @Configuration
└── interfaces      # 接口层：REST Controller、请求/响应 DTO、参数校验
```

### 2.1 依赖方向铁律

- **`domain` 层**：必须保持纯 Java 实现，严禁导入 `application`、`infrastructure`、`interfaces`、Spring Web、MyBatis、MyBatis-Plus 或 `java.sql.*` / `javax.sql.*`。
- **`application` 层**：仅依赖 `domain` 与自身定义的出站端口（`port.out.*`），严禁直接依赖 `infrastructure` 实现类、MyBatis Mapper 或 Spring MVC 注解。
- **`interfaces` 层**：调用 `application` 层的 `*UseCase`，严禁跨越应用层直接调用 `infrastructure` 仓储或 Mapper。

分层约束由 `iam-test-support` 中的 [`LayeredArchitectureRules`](file:///d:/release/enterprise-iam/backend/iam-test-support/src/main/java/com/enterprise/iam/testsupport/architecture/LayeredArchitectureRules.java) 在各服务的 `LayeredArchitectureTest` 中强制校验，违规将导致构建失败。

### 2.2 命名约定

| 元素 | 命名模式 | 示例 |
| :--- | :--- | :--- |
| 应用用例 | `*UseCase` | `CreateUserUseCase`、`GetNavigationSchemaUseCase` |
| 入站命令 | `*Command` / `*Query` | `CreateUserCommand` |
| 出站端口 | `*Repository` / `*Port` | `UserRepository`、`PositiveIdGenerator` |
| 仓储适配器 | `Jdbc*Repository` | `JdbcUserRepository` |
| Mapper 接口 | `*Mapper` | `IdentityPersistenceMapper` |
| 持久化行对象 | `*Row` | `UserRow` |
| 控制器 | `*Controller` | `UserController` |
| 请求/响应 DTO | `*Request` / `*Response` / `*Dto` | `CreateUserRequest`、`UserResponse` |

---

## 3. 持久层铁律（MyBatis-Plus + 纯 XML Mapper）

根据 SPEC 47 §5 裁决：

1. **禁止内联 SQL 与注解 SQL**：生产代码中严禁使用 Spring `JdbcTemplate`、`NamedParameterJdbcTemplate`、原生 `Connection`，以及 MyBatis `@Select` / `@Insert` / `@Update` / `@Delete` 注解。
2. **100% XML 集中管理**：所有持久化 SQL 必须位于各模块 `src/main/resources/mapper/**/*.xml` 中，并与 `infrastructure.persistence.mapper.*Mapper` 接口方法同名对应。
3. **防注入参数化绑定**：XML Mapper 中一律使用 `#{...}` 参数绑定，严禁使用 `${...}` 字符串拼接。
4. **数据库演进**：表结构或约束变更必须通过对应微服务的 `src/main/resources/db/migration/V<N>__*.sql` Flyway 迁移脚本交付，不得直接篡改历史已冻结迁移文件。

---

## 4. 安全与序列化红线

### 4.1 64 位整数 ID 字符串化

JSON 响应中的所有 64 位业务 ID（`userId`、`tenantId`、`roleId`、`shareId`、`fileId` 等）统一序列化为十进制字符串。仅版本号（`version`、`permissionVersion`、`projectionEpoch`）或秒数（`expiresIn`）等数值字段可使用 `@NumericLong` 保持 JSON 数值类型。

### 4.2 默认拒绝与故障闭合（Default Deny & Fail-Closed）

- 授权决策引擎在无匹配授权、投影纪元滞后（`expected_epoch > applied_epoch`）、Condition DSL 语法越界（长度 ≤ 1024 / 深度 ≤ 8 / Token ≤ 64）或下游安全依赖不可用时，必须返回 `DENY` 或对应 `403`/`503` 错误，绝不允许回退放行。
- 字段脱敏在策略缺失、配置畸形或文本长度不足时，一律回退为全掩码 `"***"`，绝不暴露明文。

### 4.3 前端安全定位

前端（[`frontend/iam-admin-ui`](file:///d:/release/enterprise-iam/frontend/iam-admin-ui/package.json)）仅作为用户体验层，严禁在前端硬编码 `role === 'ADMIN'` 等角色字面量判断或权限字符串。页面导航、按钮可见性与表格列定义统一通过 [`useServerMetadata.ts`](file:///d:/release/enterprise-iam/frontend/iam-admin-ui/src/hooks/useServerMetadata.ts) 从服务端 `/admin/v1/navigation/*` 元数据端点获取并支持离线平滑降级。

---

## 5. 测试规范

仓库包含完整的测试套件，必须维护并按需扩展，禁止随意删除现有测试。

### 5.1 必有的架构与冒烟测试

每个微服务与网关均须包含以下测试，缺失即视为构建不合规：

- `LayeredArchitectureTest`：调用 `LayeredArchitectureRules.verify()` 校验分层依赖方向，违反依赖红线直接失败。
- `*ApplicationTest`：Spring Boot 上下文加载冒烟测试，确保 Bean 装配与配置绑定无误。

当前 `iam-gateway`、`iam-auth-service`、`iam-organization-service`、`iam-authorization-service`、`iam-sharing-service`、`iam-file-service`、`iam-audit-service`、`iam-job-service` 均已具备；`iam-identity-service` 待补齐，新增功能时须同步补上。

### 5.2 推荐的测试分层

| 测试类型 | 位置 | 用途 |
| :--- | :--- | :--- |
| 用例测试 | `application/service/*Test.java` | 验证 `*UseCase` 业务逻辑与出站端口协作 |
| 领域测试 | `domain/**/*Test.java` | 验证领域模型纯逻辑（无 Spring 上下文） |
| 持久化测试 | `infrastructure/persistence/*MapperXmlTest.java` | 校验 XML Mapper 语法与 SQL 绑定 |
| 控制器测试 | `interfaces/**/*ControllerTest.java` | 验证 HTTP 契约、参数校验与序列化 |
| 集成测试 | `*IT.java` | 需数据库/中间件的端到端验证 |

### 5.3 测试纪律

- 新增功能须同步补充对应测试，遵循现有测试的命名与结构模式。
- 不得创建与生产代码无关的临时测试文件、调试脚本或 `tools/` 目录。
- 测试不得破坏分层约束：领域层测试不得引入 Spring。

---

## 6. 工作区整洁度

- **`.gitkeep` 仅限 Maven 必需目录**：仅在 Maven 要求存在但暂无文件的源码目录（如空的 `src/main/java/` 包根、`src/main/resources/`）保留 `.gitkeep`；业务代码删除后产生的空目录须同步清理，不得留存 `.gitkeep`。
- **零临时文件**：不得创建调试用的临时脚本、SQL 草稿、`*.tmp`、`*.bak` 文件。
- **零硬编码凭据**：密钥、密码、Token 不得硬编码于代码，须通过环境变量或配置中心注入。开发态默认凭据仅允许出现在 `application.yml` 的 `${ENV:default}` 占位中。

---

## 7. 构建验证指令

每次修改后必须依次执行以下验证，全部通过方可交付：

### 7.1 后端全模块编译检查

```powershell
mvn -f backend/pom.xml compile
```

### 7.2 后端测试（含架构分层校验）

```powershell
mvn -f backend/pom.xml test
```

### 7.3 前端 ESLint 零告警检查

前端工程锁定 `pnpm@9.15.0`，必须使用 pnpm：

```powershell
pnpm -C frontend/iam-admin-ui run lint
```

### 7.4 前端类型检查与生产打包

```powershell
pnpm -C frontend/iam-admin-ui run build
```

---

## 8. 修改边界

- **外科手术式修改**：仅触碰与任务直接相关的代码，不顺手重构相邻代码、不调整无关格式。
- **孤儿清理**：因本次修改导致不再使用的 import、变量、方法须同步删除；不主动删除既有的无关死代码（发现后向用户报告即可）。
- **不创建未请求的文件**：未经用户明确要求，不创建新的文档、配置或脚本文件。
