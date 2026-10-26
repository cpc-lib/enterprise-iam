# AGENTS.md — AI Coding Agent 协作规范与架构红线

本文档为在 `enterprise-iam` 仓库中工作的 AI 编码助手（Agents / Subagents）提供权威架构约束、代码分层铁律、持久化规范与构建验证指南。任何代码修改均不得违反以下规则。

---

## 1. 规格权威与决策准则

1. **权威优先级**：
   - [`docs/spec/spec.md`](file:///d:/release/specs/enterprise-iam/docs/spec/spec.md)（含 D-1 ~ D-25 裁决）具有最高优先级，其次依次为 `SPEC 46 > SPEC 45 > ... > SPEC 37 > SPEC 36 > SPEC 01~35`。
   - 开放决策寄存器见 [`docs/planning/OPEN-DECISIONS.md`](file:///d:/release/specs/enterprise-iam/docs/planning/OPEN-DECISIONS.md)（`DEC-001` ~ `DEC-014`）。在生产环境未提供外部 KMS/HSM 或定制阈值前，代码必须保持 **Fail-Closed 安全默认值**。
2. **契约先行（Contract-First）**：
   - 所有对外或内部 HTTP 端点必须与 [`docs/api/openapi-code-phase-01.yaml`](file:///d:/release/specs/enterprise-iam/docs/api/openapi-code-phase-01.yaml)、[`docs/api/openapi-code-phase-02-policy.yaml`](file:///d:/release/specs/enterprise-iam/docs/api/openapi-code-phase-02-policy.yaml)、[`docs/api/openapi-code-phase-03-sharing-file.yaml`](file:///d:/release/specs/enterprise-iam/docs/api/openapi-code-phase-03-sharing-file.yaml) 保持双向一致。
   - 新增或变更错误码必须同步登记至 [`docs/api/ERROR-CODES.md`](file:///d:/release/specs/enterprise-iam/docs/api/ERROR-CODES.md)。

---

## 2. 后端架构与代码分层红线（DDD + 六边形架构）

每个业务微服务（`iam-auth-service`、`iam-identity-service`、`iam-organization-service`、`iam-authorization-service`、`iam-sharing-service`、`iam-file-service`、`iam-audit-service`、`iam-job-service`）均严格划分为四个顶层包：

```text
com.enterprise.iam.<service>
├── domain          # 领域层：聚合根、实体、值对象、枚举、纯内存领域服务（如 ConditionDslEvaluator）
├── application     # 应用层：用例服务 (*UseCase)、入站命令/查询对象、出站端口接口 (port.out.*)
├── infrastructure  # 基础设施层：MyBatis-Plus Mapper 接口、持久化行对象 (*Row)、仓储适配器 (Jdbc*Repository)、MQ/Redis/MinIO 适配器、Spring @Configuration
└── interfaces      # 接口层：REST Controller、请求/响应 DTO、参数校验
```

### 严禁违反的依赖方向
- **`domain` 层**：必须保持纯 Java 实现，**严禁**导入 `application`、`infrastructure`、`interfaces`、Spring Web、MyBatis、MyBatis-Plus 或 `java.sql.*` / `javax.sql.*`。
- **`application` 层**：仅依赖 `domain` 与自身定义的出站端口（`port.out.*`），**严禁**直接依赖 `infrastructure` 实现类、MyBatis Mapper 或 Spring MVC 注解。
- **`interfaces` 层**：调用 `application` 层的 `*UseCase`，**严禁**跨越应用层直接调用 `infrastructure` 仓储或 Mapper。

---

## 3. 持久层铁律（MyBatis-Plus + 纯 XML Mapper）

根据 [`SPEC 47 §5`](file:///d:/release/specs/enterprise-iam/docs/spec/spec.md) 最终裁决：

1. **禁止内联 SQL 与注解 SQL**：
   - 生产代码中**严禁**使用 Spring `JdbcTemplate`、`NamedParameterJdbcTemplate`、原生 `Connection`，以及 MyBatis `@Select` / `@Insert` / `@Update` / `@Delete` 注解。
2. **100% XML 集中管理**：
   - 所有持久化 SQL 必须位于各模块 `src/main/resources/mapper/**/*.xml` 中，并与 `infrastructure.persistence.mapper.*Mapper` 接口方法同名对应。
3. **防注入参数化绑定**：
   - XML Mapper 中**一律使用 `#{...}` 参数绑定**，**严禁**使用 `${...}` 字符串拼接。
4. **数据库演进与跨库边界**：
   - 表结构或约束变更必须通过对应微服务的 `src/main/resources/db/migration/V<N>__*.sql` Flyway 迁移脚本交付，不得直接篡改历史已冻结迁移文件。

---

## 4. 安全与序列化红线

1. **64 位整数 ID 字符串化（防前端 JS 精度丢失）**：
   - JSON 响应中的所有 64 位业务 ID（`userId`, `tenantId`, `roleId`, `shareId`, `fileId` 等）统一序列化为十进制字符串。
   - 仅版本号（`version`, `permissionVersion`, `projectionEpoch`）或秒数（`expiresIn`）等数值字段可使用 `@NumericLong` 保持 JSON 数值类型。
2. **默认拒绝与故障闭合（Default Deny & Fail-Closed）**：
   - 授权决策引擎在无匹配授权、投影纪元滞后（`expected_epoch > applied_epoch`）、Condition DSL 语法越界（超出 `DEC-014` 长度 ≤ 1024 / 深度 ≤ 8 / Token ≤ 64）或下游安全依赖不可用时，必须返回 `DENY` 或对应 `403`/`503` 错误，绝不允许回退放行。
   - 字段脱敏在策略缺失、配置畸形或文本长度不足时，一律回退为全掩码 `"***"`，绝不暴露明文。
3. **前端安全定位（UX Enforcement Only）**：
   - 前端（[`frontend/iam-admin-ui`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/package.json)）仅作为用户体验层，**严禁**在前端硬编码 `role === 'ADMIN'` 等角色字面量判断或权限字符串。
   - 页面导航、按钮可见性与表格列定义统一通过 [`useServerMetadata.ts`](file:///d:/release/specs/enterprise-iam/frontend/iam-admin-ui/src/hooks/useServerMetadata.ts) 从服务端 `/admin/v1/navigation/*` 元数据端点获取并支持离线平滑降级。

---

## 5. 工作区整洁度与构建验证指令

### 工作区整洁约束
- **零测试文件残留**：按项目交付要求，仓库保持纯生产代码形态，请勿在 `backend/**/src/test`、`frontend/iam-admin-ui/src/**/*.test.*` 或根目录 `tests/`、`tools/` 下创建测试文件。
- **零空文件夹与零 `.gitkeep`**：删除废弃文件后，必须同步清理空目录，不要留存 `.gitkeep` 占位文件。

### 每次修改后的必跑验证命令

1. **后端全模块编译检查**：
   ```powershell
   mvn -f backend/pom.xml compile
   ```
2. **前端 ESLint 零告警检查**：
   ```powershell
   npm -C frontend/iam-admin-ui run lint
   ```
3. **前端 TypeScript 类型检查与生产打包**：
   ```powershell
   npm -C frontend/iam-admin-ui run build
   ```
