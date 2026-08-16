# Enterprise IAM & Dynamic Authorization Platform
## 27 — Domain Event Catalog, Transaction Boundary & Service Interaction Matrix SPEC 1.0

> 本文用于冻结 V1.0 的跨服务协作契约。
>
> 目标：
>
> 1. 明确每个服务可以同步调用谁；
> 2. 明确哪些业务事实通过 MQ 传播；
> 3. 明确每个事件由谁产生、谁消费；
> 4. 明确哪些表必须处于同一本地事务；
> 5. 明确何时使用 Outbox、何时允许 RPC、何时需要 Saga；
> 6. 防止编码阶段出现跨库访问、RPC 环、重复事实源和事务边界漂移。

---

# 1. 核心原则

正式冻结：

```text
Local Transaction First
+
Outbox For Cross-Service Events
+
Projection For Read Performance
+
No Cross-Service Database Access
+
No Authorization Runtime Fan-Out
+
Saga Before Seata
+
Fail Closed For Security Decisions
```

---

# 2. 服务清单

V1.0 核心服务：

```text
iam-gateway

iam-auth-service
iam-identity-service
iam-organization-service
iam-authorization-service
iam-sharing-service
iam-audit-service
iam-job-service
```

外部业务服务：

```text
business-service/*
```

例如：

```text
crm-service
contract-service
project-service
```

它们通过 IAM Starter 接入。

---

# 3. 服务事实源归属

| Domain Fact | Source of Truth |
|---|---|
| Tenant | Identity Service |
| User | Identity Service |
| UserRole | Identity Service |
| Organization | Organization Service |
| Team | Organization Service |
| TeamMember | Organization Service |
| TeamRole | Organization Service |
| Resource/Operation | Authorization Service |
| RolePermission | Authorization Service |
| TeamRolePermission | Authorization Service |
| DataScope | Authorization Service |
| FieldPolicy | Authorization Service |
| API Mapping | Authorization Service |
| PermissionVersion | Authorization Service |
| ResourceShare | Sharing Service |
| Session | Auth Service |
| RefreshToken | Auth Service |
| Security Event | Audit Service |
| Audit Log | Audit Service |
| Business Resource | Corresponding Business Service |
| ACL Projection | Local Business Read Model |

任何服务不得维护第二份“可修改事实源”。

---

# 4. 同步调用原则

同步 RPC 只用于：

```text
当前请求必须立即获得结果
```

例如：

```text
Auth → Identity
Business → Authorization
Gateway → Authorization
Sharing → Authorization
```

不用于：

```text
缓存失效
投影同步
审计
通知
索引更新
```

这些走 Event。

---

# 5. 禁止同步调用链

禁止运行时形成：

```text
Authorization
 → Identity
 → Organization
 → Sharing
```

也禁止：

```text
Business
 → Sharing
 → Organization
 → Identity
```

授权热路径必须主要依赖：

```text
Authorization local read model
+
Redis
+
Caffeine
```

---

# 6. Gateway 同步依赖

Gateway 允许：

```text
Gateway → Authorization
```

用于：

```text
Dynamic API Mapping
Coarse Authorization
```

Authentication：

优先：

```text
JWT local verify
+
Redis Session
```

不要求每请求调用 Auth Service。

---

# 7. Auth Service 同步依赖

允许：

```text
Auth → Identity
```

用于：

```text
Resolve User
Resolve Credential
Check User Status
```

Auth 不允许：

```text
Auth → Authorization
```

来决定用户是否可以登录。

登录和业务权限解耦。

---

# 8. Identity Service 同步依赖

正常业务写入：

```text
Identity
```

应独立完成。

不允许：

```text
Identity → Organization DB
Identity → Authorization DB
```

如果需要后续传播：

```text
Outbox Event
```

---

# 9. Organization Service 同步依赖

主要独立完成：

```text
Organization
Team
Team Member
Team Role
```

用户存在性可通过：

```text
Identity Internal API
```

做轻量校验。

但不能在每个授权请求中实时访问 Identity。

---

# 10. Authorization Service 同步依赖

授权热路径禁止同步 fan-out。

管理写场景允许少量：

```text
Authorization → Identity/Organization
```

仅用于：

```text
校验 subject / team / role 是否存在
```

但更推荐使用本地 Projection。

---

# 11. Sharing Service 同步依赖

创建 Share 时必须同步：

```text
Sharing → Authorization
```

验证：

```text
grantor can SHARE
grantor can access instance
grantor grantable operations
grantor grantable fields
```

必要时：

```text
Sharing → Business Resource Metadata Provider
```

确认：

```text
resource instance exists
owner / status metadata
```

---

# 12. Audit Service 同步依赖

Audit Service 不应成为业务请求主链强依赖。

业务服务：

```text
不需要同步调用 Audit Service
```

高价值审计：

```text
Outbox → MQ → Audit
```

普通低风险访问日志可异步发送。

---

# 13. Job Service 同步依赖

Job Service：

```text
只调用目标领域 Application API
```

例如：

```text
Job → Sharing internal command
Job → Auth cleanup command
Job → Authorization reconcile command
```

Job Processor 不跨数据库直接更新别的服务表。

---

# 14. Domain Event Envelope

所有 Domain Event：

```json
{
  "eventId": "01J...",
  "eventType": "UserRoleChanged",
  "schemaVersion": 1,
  "tenantId": "1001",
  "aggregateType": "USER",
  "aggregateId": "100",
  "aggregateVersion": 8,
  "traceId": "...",
  "occurredAt": "...",
  "payload": {}
}
```

必须字段：

```text
eventId
eventType
schemaVersion
tenantId
aggregateType
aggregateId
aggregateVersion
occurredAt
```

traceId 在存在请求上下文时必须传播。

---

# 15. Identity Event Catalog

## TenantCreated

Producer：

```text
Identity
```

Consumers：

```text
Authorization
Audit
```

用途：

```text
初始化租户级授权元数据
建立版本空间
记录审计
```

---

# 16. TenantStatusChanged

Producer：

```text
Identity
```

Consumers：

```text
Auth
Authorization
Audit
```

效果：

```text
SUSPENDED/DISABLED
→ Auth 拒绝新会话
→ Authorization Hard Guard 生效
```

---

# 17. UserCreated

Producer：

```text
Identity
```

Consumers：

```text
Authorization
Audit
```

Authorization：

```text
建立 Subject Projection
```

---

# 18. UserStatusChanged

Producer：

```text
Identity
```

Consumers：

```text
Auth
Authorization
Audit
```

如果：

```text
DISABLED
```

Auth：

```text
revoke sessions / token version
```

Authorization：

```text
subject status invalid
```

---

# 19. UserRoleChanged

Producer：

```text
Identity
```

Consumers：

```text
Authorization
Audit
```

Payload 建议：

```text
userId
addedRoleIds
removedRoleIds
userVersion
```

---

# 20. Organization Event Catalog

## OrganizationChanged

Producer：

```text
Organization
```

Consumers：

```text
Authorization
Audit
```

---

# 21. TeamChanged

Producer：

```text
Organization
```

Consumers：

```text
Authorization
Audit
```

---

# 22. TeamMemberChanged

Producer：

```text
Organization
```

Consumers：

```text
Authorization
Audit
```

Payload：

```text
teamId
userId
membershipStatus
teamVersion
userMembershipVersion
```

---

# 23. TeamRoleChanged

Producer：

```text
Organization
```

Consumers：

```text
Authorization
Audit
```

---

# 24. TeamMemberRoleChanged

Producer：

```text
Organization
```

Consumers：

```text
Authorization
Audit
```

效果：

```text
Authorization subject projection update
```

---

# 25. Authorization Event Catalog

## ResourceChanged

Producer：

```text
Authorization
```

Consumers：

```text
Gateway
Business Starters(optional metadata cache)
Audit
```

---

# 26. OperationChanged

Producer：

```text
Authorization
```

Consumers：

```text
Gateway
Audit
```

---

# 27. ApiMappingChanged

Producer：

```text
Authorization
```

Consumers：

```text
Gateway
Audit
```

效果：

```text
API mapping cache invalidation
```

---

# 28. RolePermissionChanged

Producer：

```text
Authorization
```

Consumers：

```text
Authorization internal projection/cache
Audit
```

必须：

```text
RoleVersion++
```

同事务。

---

# 29. TeamRolePermissionChanged

Producer：

```text
Authorization
```

Consumers：

```text
Authorization projection/cache
Audit
```

必须：

```text
TeamRoleVersion++
```

同事务。

---

# 30. DataScopeChanged

Producer：

```text
Authorization
```

Consumers：

```text
Authorization cache
Audit
Security Event Processor
```

如果权限扩大：

```text
可以触发 High Risk Security Event
```

---

# 31. FieldPolicyChanged

Producer：

```text
Authorization
```

Consumers：

```text
Authorization cache
Audit
Security Event Processor
```

---

# 32. PermissionVersionChanged

Producer：

```text
Authorization
```

Consumers：

```text
Gateway
Business IAM Client
Authorization Cache
Audit
```

这是：

```text
撤权快速失效
```

的重要事件。

但安全正确性不能只依赖消息到达。

---

# 33. Sharing Event Catalog

## ResourceShareCreated

Producer：

```text
Sharing
```

Consumers：

```text
Authorization
Business ACL Projection
Audit
```

Payload 使用：

```text
State-Carrying Event
```

---

# 34. ResourceShareChanged

用于：

```text
WAITING → ACTIVE
```

等非终态变化。

Consumers 同上。

---

# 35. ResourceShareRevoked

Producer：

```text
Sharing
```

Consumers：

```text
Authorization
Business ACL Projection
Audit
```

必须在同一 Share 事务内：

```text
share.status
+
share.version
+
permission version/epoch coordination
+
outbox
```

---

# 36. ResourceShareExpired

Producer：

```text
Sharing
```

Consumers：

```text
Authorization
Business ACL Projection
Audit
```

注意：

```text
运行时 expire_time 已保证安全
```

此事件主要用于状态收敛。

---

# 37. Auth Event Catalog

## SessionCreated

Producer：

```text
Auth
```

Consumers：

```text
Audit
```

不需要 Authorization 消费。

---

# 38. SessionRevoked

Producer：

```text
Auth
```

Consumers：

```text
Audit
Security Monitoring
```

---

# 39. RefreshTokenReuseDetected

Producer：

```text
Auth
```

Consumers：

```text
Audit
Security Event Processor
```

属于：

```text
CRITICAL
```

---

# 40. PasswordChanged

Producer：

```text
Auth
```

Consumers：

```text
Audit
Security Notification
```

---

# 41. Business Resource Event Catalog

业务服务需要发布：

```text
ResourceCreated
ResourceUpdated
ResourceDeleted
ResourceOwnershipChanged
```

其中与 IAM 强相关：

```text
ResourceDeleted
ResourceOwnershipChanged
```

---

# 42. ResourceDeleted

Producer：

```text
Business Service
```

Consumers：

```text
Sharing
Authorization
Audit
```

效果：

```text
Share runtime invalid
Projection cleanup
Audit
```

---

# 43. ResourceOwnershipChanged

Producer：

```text
Business Service
```

Consumers：

```text
Sharing
Authorization
Audit
```

Sharing 根据：

```text
share_survives_owner_transfer
```

策略处理。

---

# 44. Audit Event Catalog

Audit 不应再次向核心权限域发布“事实改变”事件。

它可以发布：

```text
SecurityAlertTriggered
AuditArchiveCompleted
```

但不能成为：

```text
User/Role/Share
```

事实源。

---

# 45. Transaction Boundary — Identity

Create User：

```text
BEGIN

iam_user
iam_user_identity
sys_outbox_event(UserCreated)
sys_idempotency_record(success)

COMMIT
```

如果 User 与 Identity 属于同服务：

必须同一本地事务。

---

# 46. Assign User Role Transaction

```text
BEGIN

iam_user_role
user_version++
sys_outbox_event(UserRoleChanged)
permission-change-local-record(optional source audit fact)

COMMIT
```

不能：

```text
commit UserRole
then later update version
```

---

# 47. Team Member Transaction

```text
BEGIN

iam_team_member
membership version
sys_outbox_event(TeamMemberChanged)

COMMIT
```

TeamRole 绑定如果同一命令：

```text
可以同事务
```

---

# 48. Role Permission Transaction

```text
BEGIN

iam_role_permission
iam_role_data_scope(optional)
iam_role_field_policy(optional)
role permission version++
sys_outbox_event(RolePermissionChanged)
sys_outbox_event(PermissionVersionChanged)
sys_idempotency_record

COMMIT
```

高价值审计事实也必须可可靠传播。

---

# 49. TeamRole Permission Transaction

同理：

```text
team role permission
data scope
field policy
version
outbox
```

同一本地事务。

---

# 50. API Mapping Transaction

```text
BEGIN

iam_api_resource_mapping
iam_api_security_policy
mapping_version++
outbox(ApiMappingChanged)
audit event outbox

COMMIT
```

---

# 51. Share Create Transaction

```text
BEGIN

iam_resource_share
iam_resource_share_operation
iam_resource_share_field
share_version
share epoch/version
sys_outbox_event(ResourceShareCreated)
sys_idempotency_record

COMMIT
```

不能在事务中：

```text
等待 RabbitMQ
等待 Audit
等待 Business DB
```

---

# 52. Share Revoke Transaction

```text
BEGIN

share status → REVOKED
revoke metadata
share_version++
permission/share epoch++
outbox(ResourceShareRevoked)
idempotency status

COMMIT
```

Commit 后：

```text
旧授权版本不可再作为 ALLOW
```

---

# 53. Login Transaction Boundary

登录：

```text
BEGIN

login_session
refresh_token
login security state changes(optional)

COMMIT
```

Redis Session：

```text
DB commit 后写入
```

失败：

```text
补偿 revoke DB session
```

---

# 54. Refresh Rotation Transaction

必须：

```text
BEGIN

RT1 ACTIVE → ROTATED
RT2 INSERT ACTIVE
session metadata update(optional)

COMMIT
```

绝不能：

```text
先提交 RT2
再异步 revoke RT1
```

---

# 55. Disable User Cross-Service Boundary

User Disable 涉及：

```text
Identity
Auth
Authorization
```

不能用一个跨库本地事务。

正确：

```text
Identity local TX
 ↓
UserStatusChanged event
 ↓
Auth revoke session
Authorization subject invalid
```

安全窗口控制：

```text
Authorization/User Status version
+
Auth security state
```

而不是单纯等待 MQ。

---

# 56. Tenant Bootstrap Boundary

属于跨服务业务流程。

正式：

```text
Saga
```

步骤：

```text
Create Tenant
Create Initial User
Create Org
Create Team
Create IAM Role
Assign Role
Activate Tenant
```

失败：

```text
INITIALIZATION_FAILED
```

允许补偿/重试。

V1 不要求强制 Seata。

---

# 57. New Business Integration Boundary

业务服务接入 IAM：

```text
API Discovery
Resource Metadata
Data Schema
Field Metadata
```

属于：

```text
配置注册流程
```

不是业务请求的同步事务。

---

# 58. Projection Consumer Transaction

例如 ACL：

```text
BEGIN

insert consume_record
upsert acl_projection
update projection checkpoint/version

COMMIT

ACK
```

三者必须同一业务数据库事务。

---

# 59. Consumer Duplicate

如果：

```text
consume_record SUCCESS
```

再次收到：

```text
ACK
```

不执行 Projection。

---

# 60. Projection Version Gap

如果：

```text
incoming.version > current.version + 1
```

State-Carrying Event：

```text
可 apply
+
mark gap
+
schedule reconcile
```

Delta Event：

```text
不得直接 apply
```

---

# 61. Outbox Relay Boundary

Relay：

```text
claim event
 ↓
publish
 ↓
publisher confirm
 ↓
mark PUBLISHED
```

Outbox Relay 本身不需要与原业务事务绑定，因为原事务已经提交。

---

# 62. Audit Ingest Boundary

消费高价值审计：

```text
BEGIN

consume_record
audit_log

COMMIT

ACK
```

避免重复审计。

---

# 63. Job Boundary

Job 不直接形成跨服务事务。

例如 Share Expire：

```text
PowerJob
 ↓
Sharing Application Service
 ↓
Sharing local TX
```

---

# 64. Service Interaction Matrix

| Caller | Callee | Mode | Purpose | Runtime Critical |
|---|---|---|---|---|
| Gateway | Authorization | Sync | API mapping/coarse authz | Yes |
| Auth | Identity | Sync | resolve user/credential | Login only |
| Organization | Identity | Sync/Projection | validate user | Admin write |
| Authorization | Identity Projection | Local | subject data | Yes |
| Authorization | Organization Projection | Local | team/teamrole data | Yes |
| Sharing | Authorization | Sync | grantability check | Share create |
| Sharing | Business Metadata Provider | Sync | instance existence/owner | Share create |
| Business | Authorization | Sync | fine-grained authz | Yes |
| Identity | Authorization | Event | subject/role updates | Async |
| Organization | Authorization | Event | team updates | Async |
| Sharing | Authorization | Event | ACL/share updates | Async |
| Sharing | Business Projection | Event | local ACL | Async |
| All Core Services | Audit | Event | audit | Async |
| Job | Domain Service | Sync | scheduled command | Background |

---

# 65. 禁止 Interaction

以下禁止：

```text
Business → Identity DB
Business → Sharing DB
Authorization → Sharing DB
Authorization → Organization DB
Gateway → Identity DB
Audit → Core Domain DB
Job → Other Service DB
```

---

# 66. RPC 超时原则

安全主链：

```text
短 timeout
```

例如：

```text
Business → Authorization
```

不可：

```text
60s read timeout
```

具体数值压测确定。

---

# 67. RPC Retry 原则

默认：

```text
0~1 次有限重试
```

对于：

```text
非幂等命令
```

禁止透明自动重试。

写操作重试必须依赖：

```text
Idempotency-Key
```

---

# 68. Event Retry 原则

Event Consumer：

```text
Retry Queue
+
DLQ
```

不能：

```text
无限 requeue=true
```

---

# 69. Event 命名规范

过去式：

```text
UserCreated
RolePermissionChanged
ResourceShareRevoked
```

不要：

```text
CreateUser
UpdateRole
```

Event 表示：

```text
已经发生的事实
```

---

# 70. Command 命名规范

Application Command：

```text
CreateUserCommand
AssignUserRoleCommand
RevokeResourceShareCommand
DisableUserCommand
```

Command 表示：

```text
请求发生改变
```

---

# 71. Query 命名规范

```text
GetUserQuery
PageUsersQuery
ExplainAuthorizationQuery
```

Query：

```text
不得产生业务状态变化
```

---

# 72. Event Payload 原则

事件不应该把：

```text
整个数据库 Entity
```

序列化。

只包含 Consumer 需要的稳定业务字段。

敏感字段禁止进入事件。

---

# 73. State-Carrying Event 原则

Projection 相关事件优先：

```text
完整当前状态
```

例如 Share：

```text
shareId
resourceId
instanceKey
target
operations
start
expire
status
version
```

降低乱序和丢中间事件风险。

---

# 74. Event Schema 兼容

增加字段：

```text
兼容
```

删除/改语义：

```text
schemaVersion++
```

滚动升级期间：

```text
Consumer 支持 old + new
```

---

# 75. Permission Version 传播矩阵

| Change | Version Dimension |
|---|---|
| User status | UserVersion |
| UserRole | UserVersion |
| RolePermission | RoleVersion |
| Team membership | UserMembershipVersion / TeamVersion |
| TeamRole permission | TeamRoleVersion |
| Resource status | ResourceVersion |
| Operation status | Resource/OperationVersion |
| Share | Share/Resource ACL Epoch |
| Data Scope | Grant Binding Version |
| Field Policy | Grant Binding Version |

---

# 76. Immediate Revocation Contract

任何撤权事实提交后：

```text
old ALLOW
```

不得依赖：

```text
event consumer eventually catches up
```

必须通过：

```text
version mismatch
hard guard
expire time
```

立即失效。

---

# 77. Eventual Availability vs Security

允许：

```text
新授权传播稍慢
```

导致短暂：

```text
under-grant
```

不允许：

```text
撤权传播慢
```

造成：

```text
over-grant
```

安全策略：

```text
宁可暂时拒绝
不允许继续越权
```

---

# 78. Source-of-Truth Reconcile Matrix

| Projection | Source of Truth |
|---|---|
| Authorization User/Role ReadModel | Identity |
| Authorization Team ReadModel | Organization |
| Authorization Share ReadModel | Sharing |
| Business ACL Projection | Sharing |
| Authorization Cache | Authorization DB/ReadModel |
| API Mapping Cache | Authorization |
| Session Redis Cache | Auth DB |
| Audit Search Index | Audit DB |

---

# 79. Rebuild Contract

所有 Projection 必须具备：

```text
Full Rebuild
Incremental Reconcile
Version Check
Health Status
```

---

# 80. Rebuild 不得破坏在线安全

重建时：

```text
旧 projection
+
new build namespace/table
```

完成后：

```text
atomic switch
```

优于：

```text
TRUNCATE
然后慢慢重建
```

对于大规模 ACL 尤其重要。

---

# 81. Security Event Production Matrix

| Trigger | Producer |
|---|---|
| Refresh Token Reuse | Auth |
| Cross Tenant Attempt | Gateway/Authorization |
| Share Escalation | Sharing |
| Field Write Denied | Field Permission Starter/Audit path |
| Internal Signature Failure | Security Starter |
| PUBLIC API Expansion | Authorization |
| Projection Version Gap | Consumer/Job |

最终统一进入 Audit/Security Center。

---

# 82. Audit Reliability Matrix

| Audit Type | Delivery |
|---|---|
| Permission Change | Outbox required |
| Share Create/Revoke | Outbox required |
| User Disable | Outbox required |
| Password Change | Outbox required |
| Refresh Reuse | Outbox/high reliability |
| Normal READ access | Async/sampling |
| Normal ALLOW decision | Sampling |
| DENY | 100% async audit |

---

# 83. Transaction Anti-Patterns

禁止：

```text
@Transactional
+
remote RPC
+
slow external API
```

长时间混在一个本地事务中。

禁止：

```text
DB commit
then direct RabbitMQ send
without outbox
```

禁止：

```text
cross-service DB transaction
```

通过共享 DataSource 完成。

---

# 84. Shared Database Anti-Pattern

即使开发环境：

```text
一个 MySQL 实例
```

也不能：

```text
iam_authorization
JOIN
iam_identity
```

生产边界必须从开发阶段就保持。

---

# 85. Service Ownership Rule

任何表只能：

```text
一个服务写
```

其他服务如需数据：

```text
Internal API
Event Projection
```

读取。

---

# 86. Double Write 防护

例如：

```text
RolePermission changed
```

禁止：

```text
Update DB
Update Redis
Send MQ
```

三步全靠应用顺序保证。

正确：

```text
DB + Version + Outbox local TX
```

Redis：

```text
通过 Version / Event 收敛
```

---

# 87. Cache Invalidation Contract

缓存失效：

```text
Version First
Event Second
TTL Last Resort
```

不能只靠：

```text
delete redis key
```

---

# 88. Service Failure Behavior

Identity 不可用：

```text
新登录/用户管理受影响
已有授权尽可能使用安全 ReadModel
```

Organization 不可用：

```text
团队管理受影响
已有授权使用 ReadModel
```

Sharing 不可用：

```text
不能创建新 Share
已有 Share 运行时由现有投影/version/expiry 决定
```

Audit 不可用：

```text
核心授权继续
高价值审计事件排队
```

---

# 89. Authorization Failure Behavior

Authorization unavailable：

```text
safe version-valid cache
→ may use

otherwise
→ DENY/503
```

禁止：

```text
allow all
```

---

# 90. Event Correlation

一个业务命令：

```text
traceId
```

产生多个事件：

```text
eventId each unique
```

同 Aggregate：

```text
aggregateVersion monotonic
```

这样可以还原完整链路。

---

# 91. Service Interaction Acceptance

必须通过架构测试/代码扫描确保：

```text
No cross DB access
No business permission hardcoding
No RabbitTemplate in Domain
No Mapper outside Infrastructure
No Audit synchronous dependency in authz path
```

---

# 92. Event Contract Test

每个 Producer/Consumer 必须：

```text
schema test
serialization test
duplicate test
version test
```

---

# 93. Transaction Test

每个关键本地事务必须至少测试：

```text
business mutation failure
outbox failure
commit failure
duplicate request
optimistic conflict
```

---

# 94. Integration Matrix Test

至少建立：

```text
Identity → Authorization
Organization → Authorization
Sharing → Authorization
Sharing → Business ACL Projection
Auth → Audit
Authorization → Gateway cache
```

端到端集成测试。

---

# 95. CODE PHASE 应用

后续编码时，一个跨服务 Story 必须写清：

```text
Source Service
Target Service
Sync or Event
Transaction Boundary
Outbox Required?
Version Changed?
Consumer Idempotency?
Failure Strategy?
```

缺任一项：

```text
Story 不进入开发
```

---

# 96. Story 示例 — Revoke Share

```text
Source:
Sharing

Local TX:
share status + version + outbox

Event:
ResourceShareRevoked

Consumers:
Authorization
Business ACL Projection
Audit

Immediate Security:
permission/share version mismatch

Retry:
Idempotent command

Failure:
MQ down → outbox backlog
```

---

# 97. Story 示例 — Remove User From Team

```text
Source:
Organization

Local TX:
membership state + version + outbox

Event:
TeamMemberChanged

Consumers:
Authorization
Audit

Immediate Security:
subject/team membership version invalidates old allow

No cross-service transaction
```

---

# 98. Story 示例 — Disable User

```text
Source:
Identity

Local TX:
user status + user version + outbox

Event:
UserStatusChanged

Consumers:
Auth
Authorization
Audit

Immediate Security:
user status/version guard

Auth:
revoke sessions asynchronously but safely
```

---

# 99. V1.0 Interaction Freeze

V1.0 不新增：

```text
Identity ↔ Sharing synchronous cycles
Authorization ↔ Organization hot RPC
Business ↔ Identity runtime authorization calls
Audit ↔ Core write-back
```

如果未来必须增加：

```text
需要 ADR
```

---

# 100. SPEC 27 冻结结论

从本 SPEC 起，跨服务协作必须遵循：

```text
One Source of Truth
One Local Transaction Boundary
Outbox for Facts
Projection for Reads
Version for Immediate Revocation
Idempotent Consumers
No Cross-DB Access
No Runtime Authorization Fan-Out
```

进入代码阶段后，任何新的同步 RPC、Domain Event、跨服务事务，都必须先确认是否符合本矩阵。
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
