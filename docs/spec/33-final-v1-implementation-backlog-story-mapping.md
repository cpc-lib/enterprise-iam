# Enterprise IAM & Dynamic Authorization Platform
## 33 — Final V1.0 Implementation Backlog & Story Mapping SPEC 1.0

> 本文将 SPEC 01~32 转换为真正可执行的一人 + AI 开发 Backlog。
>
> 目标：
>
> ```text
> Architecture
> → Epic
> → Story
> → Task
> → Acceptance Criteria
> → Test
> → Release Gate
> ```
>
> 从本 SPEC 开始，项目不再继续扩展设计范围，除非发现 P0/P1 安全或架构缺陷。

---

# 1. Backlog 总原则

正式采用：

```text
Vertical Slice First
Security First
Backend First
Test Alongside Code
No Big-Bang Generation
```

每个 Story：

```text
0.5 ~ 2 天
```

每个 Epic：

```text
1 ~ 3 周
```

一人同时：

```text
DOING <= 2
```

---

# 2. Epic 总览

V1.0 共 14 个 Epic：

```text
E01 Repository & Build Foundation
E02 Infrastructure & Runtime
E03 Tenant / User / Identity
E04 Authentication & Session
E05 Resource / Operation / API Mapping
E06 RBAC Authorization Engine
E07 Organization / Team / Team Role
E08 Data Permission
E09 Field Permission
E10 Cross-Team Sharing & ACL
E11 Idempotency / Outbox / MQ / Job
E12 Audit / Explain / Security Center
E13 React Administration Console
E14 E2E / Security / Performance / Release
```

---

## Final Consistency Addendum - E15 Backlog Extension

SPEC 36 §28 已将 Backlog 冻结为 E01~E15，新增：

```text
E15 Enterprise File Management
```

Story 清单冻结于 SPEC 34 §134（S180~S197）与 SPEC 35 §113（S198~S204），
V1/V1.1 范围边界见 SPEC 36 §19 与 SPEC 35 §114。
执行版 Backlog 见 docs/planning/V1-BACKLOG.csv。

本 Addendum 仅回写既有冻结事实，取代本文件"V1.0 共 14 个 Epic"的早期措辞，不改变任何功能。

---

# 3. 里程碑映射

```text
M0 Foundation Ready           Week 1~4
M1 Auth Closed Loop           Week 5~6
M2 RBAC Closed Loop           Week 7~9
M3 Data/Field Closed Loop     Week 10~12
M4 Share Closed Loop          Week 13~14
M5 Consistency Beta           Week 15~16
M6 React Beta                 Week 17~19
M7 Audit/Explain              Week 20
M8 Release Candidate          Week 21~23
M9 V1.0                       Week 24
```

---

# 4. E01 — Repository & Build Foundation

目标：

```text
Monorepo 可构建
模块边界固定
CI 可执行
```

Stories：

```text
S01 Root Maven Reactor
S02 iam-dependencies BOM
S03 framework parent/modules
S04 service module skeletons
S05 ArchUnit baseline
S06 code style / CI baseline
```

---

# 5. S01 — Root Maven Reactor

Tasks：

```text
创建 backend/pom.xml
注册所有 modules
统一 Java 21
统一 encoding
统一 Maven plugin management
```

Acceptance：

```text
mvn clean verify
```

在无业务代码情况下：

```text
BUILD SUCCESS
```

---

# 6. S02 — Dependency BOM

实现：

```text
iam-dependencies
```

管理：

```text
Spring Boot
Spring Cloud
Spring Cloud Alibaba
MyBatis-Plus
JSqlParser
Flyway
Redisson
RabbitMQ
Seata Client
PowerJob Worker
Testcontainers
ArchUnit
```

Acceptance：

```text
业务 module 不自行声明核心版本号
```

---

# 7. S03 — Framework Modules

创建：

```text
iam-common-core
iam-common-web
iam-common-tenant
iam-common-security
iam-common-mybatis
iam-common-redis
iam-common-mq
iam-common-observability
```

第一阶段只做骨架。

不要一次实现所有 Starter。

---

# 8. S04 — Service Skeletons

创建：

```text
gateway
auth
identity
organization
authorization
sharing
audit
job
```

Acceptance：

```text
所有 module compile
```

---

# 9. S05 — ArchUnit Baseline

必须阻止：

```text
domain → infrastructure
domain → Spring MVC
domain → MyBatis
controller → mapper
application → mapper
```

---

# 10. E02 — Infrastructure & Runtime

目标：

```text
Windows 开发环境一键启动
```

Stories：

```text
S10 MySQL
S11 Redis
S12 RabbitMQ
S13 Nacos
S14 PowerJob
S15 MinIO
S16 Seata optional
S17 PowerShell scripts
S18 Health checks
```

---

# 11. S10 — MySQL Infrastructure

交付：

```text
docker compose
multi-database init
iam_auth
iam_identity
iam_organization
iam_authorization
iam_sharing
iam_audit
iam_job
```

Acceptance：

```text
container healthy
all databases created
```

---

# 12. S11 — Redis Infrastructure

交付：

```text
password
healthcheck
namespace conventions
```

Acceptance：

```text
Spring app ping success
```

---

# 13. S12 — RabbitMQ Infrastructure

交付：

```text
iam vhost
domain exchange
audit exchange
DLX
management UI
```

Acceptance：

```text
publish/consume smoke
```

---

# 14. S13 — Nacos Infrastructure

交付：

```text
service discovery
shared config structure
```

Acceptance：

```text
demo service registers
```

---

# 15. S14 — PowerJob

目标：

```text
job worker can register
```

先不实现业务 Job。

---

# 16. S15 — MinIO

目标：

```text
private bucket
presigned URL smoke
```

---

# 17. S17 — Windows Scripts

必须提供：

```text
start-infra.ps1
stop-infra.ps1
health-check.ps1
reset-dev.ps1
```

---

# 18. E03 — Tenant / User / Identity

目标：

```text
身份域闭环
```

Stories：

```text
S20 Flyway identity
S21 Tenant aggregate/repository
S22 User aggregate/repository
S23 User identity lookup
S24 Role
S25 UserRole
S26 Bootstrap tenant
S27 User management API
```

---

# 19. S20 — Identity Flyway

实现：

```text
iam_tenant
iam_user
iam_user_identity
iam_role
iam_user_role
sys_outbox_event
sys_idempotency_record
sys_message_consume_record
```

Acceptance：

```text
empty DB migrate success
```

---

# 20. S21 — Tenant

实现：

```text
Tenant Aggregate
TenantRepository
TenantApplicationService
```

状态机：

```text
INITIALIZING
ACTIVE
SUSPENDED
DISABLED
```

测试：

```text
illegal transition denied
```

---

# 21. S22 — User

实现：

```text
User Aggregate
UserRepository
UserQueryService
```

Acceptance：

```text
tenant isolation
username unique
optimistic version
```

---

# 22. S23 — Authentication Identity View

Identity 提供：

```text
IdentityAuthenticationView
```

一次查询返回：

```text
userId
credential metadata
user status
tenant status
```

供 Auth 登录使用。

---

# 23. S24 — Role

实现：

```text
Role Aggregate
RoleRepository
Role APIs
```

暂不实现 Permission。

---

# 24. S25 — UserRole

实现：

```text
assign roles replace-set
diff
version
outbox
```

Acceptance：

```text
duplicate assignment idempotent
```

---

# 25. S26 — Tenant Bootstrap

实现：

```text
BootstrapTenantCommand
Saga state
initial admin
root org placeholder
bootstrap role placeholder
```

第一阶段可以逐步补齐 Saga。

---

# 26. E04 — Authentication & Session

Stories：

```text
S30 Auth Flyway
S31 Password hashing
S32 Login
S33 Session Redis
S34 Access JWT
S35 Refresh rotation
S36 Logout
S37 Force logout
S38 Disable user session invalidation
```

---

# 27. S31 — Credential Security

实现：

```text
Argon2id / BCrypt
password policy
no plaintext persistence
```

---

# 28. S32 — Login

闭环：

```text
tenant
identity
credential
user status
session
refresh
JWT
audit event
```

Acceptance：

```text
valid login works
invalid credentials generic
disabled user denied
```

---

# 29. S33 — Session Redis

实现：

```text
iam:session:{sessionId}
```

Redis 缺失：

```text
Fail Closed
```

---

# 30. S34 — JWT

只包含：

```text
tenantId
userId
sessionId
tokenVersion
jti
iat
exp
```

禁止：

```text
role
permission
team
```

---

# 31. S35 — Refresh Rotation

实现：

```text
RT1 ACTIVE → ROTATED
RT2 ACTIVE
```

并发测试：

```text
100 concurrent refresh
one valid successor
```

---

# 32. S36 — Logout

实现：

```text
session revoke
Redis invalidate
refresh family revoke
```

重复：

```text
idempotent
```

---

# 33. E05 — Resource / Operation / API Mapping

Stories：

```text
S40 Authorization Flyway resource model
S41 Application metadata
S42 Service metadata
S43 Resource
S44 Operation
S45 ResourceOperation
S46 API discovery
S47 API mapping
S48 Gateway mapping cache
```

---

# 34. S43 — Resource

实现：

```text
Resource Aggregate
ResourceRepository
CRUD
disable hard guard
```

---

# 35. S44 — Operation

动态：

```text
operationCode
name
risk
status
```

禁止 Java 权限 enum。

---

# 36. S46 — API Discovery

Starter：

```text
scan RequestMappingHandlerMapping
register endpoints
mark stale
```

Acceptance：

```text
repeat discovery no duplicates
```

---

# 37. S47 — API Mapping

实现：

```text
API → Resource + Operation + SecurityPolicy
```

未知 API：

```text
DENY
```

---

# 38. E06 — RBAC Authorization Engine

Stories：

```text
S50 Permission definition
S51 RolePermission binding
S52 PermissionVersion
S53 Subject read model
S54 Grant resolvers
S55 Merge engine
S56 Authorization check
S57 Batch authorization
S58 Explain basic
S59 Immediate revoke test
```

---

# 39. S50 — Permission Definition

```text
Resource + Operation
→ Permission
```

唯一：

```text
tenant/resource/operation
```

---

# 40. S51 — RolePermission Binding

字段：

```text
effect
priority
condition
start/expire
status
version
```

---

# 41. S52 — PermissionVersion

实现：

```text
atomic increment
batch query
effective version
```

这是立即撤权核心。

---

# 42. S53 — Subject Read Model

包含：

```text
user status
role ids
team ids placeholder
version vector
```

先完成 Role。

---

# 43. S54 — Grant Resolvers

第一阶段：

```text
UserRoleGrantResolver
DirectGrantResolver
TemporaryGrantResolver
```

Team/Share 后续接入。

---

# 44. S55 — Merge Engine

纯 Java。

必须 TDD：

```text
no grant deny
allow
deny
same priority deny wins
higher priority wins
expired ignored
hard guard wins
```

---

# 45. S56 — Authorization Check

实现：

```text
authorize()
```

返回：

```text
ALLOW/DENY
decisionCode
version
```

---

# 46. S59 — RBAC Closed Loop

Golden E2E：

```text
Create user
Create role
Create resource/op
Grant role
Assign user
Login
Call protected API → ALLOW
Revoke permission
Call again → DENY
No relogin
```

未通过：

```text
不进入 Data Permission
```

---

# 47. E07 — Organization / Team / Team Role

Stories：

```text
S60 Organization Flyway
S61 Organization tree
S62 Team
S63 Team member
S64 Team role
S65 Team member role
S66 TeamRolePermission
S67 Team projection
S68 Team closed loop
```

---

# 48. S68 — Team Closed Loop

```text
Join Team
Assign TeamRole
Grant TeamRolePermission
ALLOW
Remove member
Immediate DENY
```

---

# 49. E08 — Data Permission

Stories：

```text
S70 DataScope schema
S71 ResourceDataSchema
S72 DataPermissionPlan
S73 MyBatis interceptor
S74 SELF
S75 TEAM
S76 TEAM_AND_CHILDREN
S77 SPECIFIED_TEAM
S78 SHARED placeholder
S79 UPDATE/DELETE protection
S80 Data permission E2E
```

---

# 50. S73 — SQL Interceptor

技术：

```text
JSqlParser AST
```

顺序：

```text
Tenant
DataPermission
Pagination
```

Parser failure：

```text
Fail Closed
```

---

# 51. S80 — Data Permission Closed Loop

```text
Team A sees C1
cannot see C2
count consistent
detail denied
update denied
scope expanded
next request sees new scope
```

---

# 52. E09 — Field Permission

Stories：

```text
S81 Resource field metadata
S82 Mask strategy
S83 Field policy binding
S84 Request field presence
S85 Write guard
S86 MyBatis SET guard
S87 Response filter
S88 Dynamic React form integration
S89 Field closed loop
```

---

# 53. S89 — Field Closed Loop

```text
phone MASK
salary HIDDEN
salary WRITE deny
manual PATCH salary → 403
DB unchanged
```

---

# 54. E10 — Cross-Team Sharing & ACL

Stories：

```text
S90 Sharing Flyway
S91 Sharing policy
S92 ResourceShare aggregate
S93 Create share
S94 Revoke share
S95 Reshare
S96 Share epoch
S97 MQ projection
S98 Business ACL projection
S99 Shared DataScope integration
S100 Share expiry job
S101 Share closed loop
```

---

# 55. S92 — ResourceShare Aggregate

必须 TDD：

```text
start/expire
operation subset
field subset
canReshare
depth
parent
revoke
expire
```

---

# 56. S96 — Projection Epoch

实现：

```text
share projection epoch
expected epoch
business checkpoint
```

这是撤权安全第二防线。

---

# 57. S101 — Share Closed Loop

```text
Team A no access
Team B shares READ
Team A ALLOW
Field constrained
Revoke
next request DENY
Projection delayed still DENY
```

---

# 58. E11 — Idempotency / Outbox / MQ / Job

Stories：

```text
S110 HTTP idempotency starter
S111 Canonical request hash
S112 Lease recovery
S113 Outbox starter
S114 Outbox relay
S115 MQ consumer dedup
S116 Retry/DLQ
S117 Event version
S118 PowerJob adapters
S119 Reconcile jobs
S120 Failure recovery tests
```

---

# 59. S110 — HTTP Idempotency

实现：

```text
Idempotency-Key
PROCESSING
SUCCESS
RETRYABLE_FAILED
FINAL_FAILED
```

测试：

```text
same key same request replay
same key diff request 409
100 concurrent only one effect
```

---

# 60. S113 — Outbox

本地事务：

```text
business
+
outbox
```

必须：

```text
atomic
```

---

# 61. S114 — Relay

实现：

```text
SKIP LOCKED claim
publish confirm
mandatory return
retry
dead
```

---

# 62. S120 — Failure Recovery

至少：

```text
RabbitMQ down
MQ duplicate
MQ out-of-order
consumer crash
PowerJob down
```

---

# 63. E12 — Audit / Explain / Security Center

Stories：

```text
S121 Audit Flyway
S122 Login audit
S123 Admin audit
S124 Permission change audit
S125 Authorization audit
S126 Security event
S127 Explain full
S128 Simulator
S129 Security Center APIs
S130 Audit React pages
```

---

# 64. S127 — Explain

必须复用：

```text
same AuthorizationEngine
```

输出：

```text
hard guards
grants
winning tier
data scope
field policy
decision
```

---

# 65. E13 — React Administration Console

Stories：

```text
S140 React project bootstrap
S141 Axios client
S142 Auth store
S143 Refresh single-flight
S144 Dynamic navigation
S145 Dynamic routes
S146 Permission guard
S147 User pages
S148 Role pages
S149 Team pages
S150 Resource/Operation
S151 Permission Matrix
S152 DataScope UI
S153 FieldPolicy UI
S154 Share UI
S155 Explain UI
S156 Audit UI
S157 Security UI
```

---

# 66. React 顺序

不能先把所有页面做完。

顺序：

```text
Login
→ User
→ Role
→ Resource/Operation
→ Permission Matrix
→ Team
→ Data
→ Field
→ Share
→ Explain/Audit
```

与后端闭环一致。

---

# 67. S143 — Refresh Single Flight

20 concurrent 401：

```text
1 refresh request
19 wait
```

refresh 失败：

```text
clear auth
clear permission
redirect login
```

---

# 68. S146 — Permission Guard

允许：

```text
controlId
operationId from server schema
```

禁止：

```text
role === 'ADMIN'
permission === 'customer:update'
```

---

# 69. E14 — E2E / Security / Performance / Release

Stories：

```text
S160 Testcontainers integration suite
S161 Playwright business loops
S162 Security abuse suite
S163 Performance smoke
S164 Chaos/failure suite
S165 Docker full compose
S166 Backup restore
S167 Runbooks/dashboard
S168 Release checklist
S169 RC1
S170 V1.0
```

---

# 70. S161 — Playwright Golden Loops

必须：

```text
tenant bootstrap
rbac loop
team loop
data loop
field loop
share loop
auth session loop
audit explain loop
```

---

# 71. S162 — Security Abuse Suite

Release blocking：

```text
tenant spoof
user spoof
JWT forge
cross tenant
same tenant IDOR
field mass assignment
share escalation
stale ACL
internal API
fail-open
refresh reuse
```

---

# 72. S163 — Performance Smoke

至少：

```text
Authorization L1
Redis
DB fallback
Data Scope SQL
ACL
Audit cursor
```

---

# 73. S165 — Full Compose

必须可以：

```text
docker compose up -d
```

启动：

```text
infra
gateway
all services
React
```

---

# 74. S166 — Backup Restore

验证：

```text
backup
new DB
restore
Flyway validate
smoke
```

---

# 75. Story Definition of Ready

Story 进入 READY 前必须：

```text
SPEC reference
Goal
Dependencies
Security constraints
Acceptance
Test strategy
```

---

# 76. Story Definition of Done

必须：

```text
Code
Unit test
Integration test
Error code
Audit if required
Metric if required
Docs updated
No architecture violation
```

---

# 77. AI Task Contract

每次 AI 任务：

```text
SPEC:
Story:
Goal:
Allowed modules:
Forbidden changes:
Security invariants:
Expected files:
Acceptance tests:
```

---

# 78. AI Generation Rule

一次最多生成：

```text
一个 Story
或
一个高度内聚子任务
```

禁止：

```text
一次生成完整微服务
```

---

# 79. AI Review Rule

所有核心代码必须让 AI Review：

```text
auth bypass
tenant leak
state race
idempotency
transaction
outbox
N+1
hardcoded permission
test gap
```

但开发者最终决定是否合并。

---

# 80. 每周容量

一人全职：

```text
5~8 Story Points / week
```

这里 Story Point 只是团队内部相对复杂度。

不要与工时直接等价。

---

# 81. Story Point 基线

```text
1 = < 0.5 day
2 = ~1 day
3 = 1~2 days
5 = 2~3 days
8 = 必须拆分
```

8 分以上：

```text
禁止直接进入开发
```

---

# 82. Backlog 优先级

```text
P0 Security/Correctness
P1 Vertical Slice Dependency
P2 Operational Completeness
P3 UX Polish
P4 Future Enhancement
```

---

# 83. Dependency Rule

如果 Story B 依赖 Story A：

```text
B 不提前实现假逻辑
```

允许：

```text
temporary mock
```

但必须有：

```text
remove mock story
```

和截止点。

---

# 84. Mock 截止

正式：

```text
Gateway Authz Mock
→ Week 9 前移除

SHARED ACL Mock
→ Week 14 前移除

Audit Mock
→ Week 20 前移除
```

---

# 85. 技术债 Backlog

单独维护：

```text
TECH-DEBT
```

来源：

```text
temporary mock
unsafe shortcut
missing test
performance workaround
```

每周五清理。

---

# 86. Bug Backlog

P0：

```text
立即停止新功能
```

P1：

```text
当前周修复
```

P2：

```text
下一 Sprint
```

P3：

```text
可排后
```

---

# 87. 24 周执行映射

## Week 1

```text
E01
```

## Week 2~4

```text
E02 + framework
```

## Week 5~6

```text
E03 + E04
```

## Week 7~9

```text
E05 + E06
```

## Week 10~12

```text
E08 + E09
```

## Week 13~14

```text
E10
```

## Week 15~16

```text
E11
```

## Week 17~19

```text
E13
```

## Week 20

```text
E12
```

## Week 21~23

```text
E14
```

## Week 24

```text
V1.0 stabilization
```

---

# 88. 每周必做

每周必须至少：

```text
1 Vertical Slice Demo
1 Integration Test
1 Architecture Review
1 Security Regression subset
1 README/SPEC update
```

---

# 89. 每周五 Gate

必须：

```text
main green
no P0
no known cross-tenant leak
critical tests green
```

否则：

```text
下周不开始新 Epic
```

---

# 90. MVP Exit Criteria

MVP 不是页面多。

必须：

```text
login
user
role
resource
operation
dynamic mapping
RBAC
basic data
basic field
React admin basic
docker infra
```

核心：

```text
grant → allow → revoke → deny
```

---

# 91. Beta Exit Criteria

必须：

```text
Team
Data
Field
Share
Idempotency
Outbox
MQ
Job
```

安全测试：

```text
green
```

---

# 92. RC Exit Criteria

必须：

```text
Playwright
Security Abuse
Performance Smoke
Docker Full
Backup Restore
Runbooks
No Critical CVE
```

---

# 93. V1.0 Exit Criteria

必须：

```text
all release blocking scenarios pass
no P0/P1
docs complete
demo data complete
full compose works
tag created
```

---

# 94. 第一批代码任务

真正进入 CODE 后，第一批：

```text
TASK-001 Root Maven
TASK-002 Dependency BOM
TASK-003 Common Core/Web/Tenant
TASK-004 Docker MySQL/Redis/Rabbit/Nacos
TASK-005 Identity Flyway V1
TASK-006 User/Tenant Domain
TASK-007 Auth Flyway V1
TASK-008 Login Skeleton
```

---

# 95. 第一条业务闭环

CODE Phase 第一目标不是：

```text
完成所有 Framework
```

而是：

```text
Tenant
→ User
→ Role
→ Resource
→ Operation
→ RolePermission
→ Login
→ Gateway
→ Authorization
→ Demo API
→ Revoke
```

---

# 96. 第一条闭环验收

必须看到：

```text
Alice initially DENY
Grant RolePermission
Alice ALLOW
Revoke RolePermission
Alice next request DENY
No relogin
Audit trace exists
```

这条通过：

```text
Architecture Proven
```

---

# 97. 第二条闭环

```text
Team
→ TeamRole
→ TeamRolePermission
→ ALLOW
→ Remove TeamMember
→ DENY
```

---

# 98. 第三条闭环

```text
Data Scope
→ SQL filter
→ list/detail/update
```

---

# 99. 第四条闭环

```text
Field policy
→ response mask
→ malicious write deny
```

---

# 100. 第五条闭环

```text
Cross-Team Share
→ ACL Projection
→ ALLOW
→ Revoke
→ Immediate DENY
```

---

# 101. Final Scope Lock

SPEC 33 后：

V1.0 不再加入：

```text
WebAuthn full
Flowable approval
ClickHouse
Kubernetes Helm
ABAC visual builder
Service Mesh
Multi-region
Folder inheritance
```

这些统一：

```text
V1.1 / V2 backlog
```

---

# 102. 进入代码阶段条件

满足：

```text
SPEC 01~33 frozen
Repository tree frozen
Database ownership frozen
API contract frozen
Threat model frozen
Backlog frozen
```

即可：

```text
START CODE
```

---

# 103. SPEC 33 结论

从本 SPEC 起：

```text
不再按“模块完成率”
```

推进。

只按：

```text
Closed Loop
Story Done
Release Gate
```

推进。

项目开发核心节奏：

```text
SPEC
→ Story
→ Test
→ Code
→ Integration
→ Security
→ Demo
→ Merge
```

这就是 Enterprise IAM V1.0 的最终实施 Backlog 基线。
