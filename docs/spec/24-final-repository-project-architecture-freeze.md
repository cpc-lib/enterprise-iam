# Enterprise IAM & Dynamic Authorization Platform
## 24 — Final Repository, Project Architecture & SPEC Freeze 1.0

> 本文是 Enterprise IAM & Dynamic Authorization Platform V1.0 的最终项目架构、仓库结构、模块边界、数据库归属、部署关系和工程实施冻结文档。
>
> 从本文冻结后开始，项目正式由 **SPEC 阶段** 进入 **工程实现阶段**。除非出现 P0/P1 级安全、正确性或架构问题，否则不再随意调整服务边界、数据库归属、授权语义、核心 Starter 责任和关键依赖关系。

---

# 1. 项目定位

本项目不是普通的：

```text
User + Role + Permission
```

后台，而是：

```text
Enterprise IAM
+
Dynamic Authorization Platform
+
Team Collaboration Authorization
+
Data Permission
+
Field Permission
+
Resource ACL
+
Security Audit
```

核心目标：

```text
统一认证
统一授权
统一权限元数据
统一数据权限
统一字段权限
统一跨团队共享
统一审计
统一幂等与一致性
```

正式原则：

# Permission As Data, Not Permission As Code

业务代码禁止：

```text
customer:update
system:user:update
ROLE_ADMIN
if (isAdmin)
@PreAuthorize("...")
hasAuthority("...")
```

授权语义全部来自运行时元数据：

```text
Application
Service
Resource
Operation
Role
Team Role
Data Scope
Field Policy
ACL
Condition Policy
```

---

# 2. 总体项目架构

整体采用：

```text
微服务
+
DDD 分层
+
事件驱动
+
读模型 / Projection
+
本地事务 + Outbox
+
动态授权 Starter
```

总体架构：

```text
                         ┌────────────────────┐
                         │   React Admin UI   │
                         │ TypeScript + AntD  │
                         └─────────┬──────────┘
                                   │ HTTPS
                                   ▼
                         ┌────────────────────┐
                         │    IAM Gateway     │
                         │                    │
                         │ Authentication     │
                         │ Dynamic API Map    │
                         │ Rate Limit         │
                         │ Coarse PEP         │
                         └─────────┬──────────┘
                                   │
                    Signed Delegation / Service Identity
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌───────────────┐        ┌──────────────────┐       ┌──────────────────┐
│ Auth Service  │        │ Identity Service │       │ Organization Svc │
│               │        │                  │       │                  │
│ Login         │        │ User             │       │ Organization     │
│ JWT           │        │ Role             │       │ Team             │
│ Session       │        │ UserRole         │       │ Team Member      │
│ Refresh Token │        │ Credential Ref   │       │ Team Role        │
└───────┬───────┘        └────────┬─────────┘       └────────┬─────────┘
        │                         │                          │
        └──────────────┬──────────┴──────────────┬───────────┘
                       │                         │
                       ▼                         ▼
              ┌──────────────────┐      ┌──────────────────┐
              │ Authorization Svc│      │  Sharing Service │
              │                  │      │                  │
              │ Resource         │      │ Resource Share   │
              │ Operation        │      │ Reshare          │
              │ Permission       │      │ Share Field      │
              │ Data Scope       │      │ Share Operation  │
              │ Field Policy     │      │ ACL Source       │
              │ Explain          │      └────────┬─────────┘
              │ Permission Ver   │               │
              └────────┬─────────┘               │
                       │                         │
                       └──────────────┬──────────┘
                                      │
                                      ▼
                              ┌────────────────┐
                              │ RabbitMQ       │
                              │ Domain Events  │
                              └───────┬────────┘
                                      │
                  ┌───────────────────┼───────────────────┐
                  ▼                   ▼                   ▼
        ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
        │ Audit Service  │  │ Projection     │  │ Job Service    │
        │                │  │ Consumers      │  │                │
        │ Login Audit    │  │ ACL Projection │  │ PowerJob       │
        │ Authz Audit    │  │ Subject Model  │  │ Reconcile      │
        │ Security Event │  │ Cache Invalidate│ │ Expire/Cleanup │
        └────────────────┘  └────────────────┘  └────────────────┘
```

---

# 3. 授权主链架构

一次受保护业务请求：

```text
React
 ↓
Gateway
 ↓
Authentication
 ↓
Dynamic API Mapping
 ↓
Resource + Operation
 ↓
Authorization Service
 ↓
RBAC
 ↓
Team Role
 ↓
Direct Grant
 ↓
ACL / Share
 ↓
Data Scope
 ↓
Field Policy
 ↓
Decision
 ↓
Business Service
 ↓
MyBatis Data Permission
 ↓
Field Write Guard
 ↓
Database
 ↓
Response Field Filter
 ↓
React
```

核心要求：

```text
Gateway 粗粒度授权
+
业务服务细粒度强制授权
```

不能只依赖 Gateway。

---

# 4. PEP / PDP / PIP 架构

正式采用：

```text
PEP
Policy Enforcement Point

PDP
Policy Decision Point

PIP
Policy Information Point
```

映射：

```text
Gateway
Business Starter
    =
PEP

Authorization Service
    =
PDP

Identity / Organization / Sharing / Projection
    =
PIP
```

---

# 5. 为什么业务服务还需要 PEP

即使 Gateway 已授权：

```text
Customer.UPDATE
```

业务服务仍需要：

```text
Instance
DataScope
Field Write
ACL
```

校验。

原因：

```text
内部 RPC
Gateway Bypass
TOCTOU
程序 Bug
```

都可能绕过 Gateway。

所以安全边界：

```text
Gateway
+
Downstream Starter
```

双层存在。

---

# 6. Authorization 热路径

禁止：

```text
Business Request
 ↓
Identity Service
 ↓
Organization Service
 ↓
Sharing Service
 ↓
Authorization Service
```

每次串行调用多个服务。

正式设计：

```text
Authorization Service
维护 Subject / Permission Read Model
```

事件来源：

```text
IdentityChanged
TeamChanged
RoleChanged
ShareChanged
```

通过 RabbitMQ 同步。

运行时：

```text
L1 Caffeine
 ↓
Redis
 ↓
Authorization Read Model / MySQL
```

最大限度避免同步 fan-out。

---

# 7. 项目仓库总体结构

正式采用 Monorepo：

```text
enterprise-iam/
├── pom.xml
├── README.md
├── CHANGELOG.md
├── LICENSE
├── .editorconfig
├── .gitignore
│
├── backend/
├── frontend/
├── deploy/
├── docs/
├── scripts/
├── tools/
├── tests/
└── .github/
```

原因：

```text
一人 + AI
```

模式下，Monorepo 更适合：

```text
统一版本
统一 CI
统一 SPEC
统一依赖
统一 Docker
统一 API 协作
```

---

# 8. Backend Maven 总体结构

```text
backend/
├── pom.xml
│
├── iam-dependencies/
│
├── iam-framework/
│
├── iam-gateway/
│
├── iam-auth-service/
├── iam-identity-service/
├── iam-organization-service/
├── iam-authorization-service/
├── iam-sharing-service/
├── iam-audit-service/
├── iam-job-service/
│
└── iam-test-support/
```

---

# 9. iam-dependencies

统一 BOM：

```text
iam-dependencies
```

负责统一：

```text
Spring Boot
Spring Cloud
Spring Cloud Alibaba
MyBatis-Plus
JSqlParser
Redisson
RabbitMQ
Flyway
Seata Client
PowerJob Worker
Jackson
OpenTelemetry
Testcontainers
ArchUnit
```

业务模块：

```text
禁止自行散落版本号
```

---

# 10. Framework 最终结构

```text
iam-framework/
├── iam-common-core/
├── iam-common-web/
├── iam-common-tenant/
├── iam-common-security/
├── iam-common-mybatis/
├── iam-common-redis/
├── iam-common-mq/
├── iam-common-lock/
├── iam-common-observability/
│
├── iam-authorization-client-spring-boot-starter/
├── iam-security-spring-boot-starter/
├── iam-data-permission-spring-boot-starter/
├── iam-field-permission-spring-boot-starter/
├── iam-idempotent-spring-boot-starter/
├── iam-outbox-spring-boot-starter/
└── iam-audit-spring-boot-starter/
```

---

# 11. Framework 边界

Framework 允许：

```text
统一异常
统一返回
安全上下文
Tenant Context
Actor Context
HTTP Client
Redis
MQ Envelope
Idempotency
Outbox
Data Permission
Field Permission
Audit SPI
```

禁止：

```text
User Entity
Role Entity
Team Entity
Permission Entity
ResourceShare Aggregate
任何业务 Mapper
```

避免形成 Shared Business Monolith。

---

# 12. DDD 服务内部结构

每个核心服务统一：

```text
service-module/
└── src/main/java/.../
    ├── interfaces/
    │   ├── rest/
    │   ├── internal/
    │   ├── message/
    │   └── job/
    │
    ├── application/
    │   ├── command/
    │   ├── query/
    │   ├── service/
    │   └── dto/
    │
    ├── domain/
    │   ├── model/
    │   ├── aggregate/
    │   ├── entity/
    │   ├── valueobject/
    │   ├── repository/
    │   ├── service/
    │   ├── policy/
    │   └── event/
    │
    └── infrastructure/
        ├── persistence/
        ├── redis/
        ├── mq/
        ├── rpc/
        ├── config/
        └── repository/
```

依赖方向：

```text
interfaces
   ↓
application
   ↓
domain

infrastructure
   ↓
domain
```

Domain 禁止反向依赖 Infrastructure。

---

# 13. Gateway 架构

`iam-gateway`：

```text
gateway/
├── authentication/
├── authorization/
├── api-mapping/
├── delegation/
├── security-header/
├── rate-limit/
├── trace/
└── route/
```

职责：

```text
外部入口
认证
API Mapping
粗粒度授权
限流
动态 PUBLIC/AUTH/INTERNAL
签发短期 Delegation Token
```

禁止：

```text
直接实现 Role/Team/DataScope 算法
```

---

# 14. Auth Service 架构

```text
iam-auth-service
```

领域：

```text
LoginSession
RefreshToken
Credential
UserSecurityState
AuthenticationRisk
```

职责：

```text
Login
Logout
JWT
Refresh
Session
Token Rotation
Token Reuse Detection
Password Change
Strong Auth
```

不负责：

```text
RBAC
Team
Resource Permission
```

---

# 15. Identity Service 架构

```text
iam-identity-service
```

职责：

```text
Tenant
User
User Identity
Role
User Role
```

注意：

Application / Resource / Operation：

```text
最终归 Authorization Service
```

因为它们属于授权元数据，而不是用户身份。

---

# 16. Organization Service 架构

```text
iam-organization-service
```

职责：

```text
Organization
Team
Team Member
Team Role
Team Member Role
```

Team Role 的：

```text
角色定义
成员绑定
```

归 Organization。

Team Role → Permission：

```text
归 Authorization
```

---

# 17. Authorization Service 架构

这是核心 PDP。

职责：

```text
Application
Service Metadata
Resource
Operation
ResourceOperation
Permission
RolePermission
TeamRolePermission
DataScope
FieldPolicy
ConditionPolicy
PermissionVersion
API Definition
API Resource Mapping
API Security Policy
Authorization Explain
Authorization Snapshot
Direct Grant
Temporary Grant
```

---

# 18. Authorization Engine 内部

```text
authorization/
├── subject/
├── resource/
├── grant/
├── merge/
├── condition/
├── data/
├── field/
├── decision/
├── explain/
├── version/
└── cache/
```

核心 Pipeline：

```text
Subject Resolve
 ↓
Resource Resolve
 ↓
Grant Resolve
 ↓
Applicable Filter
 ↓
Priority Merge
 ↓
Data Scope
 ↓
Field Policy
 ↓
Decision
```

---

# 19. Authorization 冲突规则冻结

正式语义：

```text
Hard Guard
优先于所有普通 Grant
```

普通 Grant：

```text
最高 priority tier 胜出
```

相同 Priority：

```text
DENY > ALLOW
```

无 Grant：

```text
DEFAULT DENY
```

Data Scope / Field Policy：

```text
只能收缩已授予 Operation
```

不能独立产生 Operation ALLOW。

---

# 20. Sharing Service 架构

```text
iam-sharing-service
```

职责：

```text
Resource Share
Share Target
Share Operation
Share Field
Reshare
Expire
Revoke
Grant Basis
Share Policy
Share State Machine
```

Share 只产生：

```text
ALLOW
```

不实现第二套 DENY 模型。

---

# 21. ACL 读写分离

Sharing Service：

```text
Write Model
Source of Truth
```

业务查询：

```text
Local ACL Projection
```

避免：

```text
Business SQL
JOIN
Sharing DB
```

架构：

```text
Sharing DB
 ↓
Outbox
 ↓
RabbitMQ
 ↓
Business Local Projection
```

---

# 22. Audit Service 架构

```text
iam-audit-service
```

职责：

```text
Login Audit
Admin Audit
Permission Change
Authorization Decision
Resource Access
Sensitive Field Access
Security Event
Infrastructure Operation
```

属于：

```text
异步旁路服务
```

故障不能阻塞实时授权。

---

# 23. Job Service 架构

```text
iam-job-service
```

PowerJob 适配器。

任务：

```text
Share Activate
Share Expire
Temporary Grant Expire
Session Cleanup
Refresh Cleanup
Outbox Relay
Projection Reconcile
Permission Reconcile
Audit Archive
Field Metadata Reconcile
```

Job 只调 Application Service。

Processor 不承载复杂业务规则。

---

# 24. Starter 架构关系

业务微服务引入：

```text
iam-security-starter
iam-client-starter
iam-data-permission-starter
iam-field-permission-starter
iam-idempotent-starter
iam-outbox-starter
iam-audit-starter
```

形成：

```text
Business Controller
      │
      ▼
Security Context
      │
      ▼
Authorization Client
      │
      ▼
Application Service
      │
      ├── Field Write Guard
      ├── Idempotency
      └── Audit
      │
      ▼
MyBatis
      │
      ├── Tenant Interceptor
      ├── Data Permission Interceptor
      └── Field Update Guard
      │
      ▼
Database
```

---

# 25. Data Permission 架构

```text
Authorization Service
 ↓
DataPredicate
 ↓
Business Starter
 ↓
JSqlParser AST
 ↓
MyBatis SQL
```

正式支持：

```text
SELF
TEAM
TEAM_AND_CHILDREN
SPECIFIED_TEAM
SHARED
ALL
```

原则：

```text
Business Predicate
AND
Security Predicate
```

---

# 26. Field Permission 架构

写：

```text
HTTP Body
 ↓
Submitted Field Detection
 ↓
Field Write Guard
 ↓
MyBatis SET Column Guard
```

读：

```text
Business VO
 ↓
Jackson Serialization
 ↓
Field Filter
 ↓
Mask
 ↓
JSON
```

禁止业务代码：

```text
dto.setSalary(null)
```

实现权限隐藏。

---

# 27. 认证传播架构

外部 Token：

```text
User JWT
```

Gateway 验证后：

```text
Signed Delegation Token
```

传给业务服务。

业务服务：

```text
验证 Gateway Signature
验证 audience
验证 exp
验证 token type
```

客户端自带：

```text
X-User-Id
X-Tenant-Id
X-Resource-Id
```

全部视为不可信并移除。

---

# 28. Service-to-Service 架构

内部调用：

```text
Service Identity
+
Optional User Delegation
```

不能使用：

```text
固定 X-Internal-Token
```

长期共享 Secret。

第一阶段：

```text
Short-lived Service JWT
```

生产可升级：

```text
mTLS
```

---

# 29. 同步调用边界

允许的主要同步调用：

```text
Gateway → Authorization

Business → Authorization

Auth → Identity

Sharing → Authorization

Sharing → Resource Metadata SPI
```

应避免：

```text
Authorization
→ Identity
→ Organization
→ Sharing
```

每次请求实时链式 RPC。

这类数据通过：

```text
Event + Read Model
```

进入 Authorization。

---

# 30. 事件架构

统一：

```text
Domain Event Envelope
```

包含：

```text
eventId
eventType
schemaVersion
tenantId
aggregateType
aggregateId
aggregateVersion
traceId
occurredAt
payload
```

---

# 31. 关键事件

Identity：

```text
UserChanged
UserRoleChanged
```

Organization：

```text
TeamChanged
TeamMemberChanged
TeamRoleChanged
```

Authorization：

```text
PermissionChanged
FieldPolicyChanged
DataScopeChanged
ApiMappingChanged
PermissionVersionChanged
```

Sharing：

```text
ResourceShareCreated
ResourceShareRevoked
ResourceShareExpired
```

Auth：

```text
SessionRevoked
UserSecurityChanged
```

---

# 32. 一致性架构

写链：

```text
Application Service
 ↓
Local Transaction
 ├── Domain Table
 ├── Permission Version
 ├── Idempotency
 └── Outbox
 ↓
COMMIT
 ↓
Outbox Relay
 ↓
RabbitMQ
 ↓
Consumer Idempotency
 ↓
Projection
```

核心理念：

```text
DB 保证正确性
Version 保证立即撤权
MQ 负责传播
Projection 负责读性能
Reconcile 负责最终收敛
```

---

# 33. 分布式事务架构

默认：

```text
Local TX + Outbox
```

跨服务：

```text
Saga First
```

Seata：

```text
Selective Use
```

只用于：

```text
确实要求跨数据库强原子性
```

场景。

禁止：

```text
所有 Service 都 @GlobalTransactional
```

---

# 34. Redis 架构

Redis 用于：

```text
Session
Authorization Cache
Permission Version
API Mapping
Rate Limit
Replay Prevention
Distributed Lock
```

不是 Source of Truth。

缓存模型：

```text
L1 Caffeine
 ↓
L2 Redis
 ↓
MySQL / Projection
```

---

# 35. Permission Version 架构

正式采用 Version Vector 思路：

```text
UserVersion
RoleVersion
TeamVersion
TeamRoleVersion
ResourceVersion
ApplicationVersion
```

用户 Effective Version：

```text
hash(
 userVersion
 roleVersions
 teamVersions
 teamRoleVersions
)
```

避免：

```text
Team 权限变化
→ 遍历更新100万用户
```

---

# 36. 撤权链路

例如 Role 撤权：

```text
Update RolePermission
 ↓
RoleVersion++
 ↓
Outbox
 ↓
Commit
```

这一刻：

```text
旧缓存 Version 不匹配
```

下一请求：

```text
拒绝使用旧 ALLOW
```

即使 MQ 尚未消费：

```text
仍不能继续放行
```

---

# 37. 数据库最终归属

正式冻结：

## iam_auth

```text
iam_login_session
iam_refresh_token
iam_user_security_state
password/reset related tables
```

## iam_identity

```text
iam_tenant
iam_user
iam_user_identity
iam_role
iam_user_role
```

## iam_organization

```text
iam_organization
iam_team
iam_team_member
iam_team_role
iam_team_member_role
```

## iam_authorization

```text
iam_application
iam_service
iam_resource
iam_operation
iam_resource_operation
iam_permission
iam_role_permission
iam_team_role_permission
iam_api_definition
iam_api_resource_mapping
iam_api_security_policy
iam_resource_data_schema
iam_data_scope
iam_role_data_scope
iam_team_role_data_scope
iam_role_data_scope_team
iam_team_role_data_scope_team
iam_resource_field
iam_mask_strategy
iam_field_policy
iam_role_field_policy
iam_team_role_field_policy
iam_direct_grant
iam_temporary_grant
iam_condition_policy
iam_permission_version
iam_authorization_snapshot
```

## iam_sharing

```text
iam_resource_share
iam_resource_share_operation
iam_resource_share_field
iam_resource_share_history
iam_resource_share_basis
iam_resource_sharing_policy
```

## iam_audit

```text
iam_login_audit_log
iam_admin_audit_log
iam_permission_change_log
iam_authorization_log
iam_resource_access_log
iam_sensitive_field_access_log
iam_security_event
```

---

# 38. 11 号数据库 SPEC 修正冻结

此前 Database SPEC 存在若干需要修正的点，本次正式冻结：

### 38.1 Team Role Data Scope

必须增加：

```text
iam_team_role_data_scope
```

而不是只支持 Role Data Scope。

### 38.2 Team Field Policy

正式命名：

```text
iam_team_role_field_policy
```

不是：

```text
iam_team_field_policy
```

因为权限来源是 Team Role。

### 38.3 Specific Team Data Scope

不再使用语义模糊：

```text
data_scope_binding_id
```

正式拆分：

```text
iam_role_data_scope_team
iam_team_role_data_scope_team
```

### 38.4 Resource Share

原：

```text
resource_type_id
```

正式改：

```text
resource_id
```

### 38.5 Resource Instance

正式命名：

```text
resource_instance_key VARCHAR(128)
```

而不是含义较弱的：

```text
resource_instance_id
```

泛型资源可以是：

```text
Long
UUID
String Business Key
```

### 38.6 Infrastructure Tables

以下不是中央表：

```text
sys_outbox_event
sys_idempotency_record
sys_message_consume_record
```

而是：

```text
每个服务 Schema 自己拥有一份
```

---

# 39. Logical Delete 冻结

不统一使用：

```text
deleted=0/1
```

解决所有生命周期。

分类：

Metadata：

```text
可 logical delete
```

Grant / Share：

```text
REVOKED / EXPIRED
```

Audit：

```text
Append Only
```

---

# 40. Active Unique Strategy

如果业务需要逻辑删除后允许重新创建同名记录：

不使用：

```text
UNIQUE(code, deleted)
```

因为多次删除会冲突。

采用：

```text
delete_marker BIGINT NOT NULL DEFAULT 0
```

Active：

```text
delete_marker=0
```

删除时：

```text
delete_marker=id
```

唯一索引：

```text
tenant + code + delete_marker
```

---

# 41. Timestamp 冻结

统一：

```text
DATETIME(3)
```

推荐数据库默认：

```text
created_at
DEFAULT CURRENT_TIMESTAMP(3)

updated_at
DEFAULT CURRENT_TIMESTAMP(3)
ON UPDATE CURRENT_TIMESTAMP(3)
```

重要业务操作仍显式写应用时间。

---

# 42. React 总体架构

```text
frontend/
└── iam-admin-ui/
    ├── src/
    │   ├── app/
    │   ├── api/
    │   ├── stores/
    │   ├── layouts/
    │   ├── pages/
    │   ├── features/
    │   ├── components/
    │   ├── hooks/
    │   ├── types/
    │   ├── utils/
    │   └── tests/
    └── ...
```

技术：

```text
React
TypeScript
Ant Design
Zustand
Axios
TailwindCSS
```

---

# 43. React 架构原则

采用：

```text
Feature Based
```

而不是所有业务堆：

```text
pages/
```

例如：

```text
features/user
features/role
features/team
features/resource
features/resource-share
features/auth-explain
features/audit
```

---

# 44. React 权限模型

React 不维护：

```text
permission strings
```

由后端返回：

```text
Navigation Schema
Page Schema
Operation Decision
Field Schema
```

前端：

```text
controlId
→ operationId
→ allowed
```

完成 UI 显示。

---

# 45. React 安全边界

React：

```text
UX
```

Backend：

```text
Security Boundary
```

即使 React：

```text
隐藏 salary
```

用户手工 POST：

```text
salary
```

后端仍必须拒绝。

---

# 46. React 管理端模块

最终菜单：

```text
Dashboard

身份管理
├── 用户
├── 角色
└── Session

组织与团队
├── Organization
├── Team
├── Team Member
└── Team Role

授权管理
├── Resource
├── Operation
├── Permission
├── API Mapping
├── Data Scope
├── Field Policy
└── Mask Strategy

资源共享
├── 我创建的
├── 分享给我的
└── Share 管理

授权诊断
├── Explain
├── Simulator
├── Permission Source
└── Snapshot Compare

审计中心
├── Login Audit
├── Admin Audit
├── Permission Change
├── Authorization Log
└── Sensitive Field Audit

安全中心
├── Security Events
├── DLQ
└── Projection Health
```

---

# 47. Deploy 总体架构

```text
deploy/
├── docker/
│   ├── compose/
│   ├── mysql/
│   ├── redis/
│   ├── rabbitmq/
│   ├── nacos/
│   ├── seata/
│   ├── powerjob/
│   ├── minio/
│   └── nginx/
│
├── env/
└── scripts/
```

---

# 48. 开发运行拓扑

推荐：

```text
Windows
│
├── IntelliJ
│   └── Java Services
│
├── Node / pnpm
│   └── React
│
└── Docker
    ├── MySQL
    ├── Redis
    ├── RabbitMQ
    ├── Nacos
    ├── PowerJob
    ├── MinIO
    └── Seata optional
```

开发阶段不要求每次 Java 改代码都 Docker rebuild。

---

# 49. Full Container Demo

最终必须交付：

```text
docker-compose.full.yml
```

启动：

```text
Infrastructure
+
Gateway
+
All IAM Services
+
React Admin
```

用于：

```text
Demo
CI Integration
Release Validation
```

---

# 50. Production 拓扑

```text
Internet
  │
  ▼
Load Balancer
  │
  ▼
Gateway xN
  │
  ├──────────────┐
  ▼              ▼
Auth xN       Authorization xN
  │              │
  ├───────┬──────┼────────┐
  ▼       ▼      ▼        ▼
Identity Org   Sharing   Audit
                  │
                  ▼
                MQ
                  │
          ┌───────┴────────┐
          ▼                ▼
      Projection         Job
```

数据层：

```text
MySQL HA
Redis HA
RabbitMQ Cluster
Nacos Cluster
MinIO
```

---

# 51. Service Dependency Graph

同步：

```text
Gateway
  → Auth / Authorization

Auth
  → Identity

Business
  → Authorization

Sharing
  → Authorization
  → Resource Metadata Provider
```

异步：

```text
Identity
  → RabbitMQ
  → Authorization

Organization
  → RabbitMQ
  → Authorization

Sharing
  → RabbitMQ
  → Authorization / Business Projection

Authorization
  → RabbitMQ
  → Cache / Audit
```

---

# 52. 禁止跨库直接访问

严格禁止：

```text
Authorization Service
SELECT identity_db.iam_user
```

或者：

```text
Business Service
JOIN sharing_db.iam_resource_share
```

即使开发环境使用同一个 MySQL Server：

```text
逻辑服务边界仍然必须保持
```

---

# 53. Flyway 结构

每服务：

```text
src/main/resources/db/migration/
```

例如：

```text
iam-authorization-service/
└── src/main/resources/db/migration/
    ├── V1__authorization_baseline.sql
    ├── V2__data_scope.sql
    ├── V3__field_policy.sql
    └── V4__permission_version.sql
```

---

# 54. SQL 目录

项目根另外保留：

```text
docs/database/
```

作为：

```text
ERD
Table Dictionary
Index Design
Migration Guide
```

不是重复 Flyway 执行源。

Flyway 才是真正可执行 Schema Source。

---

# 55. docs 最终结构

```text
docs/
├── spec/
├── architecture/
├── adr/
├── api/
├── database/
├── testing/
├── deployment/
└── diagrams/
```

---

# 56. SPEC 目录冻结

```text
docs/spec/
├── 01-project-overview.md
├── 02-domain-and-bounded-context.md
├── 03-rbac-model.md
├── 04-team-authorization.md
├── 05-resource-operation-model.md
├── 06-data-permission.md
├── 07-field-permission.md
├── 08-resource-sharing.md
├── 09-dynamic-api-authorization.md
├── 10-distributed-consistency.md
├── 11-database-design.md
├── 12-backend-architecture.md
├── 13-authorization-engine.md
├── 14-data-permission-engine.md
├── 15-field-permission-engine.md
├── 16-resource-sharing-acl-engine.md
├── 17-authentication-session-security.md
├── 18-idempotency-consistency-framework.md
├── 19-audit-explain-observability.md
├── 20-react-admin-architecture.md
├── 21-testing-quality-gate.md
├── 22-deployment-runtime-architecture.md
├── 23-solo-developer-ai-plan.md
└── 24-final-repository-project-architecture-freeze.md
```

---

# 57. Architecture 文档

```text
docs/architecture/
├── system-context.md
├── container-architecture.md
├── authorization-runtime.md
├── event-architecture.md
├── data-ownership.md
├── cache-and-version.md
└── deployment-topology.md
```

---

# 58. ADR 冻结

至少：

```text
docs/adr/
├── 0001-permission-as-data.md
├── 0002-no-business-permission-hardcoding.md
├── 0003-gateway-and-downstream-pep.md
├── 0004-authorization-read-model.md
├── 0005-local-outbox.md
├── 0006-share-local-projection.md
├── 0007-short-lived-jwt-session.md
├── 0008-fail-closed.md
├── 0009-saga-before-seata.md
└── 0010-monorepo.md
```

---

# 59. Test 总体目录

```text
tests/
├── e2e/
├── performance/
├── security/
├── chaos/
└── datasets/
```

服务内：

```text
src/test/
```

负责：

```text
Unit
Domain
Integration
Architecture
```

---

# 60. iam-test-support

公共测试工具：

```text
iam-test-support/
├── fixture/
├── builder/
├── testcontainers/
├── scenario/
└── assertions/
```

包含：

```text
TestUserBuilder
TestRoleBuilder
TestTeamBuilder
TestShareBuilder
AuthorizationScenarioDSL
```

禁止业务生产代码依赖此模块。

---

# 61. Quality Gate

PR：

```text
Compile
Unit
Domain
ArchUnit
Coverage
Migration
Integration
Frontend Unit
Permission Hardcoding Scan
```

Main：

```text
Contract
Core E2E
Security Regression
Docker Build
```

Release：

```text
Full E2E
Security Golden Scenarios
Performance Smoke
Migration Upgrade
Backup Restore
Container Scan
```

---

# 62. Golden Security Scenarios

永久冻结：

```text
Cross Tenant Deny

Immediate Revoke

Field Write Deny

Sensitive Field Hide/Mask

ACL Escalation Deny

Expired Share Deny Without Job

HTTP Idempotency

MQ Duplicate

Refresh Token Reuse

Spoofed User/Tenant Header Deny
```

任何版本失败：

```text
禁止发布
```

---

# 63. Infrastructure Tables

每个需要的服务拥有本地：

```text
sys_outbox_event
sys_idempotency_record
sys_message_consume_record
```

Job Service：

```text
sys_job_business_record
```

这些属于：

```text
Infrastructure Tables
```

而不是统一中央数据库。

---

# 64. Seata 架构冻结

系统：

```text
支持 Seata
```

但默认：

```text
不用
```

优先级：

```text
Local TX
>
Outbox
>
MQ
>
Saga
>
Seata
```

只有明确无法补偿且必须原子完成时才使用。

---

# 65. PowerJob 架构冻结

PowerJob 不决定安全有效性。

例如：

```text
Share status ACTIVE
```

但：

```text
expire_time < now
```

运行时仍：

```text
DENY
```

Job 只负责：

```text
状态收敛
清理
重建
归档
```

---

# 66. Observability 架构

所有服务：

```text
Structured Log
+
Micrometer Metrics
+
OpenTelemetry Trace
```

主链：

```text
traceId
```

授权：

```text
decisionId
```

事件：

```text
eventId
```

三者关联。

---

# 67. Security Failure Strategy

核心统一：

# Fail Closed

如果无法确认：

```text
User Identity
Tenant
API Mapping
Authorization
Data Permission
Field Permission
ACL
Version
```

则：

```text
DENY / 401 / 403 / 503
```

不能默认放行。

---

# 68. MVP / Beta / V1 Freeze

## MVP

包含：

```text
Auth
User
Role
Team
Resource
Operation
Dynamic API Mapping
RBAC
Basic Data Scope
Basic Field Permission
React Basic Admin
Docker Infrastructure
```

## Beta

增加：

```text
Cross-Team ACL
Idempotency
Outbox
MQ
Projection
PowerJob
```

## V1.0

增加：

```text
Audit
Explain
Security Center
Complete E2E
Failure Testing
Full Docker Demo
```

---

# 69. V1.1 / V2 暂缓

以下不进入 V1.0：

```text
Full ABAC visual designer
WebAuthn complete
Flowable permission approval
ClickHouse production integration
Kubernetes Helm
Service Mesh
Multi-region
Folder inheritance ACL
Resource Collection Share
Dynamic Map Field
Advanced SoD
```

防止范围持续扩张。

---

# 70. 一人 + AI 工期冻结

正式采用：

```text
MVP       Week 10~12
Beta      Week 16
RC        Week 23
V1.0      Week 24
```

全职：

```text
约 24 周
```

约：

```text
110~130 人日
```

---

# 71. 工程生成顺序

SPEC Freeze 后不一次生成所有代码。

按 Vertical Slice：

## Phase 1

```text
Root Maven
BOM
Framework
Docker Infrastructure
```

## Phase 2

```text
Identity
Auth
Gateway
```

目标：

```text
Login 可跑通
```

## Phase 3

```text
Resource
Operation
Role
Authorization Engine
```

目标：

```text
动态 RBAC 可跑通
```

## Phase 4

```text
Organization
Team
Team Role
```

## Phase 5

```text
Data Permission
Field Permission
```

## Phase 6

```text
Sharing
ACL Projection
```

## Phase 7

```text
Idempotency
Outbox
MQ
Job
```

## Phase 8

```text
React Admin
```

## Phase 9

```text
Audit
Explain
Security Center
```

## Phase 10

```text
Full Docker
E2E
Security
Release
```

---

# 72. 第一批真正代码生成范围

进入 CODE 阶段后的第一批不生成完整 IAM。

建议只生成：

```text
enterprise-iam/
├── root pom
├── iam-dependencies
├── iam-common-core
├── iam-common-web
├── iam-common-tenant
├── iam-common-security
├── iam-gateway skeleton
├── iam-auth-service skeleton
├── iam-identity-service skeleton
├── deploy infrastructure
└── README
```

要求：

```text
mvn clean verify
docker compose infrastructure up
```

均通过。

---

# 73. 第二批代码

完成：

```text
User
Role
Login
JWT
Session
Refresh
Gateway Authentication
```

形成第一个完整 Vertical Slice。

---

# 74. 第三批代码

实现：

```text
Application
Resource
Operation
Permission
RolePermission
API Mapping
Authorization
```

到这里才能证明：

```text
No Permission Hardcoding
```

架构真正成立。

---

# 75. 禁止 Big-Bang Generation

禁止一次让 AI：

```text
生成 8 个服务
100 张表
1000 个 Java 文件
```

这种代码即使能编译：

```text
也很难 Review
很难测试
很难定位架构偏差
```

正确方式：

```text
一个 Vertical Slice
一次生成
一次测试
一次 Review
```

---

# 76. CODE 阶段 AI 任务约束

每个任务必须给 AI：

```text
SPEC section
模块
目标
不可修改范围
安全约束
验收测试
```

AI 输出必须经过：

```text
Compile
Test
Architecture Review
```

才能进入 main。

---

# 77. Repository 最终形态

```text
enterprise-iam/
│
├── backend/
│   ├── iam-dependencies/
│   ├── iam-framework/
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
│
├── deploy/
│   ├── docker/
│   ├── env/
│   └── scripts/
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
├── tests/
│   ├── e2e/
│   ├── security/
│   ├── performance/
│   └── chaos/
│
├── scripts/
├── tools/
├── .github/
├── README.md
└── CHANGELOG.md
```

---

# 78. 项目架构最终总结

整个系统最终是一套：

```text
Identity
+
Organization
+
Authentication
+
Dynamic Authorization
+
Data Permission
+
Field Permission
+
Cross-Team ACL
+
Audit
+
Distributed Consistency
```

平台。

架构中心不是：

```text
Role
```

而是：

```text
Authorization Decision
```

Role、Team、Share、DataScope、FieldPolicy：

```text
都是 Decision Input
```

---

# 79. 最终安全模型

完整授权公式：

```text
FinalDecision =
TenantGuard
AND
SubjectGuard
AND
OperationPermission
AND
ConditionPolicy
AND
InstanceOrDataPermission
AND
FieldConstraint
```

其中：

```text
OperationPermission
```

来源可以：

```text
Direct
Role
TeamRole
Owner
Share
TemporaryGrant
```

---

# 80. 最终工程原则

正式冻结以下十条：

```text
1. Permission As Data

2. Backend Is Security Boundary

3. Default Deny

4. Fail Closed

5. Tenant Always First

6. Local Transaction First

7. Outbox For Event Consistency

8. Version For Immediate Revocation

9. Projection For Read Performance

10. Test Every Security Assumption
```

---

# 81. SPEC Freeze 状态

从本文完成后：

```text
SPEC 01 ~ 24
```

正式视为：

```text
V1.0 BASELINE
```

允许继续修改：

```text
错误
矛盾
P0/P1安全问题
实现过程中证明不可行的设计
```

禁止：

```text
无止境新增功能
频繁调整服务边界
为了“看起来高级”增加技术
```

---

# 82. 下一步

SPEC 阶段完成后，正式进入：

# CODE PHASE 01 — Enterprise IAM Backend Foundation

第一批工程生成目标：

```text
Maven Monorepo

iam-dependencies BOM

iam-framework common modules

Gateway Skeleton

Auth Skeleton

Identity Skeleton

Docker Infrastructure

Flyway Baseline

Windows PowerShell Scripts

README
```

首个验收：

```text
mvn clean verify
```

成功。

以及：

```text
docker compose up -d
```

基础设施 Healthy。

完成后进入：

```text
CODE PHASE 02 — Authentication Vertical Slice
```

而不是继续扩张 SPEC。


# 186. 本次实际交付目录

本 SPEC 已实际落到 `enterprise-iam/` 目录中，项目根包含 `backend/`、`frontend/iam-admin-ui/`、`docs/spec/`、`deploy/`、`tests/` 等真实目录。
---

## Final Consistency Addendum — Share Revocation Security Fence

Final V1.x rule:

```text
Share create / permission expansion:
Sharing local transaction + Outbox is sufficient.
Temporary propagation delay may cause under-grant, never pre-authorized over-grant.

Share revoke / permission reduction:
Use a short selective Seata global transaction:
  1. Sharing DB: revoke/reduce share and increment iam_share_projection_epoch
  2. Authorization DB: increment iam_share_security_epoch / related permission version
```

If this short security transaction cannot be committed, the revoke/reduction command fails rather than reporting a successful but unsafe partial revoke.

Runtime:

```text
Authorization produces expectedShareEpoch.
Business local ACL projection exposes last contiguous checkpoint.
checkpoint < expectedShareEpoch => SHARED branch DENY / fail closed.
checkpoint > authorization plan epoch => refresh authorization plan; if unavailable => DENY.
```

Expiration remains safe without Seata because every runtime decision enforces `start <= now < expire`; PowerJob only converges persisted status.

This addendum supersedes any earlier wording implying that a cross-service revoke can be completed by one local database transaction.
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
