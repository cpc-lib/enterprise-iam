# Enterprise IAM & Dynamic Authorization Platform
## 30 — Application Service, Command/Query & Domain Aggregate SPEC 1.0

> 本文冻结 V1.0 的 Application Service、Command、Query、Aggregate、Domain Service、Domain Event 与 Repository 调用关系。
>
> 目标：让后续 Java 代码生成能够从业务 Use Case 直接映射到类，而不是从数据库表反推 Service。
>
> 核心原则：
>
> ```text
> REST / MQ / Job
>       ↓
> Application Service
>       ↓
> Domain Aggregate / Domain Service
>       ↓
> Repository
>       ↓
> Outbox / Version / Audit Fact
> ```
>
> Application Service 负责“编排”，Domain 负责“规则”，Infrastructure 负责“技术实现”。

---

# 1. Application Layer 定位

Application Layer 负责：

```text
Use Case orchestration
Transaction boundary
Authorization prerequisite
Idempotency coordination
Repository coordination
Domain Event collection
Outbox append
DTO assembly
```

不负责：

```text
SQL
MyBatis Wrapper
RabbitTemplate
Redis command
HTTP serialization
```

---

# 2. Domain Layer 定位

Domain 负责：

```text
Business invariants
State machine
Permission merge semantics
Share escalation rules
Token rotation rules
Status transitions
Version-sensitive business rules
```

不依赖：

```text
Spring MVC
MyBatis
Feign
RabbitMQ
Redis
PowerJob
```

---

# 3. Interfaces Layer 定位

Interfaces：

```text
REST
Internal RPC
MQ Consumer
Job Adapter
```

只做：

```text
Protocol mapping
Authentication context extraction
Command/Query construction
Response mapping
```

禁止：

```text
直接写 Repository
直接改 Aggregate status
```

---

# 4. Command 与 Query

Command：

```text
可能改变状态
```

例如：

```text
CreateUserCommand
DisableUserCommand
AssignUserRolesCommand
CreateResourceShareCommand
RevokeResourceShareCommand
RotateRefreshTokenCommand
```

Query：

```text
不得改变业务状态
```

例如：

```text
GetUserDetailQuery
PageUsersQuery
ExplainAuthorizationQuery
ListSharesQuery
```

---

# 5. Command 命名规则

统一：

```text
动词 + 业务对象 + Command
```

例如：

```text
CreateRoleCommand
UpdateRolePermissionsCommand
RemoveTeamMemberCommand
CreateResourceShareCommand
```

不要：

```text
RoleSaveCommand
UserHandleCommand
ProcessPermissionCommand
```

---

# 6. Query 命名规则

统一：

```text
Get
List
Page
Search
Explain
Compare
```

例如：

```text
GetRoleDetailQuery
PageSecurityEventsQuery
ExplainAuthorizationQuery
CompareAuthorizationSnapshotQuery
```

---

# 7. Application Service 命名

按领域 Use Case：

```text
UserApplicationService
RoleApplicationService
TeamApplicationService
AuthorizationApplicationService
ResourceShareApplicationService
AuthenticationApplicationService
```

避免：

```text
CommonService
BaseService
PermissionManager
```

---

# 8. Aggregate 设计原则

Aggregate 必须：

```text
足够小
围绕一致性边界
```

不要把：

```text
Tenant
User
Roles
Teams
Sessions
Permissions
Shares
```

做成一个 User Aggregate。

---

# 9. V1 Aggregate 清单

正式冻结：

```text
Tenant
User
Role
Team
TeamRole
Application
Resource
Operation
RolePermissionBinding
TeamRolePermissionBinding
ResourceShare
LoginSession
RefreshToken
SecurityEvent
TemporaryGrant
```

某些关系如 UserRole、TeamMember：

```text
可以是独立关系 Aggregate / Domain Entity
```

根据事务边界实现。

---

# 10. Tenant Aggregate

状态：

```text
INITIALIZING
ACTIVE
SUSPENDED
DISABLED
INITIALIZATION_FAILED
```

Domain methods：

```text
activate()
suspend(reason)
resume()
disable(reason)
markInitializationFailed(reason)
```

禁止：

```text
tenant.setStatus(...)
```

---

# 11. Tenant Bootstrap Application Service

Command：

```text
BootstrapTenantCommand
```

Application：

```text
TenantBootstrapApplicationService
```

流程：

```text
Idempotency acquire
 ↓
Create Tenant INITIALIZING
 ↓
Persist
 ↓
Outbox TenantCreated
 ↓
Trigger/continue Saga
```

跨服务后续：

```text
Initial User
Root Organization
Default Team
Bootstrap Role
```

不塞入一个本地事务。

---

# 12. Tenant Bootstrap Saga

Saga State：

```text
TENANT_CREATED
USER_CREATED
ORG_CREATED
TEAM_CREATED
ROLE_CREATED
ROLE_ASSIGNED
ACTIVATED
FAILED
```

每步：

```text
可重试
幂等
可追踪
```

失败：

```text
Tenant INITIALIZATION_FAILED
```

---

# 13. User Aggregate

字段语义：

```text
identity
profile
status
version
```

Domain methods：

```text
activate()
lock(reason)
unlock()
disable(reason)
enable()
archive()
```

---

# 14. Create User Command

```text
CreateUserCommand
```

Application：

```text
UserApplicationService.createUser()
```

顺序：

```text
Validate tenant active
 ↓
Check uniqueness
 ↓
User.create(...)
 ↓
UserRepository.save
 ↓
UserIdentityRepository.save
 ↓
Outbox UserCreated
 ↓
Idempotency SUCCESS
```

同一本地事务：

```text
User
UserIdentity
Outbox
Idempotency final state
```

---

# 15. Disable User Command

```text
DisableUserCommand
```

流程：

```text
Load User
 ↓
user.disable()
 ↓
save with version
 ↓
increment UserVersion
 ↓
Outbox UserStatusChanged
 ↓
Audit fact
```

后续 Auth/Authorization：

```text
Event-driven
```

但安全语义必须支持立即失效。

---

# 16. Assign User Roles Command

```text
AssignUserRolesCommand
```

Request 表示：

```text
最终 roleId 集合
```

Application：

```text
load current active roles
calculate added/removed
validate roles
persist binding diff
increment user version
outbox UserRoleChanged
audit diff
```

---

# 17. Role Aggregate

Role 负责：

```text
role metadata
status
```

Permission Binding 不塞入 Role Aggregate 的巨大 collection。

权限绑定独立：

```text
RolePermissionBinding
```

---

# 18. Update Role Permissions Command

```text
UpdateRolePermissionsCommand
```

Application：

```text
Validate Role active
 ↓
Load current bindings
 ↓
Diff
 ↓
Create/update/revoke bindings
 ↓
Validate DataScope/FieldPolicy references
 ↓
RoleVersion++
 ↓
Outbox RolePermissionChanged
 ↓
Outbox PermissionVersionChanged
 ↓
Audit
```

---

# 19. RolePermissionBinding Aggregate

它是策略锚点。

包含：

```text
roleId
permissionId
effect
priority
conditionPolicy
start/expire
status
version
```

关联：

```text
DataScope
FieldPolicy
```

必须按：

```text
role_permission_id
```

绑定。

---

# 20. Team Aggregate

负责：

```text
team metadata
hierarchy
status
version
```

Domain：

```text
rename
move
disable
archive
```

Move Team：

```text
必须校验不能形成循环
```

---

# 21. Team Membership Use Case

Commands：

```text
AddTeamMemberCommand
RemoveTeamMemberCommand
```

流程：

```text
Validate Team active
Validate User
 ↓
Create/close membership
 ↓
membership version
 ↓
outbox TeamMemberChanged
 ↓
audit
```

---

# 22. TeamRole Aggregate

TeamRole：

```text
属于某个 Team
```

负责：

```text
role metadata/status
```

权限绑定：

```text
TeamRolePermissionBinding
```

独立。

---

# 23. Assign Team Member Roles Command

```text
AssignTeamMemberRolesCommand
```

流程：

```text
Validate membership active
 ↓
Validate TeamRole belongs same Team
 ↓
Diff bindings
 ↓
Persist
 ↓
Version++
 ↓
Outbox TeamMemberRoleChanged
```

---

# 24. Application Metadata Aggregate

IAM `Application`：

```text
逻辑应用
```

不是 Spring Application 实例。

Domain：

```text
create
rename
disable
```

---

# 25. Resource Aggregate

Resource：

```text
application
service
code
type
sharing policy reference
status
version
```

Domain methods：

```text
enable()
disable()
enableSharing()
disableSharing()
```

---

# 26. Operation Aggregate

Operation 动态元数据：

```text
code
name
riskLevel
status
```

不写 Java enum：

```text
CUSTOMER_UPDATE
```

---

# 27. Register Resource Operation Command

Command：

```text
BindResourceOperationCommand
```

作用：

```text
Resource
+
Operation
```

建立支持关系。

不等同：

```text
Grant Role Permission
```

---

# 28. API Discovery Use Case

Command：

```text
DiscoverApiDefinitionsCommand
```

来源：

```text
iam-api-discovery starter
```

流程：

```text
normalize service/method/path
 ↓
upsert API definition
 ↓
mark lastSeen
 ↓
unknown mappings remain UNMAPPED
```

无授权逻辑硬编码。

---

# 29. Map API Command

```text
MapApiToResourceOperationCommand
```

流程：

```text
load API
load Resource
load Operation
validate ResourceOperation supported
 ↓
save mapping
save security policy
mappingVersion++
outbox ApiMappingChanged
audit
```

---

# 30. Authorization Application Service

核心方法：

```text
authorize()
batchAuthorize()
explain()
```

它不是 CRUD Service。

---

# 31. Authorization Request Flow

```text
Build trusted subject context
 ↓
Hard Guards
 ↓
Resolve subject read model
 ↓
Resolve resource/operation
 ↓
Resolve applicable grants
 ↓
Evaluate conditions
 ↓
Merge grants
 ↓
Build data permission plan
 ↓
Build field permission plan
 ↓
Decision
 ↓
Audit event
```

---

# 32. Authorization Domain Components

建议：

```text
AuthorizationEngine
GrantResolver
GrantMergePolicy
ConditionEvaluator
DataPermissionPlanner
FieldPermissionPlanner
AuthorizationExplainCollector
```

---

# 33. AuthorizationEngine

纯 Domain Service：

```text
input
→ decision
```

不得：

```text
查 DB
读 Redis
发 MQ
```

数据先由 Application 准备。

---

# 34. GrantResolver

各来源：

```text
DirectGrantResolver
UserRoleGrantResolver
TeamRoleGrantResolver
OwnerGrantResolver
ShareGrantResolver
TemporaryGrantResolver
```

Resolver 顺序：

```text
不决定优先级
```

最终由：

```text
GrantMergePolicy
```

统一处理。

---

# 35. Hard Guard

至少：

```text
TenantGuard
SubjectStatusGuard
ResourceStatusGuard
OperationStatusGuard
```

Hard Guard 失败：

```text
立即 DENY
```

普通 Grant 不可覆盖。

---

# 36. Authorization Explain

Explain：

```text
同一 AuthorizationEngine
```

只是增加：

```text
ExplainCollector
```

禁止实现第二套：

```text
explain-only logic
```

---

# 37. DataPermissionPlanner

输入：

```text
winning/contributing grants
resource data schema
acting team
subject memberships
share epoch
```

输出：

```text
DataPermissionPlan
```

禁止输出：

```text
raw SQL
```

---

# 38. FieldPermissionPlanner

输入：

```text
contributing grant bindings
resource fields
operation
```

输出：

```text
FieldPermissionPlan
```

后续 Starter 执行。

---

# 39. ResourceShare Aggregate

核心字段：

```text
resourceId
instanceKey
target
operations
fields
parent/root
depth
start/expire
canReshare
status
version
```

---

# 40. ResourceShare Domain Methods

```text
activate(now)
revoke(operator, reason, now)
expire(now)
assertEffective(now)
assertCanReshare(...)
createDerivedShare(...)
```

---

# 41. Create Resource Share Command

```text
CreateResourceShareCommand
```

Application 流程：

```text
Idempotency acquire
 ↓
Validate target
 ↓
Authorization grantability check
 ↓
Business resource metadata check
 ↓
Load sharing policy
 ↓
ResourceShare.create(...)
 ↓
persist aggregate
 ↓
increment share/resource epoch
 ↓
outbox ResourceShareCreated
 ↓
audit fact
 ↓
commit
```

---

# 42. Share 外部调用顺序

必须：

```text
RPC validation
before
critical DB lock
```

避免：

```text
hold DB lock while calling remote service
```

如需要防 TOCTOU：

```text
expectedResourceVersion
+
local re-check
```

---

# 43. Revoke Share Command

```text
RevokeResourceShareCommand
```

流程：

```text
load share
 ↓
authorization to revoke
 ↓
share.revoke()
 ↓
save CAS
 ↓
epoch/version++
 ↓
outbox
 ↓
audit
```

重复调用：

```text
idempotent outcome
```

无重复 Domain Event。

---

# 44. Reshare Command

```text
ReshareResourceCommand
```

流程：

```text
load parent
validate parent effective
validate canReshare
validate depth
validate operation subset
validate field subset
validate expiry
 ↓
create child
 ↓
persist
 ↓
outbox
```

---

# 45. Share Expire Job

Job：

```text
Find expirable share ids
```

逐批：

```text
ExpireResourceShareCommand
```

Application：

```text
load share
share.expire(now)
save CAS
outbox if changed
```

重复执行：

```text
safe no-op
```

---

# 46. LoginSession Aggregate

状态：

```text
ACTIVE
LOGGED_OUT
FORCED_LOGOUT
PASSWORD_CHANGED
USER_DISABLED
COMPROMISED
EXPIRED
```

Domain：

```text
logout()
forceLogout()
markPasswordChanged()
markUserDisabled()
markCompromised()
expire()
```

---

# 47. Authentication Application Service

主要 Commands：

```text
LoginCommand
RefreshAccessTokenCommand
LogoutCommand
LogoutAllCommand
ForceLogoutCommand
ChangePasswordCommand
```

Queries：

```text
ListMySessionsQuery
GetSessionQuery
```

---

# 48. Login Command

流程：

```text
Resolve Identity
 ↓
Verify User/Tenant status
 ↓
Verify credential
 ↓
Risk check
 ↓
Create LoginSession
 ↓
Create RefreshToken
 ↓
Persist local TX
 ↓
Write Redis session
 ↓
Issue Access JWT
 ↓
Audit
```

Redis 写失败：

```text
compensate session revoke
login fails
```

---

# 49. RefreshToken Aggregate

状态：

```text
ACTIVE
ROTATED
REVOKED
EXPIRED
COMPROMISED
```

Domain：

```text
rotate()
revoke()
expire()
markCompromised()
```

---

# 50. Refresh Access Token Command

流程：

```text
hash presented token
 ↓
load token FOR UPDATE
 ↓
validate ACTIVE
 ↓
rotate RT1
create RT2
 ↓
commit
 ↓
update session cache
 ↓
issue new access token
```

---

# 51. Refresh Reuse

如果旧 Token 已：

```text
ROTATED
```

再次使用：

```text
evaluate grace/reuse policy
```

真实复用：

```text
mark family compromised
revoke session
outbox security event
audit
```

---

# 52. Force Logout Command

管理员：

```text
authorize FORCE_LOGOUT operation
 ↓
load session
 ↓
session.forceLogout()
 ↓
persist
 ↓
remove Redis session
 ↓
audit
```

---

# 53. Change Password Command

流程：

```text
verify current credential
 ↓
validate password policy
 ↓
write new hash
 ↓
tokenVersion++
 ↓
revoke other sessions
 ↓
outbox PasswordChanged
 ↓
audit
```

---

# 54. TemporaryGrant Aggregate

Command：

```text
CreateTemporaryGrantCommand
RevokeTemporaryGrantCommand
ExpireTemporaryGrantCommand
```

规则：

```text
expireTime required
grant cannot exceed administrator/grantor rules
```

到期安全：

```text
runtime time check
```

Job 只做收敛。

---

# 55. SecurityEvent Aggregate

状态：

```text
OPEN
ACKNOWLEDGED
RESOLVED
FALSE_POSITIVE
```

Domain methods：

```text
acknowledge()
resolve()
markFalsePositive()
```

---

# 56. Security Event Application Service

Commands：

```text
AcknowledgeSecurityEventCommand
ResolveSecurityEventCommand
MarkSecurityEventFalsePositiveCommand
```

Queries：

```text
PageSecurityEventsQuery
GetSecurityEventDetailQuery
```

---

# 57. Audit Application Model

Audit Log 大多：

```text
Append-only record
```

不需要复杂 Aggregate。

使用：

```text
AuditIngestApplicationService
```

Consumer：

```text
event
→ sanitize
→ append audit
```

---

# 58. Permission Change Audit

由 Source Service 产生可靠事实事件：

```text
before
after
operator
trace
version before/after
```

Audit Service：

```text
只持久化
```

不能重新推断业务变化。

---

# 59. Query Application Services

推荐：

```text
UserQueryService
RoleQueryService
TeamQueryService
ResourceQueryService
ShareQueryService
AuditQueryService
SecurityQueryService
AuthorizationExplainQueryService
```

---

# 60. User Detail Query

组合：

```text
User basic
Roles
Teams
Sessions summary
Permission source summary
```

允许：

```text
多个 Query Port
```

但必须 Batch。

---

# 61. Role Detail Query

返回：

```text
Role
Permission Matrix
Data Scope
Field Policies
Members count
Version
```

适合 React 管理页。

---

# 62. Team Detail Query

返回：

```text
Team metadata
Members
Team Roles
Shared resources summary
```

---

# 63. Resource Detail Query

返回：

```text
Resource
Operations
API mappings
Data schema
Fields
Sharing policy
```

---

# 64. Shared With Me Query

不能：

```text
实时跨服务 join
```

依赖：

```text
Sharing Query Model
+
Authorization subject memberships
```

可通过：

```text
local read model
```

实现。

---

# 65. Authorization Context Query

`GET /me/authorization-context`

返回：

```text
session summary
tenant context
navigation version
permission version
acting team
```

不返回：

```text
全部 permission strings
```

---

# 66. Command Validation 层级

分三层：

```text
DTO validation
Application validation
Domain invariant
```

DTO：

```text
格式
长度
必填
```

Application：

```text
关联对象存在
跨 Aggregate 前置校验
```

Domain：

```text
状态机
业务不变量
```

---

# 67. Authorization 前置检查

Admin Command：

例如：

```text
UpdateRolePermissionsCommand
```

必须先：

```text
Authorization.requireCurrentOperation()
```

但不要把：

```text
role:update
```

字符串写入代码。

当前 Operation：

```text
由 Gateway/API Mapping Context 提供
```

---

# 68. Instance Authorization

例如 Revoke Share：

```text
requireInstance(
 resourceId = IAM_RESOURCE_SHARE,
 instanceKey = shareId
)
```

ResourceId/OperationId：

```text
来自当前动态 API mapping/context
```

业务代码不硬编码业务 permission code。

---

# 69. Application Transaction

默认：

```text
Application command method
```

开启本地事务。

Query：

```text
read-only transaction
```

或无事务，按一致性需求。

---

# 70. Remote Call 与事务

禁止：

```text
@Transactional
public void createShare() {
  remoteCall();
  lockRow();
  remoteCall();
  save();
}
```

推荐：

```text
remote validation
 ↓
open short local transaction
 ↓
CAS/save/outbox
```

---

# 71. Domain Event Collection

Aggregate 内产生：

```text
DomainEvent
```

Application：

```text
collect
 ↓
serialize to Outbox
```

Domain 不直接调用：

```text
RabbitTemplate
```

---

# 72. Event 产生时机

例如：

```text
share.revoke()
```

Domain 产生：

```text
ResourceShareRevokedDomainEvent
```

只有状态真正变化时产生。

---

# 73. Outbox Event Mapping

Domain Event：

```text
内部领域对象
```

Outbox Event：

```text
Integration Event
```

两者可以分开。

由：

```text
DomainEventMapper
```

转换。

---

# 74. 为什么区分 Domain / Integration Event

Domain：

```text
面向当前 Bounded Context
```

Integration：

```text
面向外部 Consumer
需要版本化
需要兼容
```

避免 Domain 内部重构直接破坏 MQ Contract。

---

# 75. Command Idempotency

以下 Command 必须支持：

```text
BootstrapTenant
CreateUser
CreateShare
Reshare
Grant/Permission replace-set
```

Application Service 不关心 HTTP Header。

Interfaces 层：

```text
IdempotencyContext
```

传入 Application。

---

# 76. Replace-Set Command 幂等

例如：

```text
AssignUserRoles({1,2,3})
```

重复执行：

```text
最终状态相同
```

如果无 diff：

```text
不增加 Version
不发 Change Event
```

---

# 77. Command Result

Command 返回：

```text
业务结果
```

例如：

```text
CreateUserResult
CreateShareResult
```

不要直接返回：

```text
DO/Entity
```

---

# 78. Query Result

Query 返回：

```text
Read Model
```

再由 REST：

```text
Response DTO
```

映射。

---

# 79. Error Mapping

Domain Error：

```text
ShareAlreadyRevoked
PermissionEscalation
InvalidStateTransition
```

Application：

映射到：

```text
Business Error Code
```

Interfaces：

映射：

```text
HTTP
```

---

# 80. Domain Exception 原则

Domain Exception：

```text
不包含 HTTP Status
```

避免 Domain 依赖 Web。

---

# 81. Application Error Code

例如：

```text
IAM_SHARE_OPERATION_ESCALATION
IAM_VERSION_CONFLICT
IAM_AUTHZ_DENIED
```

属于：

```text
Application/API contract
```

---

# 82. Unit of Work

V1 不引入额外 UoW 框架。

使用：

```text
Spring Transaction
```

作为技术实现。

Domain 不感知事务。

---

# 83. Aggregate Repository Save

`save(aggregate)`：

Infrastructure：

```text
insert/update header
sync child relations
optimistic version
```

如果 child 数量可能巨大：

```text
不应该放在 Aggregate
```

---

# 84. ResourceShare Child Collection

Operations/Fields 数量通常有限：

```text
适合 Aggregate
```

因此 Create/Revoke/Reshare 可完整加载。

---

# 85. RolePermission 大集合

Role 可能大量 Permission：

```text
不作为 Role Aggregate child collection
```

用：

```text
RolePermissionBinding
+
Application bulk command
```

---

# 86. Team Members 大集合

Team Aggregate 不加载所有 Member。

Membership：

```text
独立关系
```

---

# 87. Session 集合

User Aggregate 不加载所有 Session。

Auth Service：

```text
SessionRepository
```

独立管理。

---

# 88. Application Service Interactions

禁止：

```text
Application Service A
直接调用
Application Service B
```

形成复杂内部网。

同 Bounded Context 可调用：

```text
Domain Service
Repository
Policy
```

跨 Use Case 重用：

```text
提取明确 Domain/Application component
```

---

# 89. Internal Facade

跨服务暴露：

```text
Internal Facade
```

例如：

```text
AuthorizationInternalFacade
IdentityAuthenticationFacade
```

而不是暴露任意 Application Service。

---

# 90. Authorization Internal Facade

提供：

```text
check
batchCheck
grantablePermissions
```

不提供：

```text
generic SQL-like permission query
```

---

# 91. Sharing Internal Facade

可提供：

```text
reconcile
resourceDeleted
ownershipChanged
```

仅内部服务身份可调用。

---

# 92. Job Adapter

PowerJob Processor：

```text
ExpireShareProcessor
```

仅：

```text
parse job args
call application
record outcome
```

不实现：

```text
share.status='EXPIRED'
```

---

# 93. MQ Consumer Adapter

例如：

```text
TeamMemberChangedConsumer
```

仅：

```text
deserialize
schema/version check
dedup
call projection application service
ACK
```

---

# 94. Projection Application Service

例如：

```text
AuthorizationProjectionApplicationService
AclProjectionApplicationService
```

负责：

```text
event version
projection update
checkpoint
consume record
```

同本地事务。

---

# 95. Cache Application Service

缓存失效不要散落：

```text
redis.delete(...)
```

推荐：

```text
AuthorizationCacheCoordinator
ApiMappingCacheCoordinator
```

统一：

```text
version
L1
L2
```

---

# 96. Permission Version Coordinator

权限修改后：

```text
PermissionVersionCoordinator.increment(...)
```

由 Authorization Application Service 调用。

它是：

```text
Application/Domain infrastructure abstraction
```

不是到处直接更新表。

---

# 97. Security Audit Hook

高风险 Command：

```text
必须生成 reliable audit fact
```

推荐：

```text
AuditFactFactory
```

产生：

```text
before
after
operator
trace
risk
```

进入 Outbox。

---

# 98. Business Clock

所有 Application Service 注入：

```text
Clock
```

统一生成：

```text
Instant now
```

同一 Command：

```text
只取一次
```

避免边界差异。

---

# 99. Actor Context

Command 不让客户端传：

```text
operatorUserId
tenantId
```

可信值来自：

```text
ActorContext
TenantContext
```

Command DTO 只包含业务输入。

---

# 100. Service Identity Command

系统 Job / Internal Service：

```text
ActorType = SERVICE / SYSTEM_JOB
```

Audit：

```text
区分 USER/SERVICE/SYSTEM_JOB
```

---

# 101. Bulk Command

例如：

```text
BulkAssignTeamMembers
```

大规模任务：

```text
Async Job
```

Application：

```text
split batches
```

每批独立事务。

---

# 102. Application Metrics

每个关键 Command：

```text
duration
success/failure
conflict
retry
```

授权：

```text
decision
cache level
latency
```

---

# 103. Command Logging

结构化日志：

```text
commandType
aggregateId
traceId
result
```

禁止：

```text
打印完整 request body
```

尤其敏感字段。

---

# 104. Application Test Strategy

每个 Command 至少：

```text
happy path
permission denied
invalid state
optimistic conflict
idempotent retry
repository failure
```

适用时再加：

```text
outbox failure
```

---

# 105. Domain Aggregate Test

纯 Java：

```text
no Spring
```

覆盖：

```text
state transition
invariant
event generation
```

---

# 106. Application Integration Test

Testcontainers：

```text
real MySQL
Redis/MQ where needed
```

验证：

```text
transaction
outbox
version
repository
```

---

# 107. Business Closed Loop Mapping

RBAC：

```text
CreateUserCommand
CreateRoleCommand
UpdateRolePermissionsCommand
AssignUserRolesCommand
Authorize
Revoke RolePermission
Authorize DENY
```

---

# 108. Team Closed Loop Mapping

```text
CreateTeamCommand
AddTeamMemberCommand
CreateTeamRoleCommand
UpdateTeamRolePermissionsCommand
AssignTeamMemberRolesCommand
Authorize
RemoveTeamMemberCommand
Authorize DENY
```

---

# 109. Data Permission Closed Loop

```text
UpdateRoleDataScopeCommand
Authorize
DataPermissionPlan
MyBatis SQL
```

---

# 110. Field Permission Closed Loop

```text
UpdateRoleFieldPoliciesCommand
Authorize
FieldPermissionPlan
Request Field Guard
Response Filter
```

---

# 111. Share Closed Loop

```text
CreateResourceShareCommand
Projection
Authorize ALLOW
RevokeResourceShareCommand
Version/Epoch
Authorize DENY
```

---

# 112. Authentication Closed Loop

```text
LoginCommand
RefreshAccessTokenCommand
ForceLogoutCommand
old access token DENY
```

---

# 113. Audit Closed Loop

```text
Source Command
Audit Fact
Outbox
Audit Consumer
Audit Query
Explain Trace
```

---

# 114. 第一阶段 Java 类生成清单

CODE PHASE 第一闭环至少：

```text
TenantApplicationService
UserApplicationService
RoleApplicationService
AuthenticationApplicationService
AuthorizationApplicationService

Tenant
User
RolePermissionBinding
LoginSession
RefreshToken

TenantRepository
UserRepository
RoleRepository
UserRoleRepository
RolePermissionRepository
PermissionVersionRepository
SessionRepository
RefreshTokenRepository
```

---

# 115. 第二阶段生成

Team：

```text
TeamApplicationService
TeamRoleApplicationService
Team
TeamRole
TeamMemberRepository
TeamMemberRoleRepository
```

---

# 116. 第三阶段生成

Authorization Advanced：

```text
DataPermissionPlanner
FieldPermissionPlanner
ConditionEvaluator
GrantResolvers
```

---

# 117. 第四阶段生成

Sharing：

```text
ResourceShareApplicationService
ResourceShare
SharingPolicy
ResourceShareRepository
ShareProjectionEpochRepository
```

---

# 118. 第五阶段生成

Consistency：

```text
IdempotencyCoordinator
OutboxApplicationService
ProjectionApplicationService
JobApplicationService
```

---

# 119. Code Review Gate

如果出现：

```text
Controller 直接 Repository
Application 直接 Mapper
Aggregate 依赖 Spring/MyBatis
Domain Event 直接 RabbitMQ
Command 接收 tenantId/userId from client
状态通过 setStatus 直接修改
```

必须：

```text
Reject
```

---

# 120. SPEC 30 冻结结论

V1.0 Java 业务实现正式采用：

```text
Command/Query Separation
+
Application Orchestration
+
Small Aggregates
+
Domain State Machines
+
Repository Ports
+
Outbox Integration Events
+
Explicit Transaction Boundaries
+
Trusted Actor/Tenant Context
+
No Business Permission Hardcoding
```

后续生成 Java 类时，必须从：

```text
Use Case
```

出发，而不是从：

```text
Database Table
```

出发。

一个类是否应该存在，首先回答：

```text
它服务哪个业务闭环？
它承担 Application、Domain 还是 Infrastructure 职责？
它的事务边界在哪里？
它产生哪些事实事件？
```

如果回答不清楚，就不进入代码。
