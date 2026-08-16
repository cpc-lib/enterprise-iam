# Enterprise IAM & Dynamic Authorization Platform
## 25 — Business Closed Loop & End-to-End Workflow SPEC 1.0

> 本文不新增新的技术栈，而是把 SPEC 01~24 的认证、身份、组织、授权、数据权限、字段权限、跨团队分享、审计、幂等、消息、任务、部署能力串成完整业务闭环。
>
> V1.0 的验收标准不再是“每个模块都有 CRUD”，而是：
>
> **从租户初始化 → 用户入职 → 权限配置 → 业务 API 上线 → 用户访问 → 跨团队协作 → 撤权/到期 → 审计追溯 → 故障恢复，整个生命周期能够闭环。**

---

# 1. 业务闭环总览

平台必须形成以下主闭环：

```text
Tenant Bootstrap
      ↓
User / Identity
      ↓
Organization / Team
      ↓
Role / Team Role
      ↓
Application / Resource / Operation
      ↓
API Discovery / Mapping
      ↓
Permission Grant
      ↓
Authentication
      ↓
Authorization Decision
      ↓
Data Scope
      ↓
Field Permission
      ↓
Business Access
      ↓
Resource Share / Cross-Team ACL
      ↓
Revoke / Expire / Membership Change
      ↓
Permission Version
      ↓
Audit / Explain / Security Event
      ↓
Reconcile / Recovery
```

最终必须能够回答：

```text
谁
在什么租户
以什么身份
通过什么角色/团队/分享来源
对什么资源
执行什么 Operation
访问哪些数据
看到/修改哪些字段
为什么被允许或拒绝
谁后来撤销了权限
何时生效
何时失效
```

---

# 2. 核心业务角色

平台自身至少存在以下业务参与者：

```text
Platform Bootstrap Operator
Tenant Administrator
IAM Administrator
Organization Administrator
Team Manager
Security Administrator
Audit Viewer
Normal User
Service Identity
System Job
```

这些只是业务参与者概念。

实际权限仍由：

```text
Resource + Operation + Grant
```

动态配置。

禁止在代码中：

```text
if (role == TENANT_ADMIN)
```

直接形成安全特权。

---

# 3. Tenant 生命周期闭环

Tenant 状态：

```text
INITIALIZING
ACTIVE
SUSPENDED
DISABLED
INITIALIZATION_FAILED
```

生命周期：

```text
Create Tenant
   ↓
INITIALIZING
   ↓
Create Initial User
   ↓
Create Default Organization
   ↓
Create Initial Team
   ↓
Create Initial IAM Role
   ↓
Bind Administrator
   ↓
Bootstrap Permission Version
   ↓
ACTIVE
```

任何步骤失败：

```text
INITIALIZATION_FAILED
```

进入可重试 Saga。

---

# 4. Tenant Bootstrap 业务流程

输入：

```text
tenantName
tenantCode
initialAdminIdentity
displayName
```

流程：

```text
POST Tenant Bootstrap
 ↓
HTTP Idempotency
 ↓
Create Tenant(INITIALIZING)
 ↓
Create Initial User
 ↓
Create Root Organization
 ↓
Create Default Team
 ↓
Create Bootstrap Role
 ↓
Grant Bootstrap Operations
 ↓
Assign User Role
 ↓
PermissionVersion Initialize
 ↓
Tenant ACTIVE
 ↓
Outbox Events
 ↓
Audit
```

失败策略：

```text
不直接 DELETE 已创建数据
```

而是：

```text
Saga Compensation / Retry
```

保证历史可追踪。

---

# 5. Tenant Bootstrap 验收

完成后必须满足：

```text
管理员可以登录
管理员可以看到 IAM 管理菜单
管理员可以创建 User / Team / Role
其他 Tenant 完全不可见
Bootstrap API 不再允许重复初始化
```

相同 Idempotency-Key 重试：

```text
返回同一 Tenant
```

---

# 6. 用户入职闭环

用户生命周期：

```text
INVITED
ACTIVE
LOCKED
DISABLED
ARCHIVED
```

业务流程：

```text
Admin Create User
 ↓
Create Identity
 ↓
Send Invite / Initial Credential
 ↓
User Set Password
 ↓
ACTIVE
 ↓
Assign Role
 ↓
Join Team
 ↓
Assign Team Role
 ↓
Permission Version Changed
 ↓
User Login
 ↓
Navigation / Authorization Context Loaded
```

---

# 7. 用户创建与权限绑定

用户创建不应该一次请求塞入所有权限关系。

推荐业务流程：

```text
Create User
 ↓
Assign Global Role
 ↓
Join Team
 ↓
Assign Team Role
```

每个步骤：

```text
独立幂等
独立审计
独立 Permission Version
```

这样权限历史清晰。

---

# 8. 用户离职闭环

管理员执行：

```text
Disable User
```

必须同时产生以下效果：

```text
User status = DISABLED
 ↓
TokenVersion++
 ↓
All Sessions Revoked
 ↓
Authorization Subject Invalidated
 ↓
Direct Grant no longer applicable
 ↓
Team Membership authorization no longer applicable
 ↓
Share target USER no longer grants runtime access
 ↓
Security/Audit Event
```

安全正确性：

```text
不能依赖 MQ 全部消费完才失效
```

下一请求必须立即拒绝。

---

# 9. 用户重新启用

重新启用：

```text
DISABLED → ACTIVE
```

不能恢复旧 Session。

用户必须：

```text
重新登录
```

权限重新根据当前：

```text
Role
Team
Team Role
Share
Temporary Grant
```

计算。

---

# 10. Organization / Team 闭环

组织结构：

```text
Organization
  ↓
Team
  ↓
Team Member
  ↓
Team Role
```

Team 生命周期：

```text
ACTIVE
DISABLED
ARCHIVED
```

---

# 11. Team 创建闭环

流程：

```text
Create Team
 ↓
Bind Organization
 ↓
Set Parent Team(optional)
 ↓
Add Members
 ↓
Assign Team Roles
 ↓
TeamVersion++
 ↓
Authorization Projection Event
 ↓
Audit
```

---

# 12. Team 成员加入

加入 Team：

```text
Validate Tenant
 ↓
Validate User ACTIVE
 ↓
Create TeamMember
 ↓
Optional TeamRole Assignment
 ↓
Team/User Effective Version changes
 ↓
Event
 ↓
Authorization Read Model Update
```

重复加入：

```text
幂等成功
```

---

# 13. Team 成员离开

流程：

```text
TeamMember ACTIVE
 ↓
Leave / Remove
 ↓
Membership inactive
 ↓
Team Role binding inactive
 ↓
Version changes
 ↓
User loses Team-derived permission
 ↓
Team-targeted Share no longer matches subject
 ↓
Audit
```

不需要：

```text
遍历删除所有 Team Share
```

因为 Share Target 仍然属于 Team，而用户已经不再匹配 Team。

---

# 14. Role 生命周期闭环

Role：

```text
Create
 ↓
Configure Permissions
 ↓
Bind Users
 ↓
ACTIVE
 ↓
Modify
 ↓
Disable
```

Role 被禁用：

```text
所有 Role-derived grants 立即不可使用
```

而不是删除 UserRole 历史。

---

# 15. Permission 配置闭环

完整权限配置流程：

```text
Application
 ↓
Service
 ↓
Resource
 ↓
Operation
 ↓
Permission
 ↓
RolePermission / TeamRolePermission
 ↓
Data Scope
 ↓
Field Policy
```

管理员必须能够从 UI 完成整个流程。

---

# 16. Resource / Operation 上线闭环

业务服务新增接口：

```text
PUT /customers/{id}
```

启动后：

```text
API Discovery
 ↓
iam_api_definition = DISCOVERED
```

未映射：

```text
UNMAPPED
```

默认：

```text
DENY
```

管理员配置：

```text
Resource = CUSTOMER
Operation = UPDATE
SecurityPolicy = AUTH_REQUIRED
```

保存后：

```text
ApiMappingVersion++
 ↓
Gateway Cache Invalidated
 ↓
API 正式受保护
```

---

# 17. 新 API 安全闭环

新接口上线不能出现：

```text
代码部署成功
↓
因为 IAM 还没配置
↓
接口暂时裸奔
```

必须：

```text
Unmapped Protected API
=
DENY
```

如果确实 PUBLIC：

必须管理员显式配置：

```text
PUBLIC
```

并产生：

```text
High Risk Audit
```

---

# 18. Operation 权限闭环

例如：

```text
CUSTOMER.UPDATE
```

虽然业务代码不能硬编码这个字符串，

管理员配置实际关系：

```text
Resource CUSTOMER
+
Operation UPDATE
```

Role：

```text
SalesManager
```

获得：

```text
Permission(Resource=Customer, Operation=Update)
```

最终运行时：

```text
resourceId + operationId
```

参与决策。

---

# 19. 登录闭环

流程：

```text
User Input Credential
 ↓
Tenant Resolve
 ↓
Identity Resolve
 ↓
Credential Verify
 ↓
User Status
 ↓
Risk Check
 ↓
Create Session
 ↓
Create Refresh Token
 ↓
Create JWT Access Token
 ↓
Login Audit
 ↓
React Load Current User
 ↓
Load Navigation
 ↓
Load Authorization Context
```

---

# 20. 登录失败闭环

错误密码：

```text
Failure Count
 ↓
Risk Evaluation
 ↓
Optional CAPTCHA / Delay / Lock
 ↓
Login Audit
```

不能：

```text
暴露用户是否存在
```

---

# 21. Session 闭环

Session 创建后：

```text
ACTIVE
```

后续可能：

```text
LOGGED_OUT
FORCED_LOGOUT
PASSWORD_CHANGED
USER_DISABLED
COMPROMISED
EXPIRED
```

任何终态：

```text
不可恢复
```

重新登录创建新 Session。

---

# 22. Refresh Token 闭环

```text
RT1 ACTIVE
 ↓ Refresh
RT1 ROTATED
RT2 ACTIVE
```

异常重复使用 RT1：

```text
Reuse Detection
 ↓
Session COMPROMISED
 ↓
Family Revoke
 ↓
Security Event
```

---

# 23. 用户访问业务资源闭环

例如：

```text
GET /customers/100
```

完整：

```text
JWT
 ↓
Gateway Authentication
 ↓
API Mapping
 ↓
CUSTOMER + READ
 ↓
Authorization Operation Decision
 ↓
Business Service
 ↓
Instance/Data Scope
 ↓
Field Permission
 ↓
Database
 ↓
Response Mask / Hide
 ↓
Access Audit
```

---

# 24. 列表查询闭环

例如：

```text
GET /customers
```

流程：

```text
Operation QUERY ALLOW
 ↓
Resolve DataPermissionPlan
 ↓
MyBatis AST Rewrite
 ↓
Tenant Predicate
AND
Data Scope Predicate
 ↓
SQL
 ↓
Result Rows
 ↓
Field Response Filter
```

必须确保：

```text
COUNT
List
Pagination
```

使用同一安全范围。

---

# 25. 更新业务资源闭环

```text
PATCH /customers/100
```

完整：

```text
Operation UPDATE
 ↓
Instance Authorization
 ↓
Submitted Field Capture
 ↓
Field WRITE Guard
 ↓
Business Validation
 ↓
MyBatis SET Column Guard
 ↓
Data Permission UPDATE Predicate
 ↓
Optimistic Lock
 ↓
DB Commit
 ↓
Business Outbox(optional)
 ↓
Audit
```

这形成：

```text
Operation
+
Instance
+
Field
+
SQL
```

四层保护。

---

# 26. IDOR 防护闭环

用户：

```text
可以访问 Customer 100
```

手工改 URL：

```text
/customer/200
```

如果 200 不在：

```text
Data Scope / ACL
```

必须：

```text
DENY
```

不能仅靠前端列表不可见。

---

# 27. 跨团队分享业务闭环

场景：

```text
Customer 100
属于 Team B
```

Team A 用户正常：

```text
DENY
```

Team B 合法授权者创建 Share：

```text
Customer 100
→ Team A
Operations = READ
Fields = phone MASK
Expire = 7 days
```

流程：

```text
Validate SHARE
 ↓
Validate Grantable Operation
 ↓
Validate Grantable Fields
 ↓
Create Share
 ↓
Outbox
 ↓
Permission Version
 ↓
ACL Projection
 ↓
Team A next query sees Customer 100
```

---

# 28. 分享权限扩大防护

授权者只有：

```text
READ
```

请求分享：

```text
READ + UPDATE
```

必须：

```text
403 SHARE_OPERATION_ESCALATION
```

字段同理：

```text
MASK
```

不能分享：

```text
RAW READ
```

---

# 29. Share 到期闭环

Share：

```text
expireTime = 10:00
```

10:01：

运行时：

```text
立即 DENY
```

不等待 PowerJob。

PowerJob：

```text
ACTIVE → EXPIRED
```

只是状态和审计收敛。

---

# 30. Share 撤销闭环

管理员：

```text
Revoke Share
```

事务：

```text
Share REVOKED
 ↓
PermissionVersion++
 ↓
Outbox
 ↓
Commit
```

从 Commit 后：

```text
旧 ALLOW Cache 不可继续使用
```

MQ 后续：

```text
ACL Projection → REVOKED
```

完成读模型收敛。

---

# 31. Reshare 闭环

A → B：

```text
canReshare = true
```

B → C：

必须满足：

```text
Child operations ⊆ Parent
Child fields ⊆ Parent
Child expire <= Parent expire
depth <= maxDepth
```

Parent 被撤销：

```text
Child runtime grant 立即无效
```

后续 Job/MQ 收敛状态。

---

# 32. Temporary Grant 闭环

管理员可以创建：

```text
Temporary Grant
```

例如：

```text
User A
Customer EXPORT
2 hours
```

到期：

```text
运行时按 expire_time 自动无效
```

Job：

```text
负责 EXPIRED 状态
```

必须完整审计。

---

# 33. Data Scope 变更闭环

Role：

```text
Customer QUERY
Data Scope = TEAM
```

管理员改：

```text
TEAM → ALL
```

流程：

```text
Validate
 ↓
Risk = EXPAND
 ↓
Save
 ↓
PermissionVersion++
 ↓
Outbox
 ↓
Audit
 ↓
Security Event(optional/high-risk)
```

下一请求立即使用新版本。

---

# 34. Field Policy 变更闭环

例如：

```text
phone MASK → READ
```

属于：

```text
Permission Expansion
```

必须：

```text
High Risk Audit
```

如果：

```text
salary HIDDEN → READ
```

同样必须能从 Permission Change Audit 查到。

---

# 35. Role 撤权闭环

管理员删除：

```text
RolePermission
```

流程：

```text
RolePermission change
 ↓
RoleVersion++
 ↓
Outbox
 ↓
Commit
```

用户当前仍有旧 JWT：

```text
不需要重新登录
```

下一业务请求：

```text
Effective Version mismatch
 ↓
Recompute
 ↓
DENY
```

---

# 36. Team Role 撤权闭环

逻辑完全类似：

```text
TeamRolePermission change
 ↓
TeamRoleVersion++
 ↓
Effective Subject Version changes
 ↓
Old ALLOW invalid
```

---

# 37. User Role 解绑闭环

```text
UserRole ACTIVE
 ↓
REVOKED / REMOVED
 ↓
UserVersion++
 ↓
Authorization Read Model Event
```

下一请求不再匹配该 Role。

---

# 38. Resource Disabled 闭环

管理员禁用 Resource：

```text
Resource enabled=false
```

Hard Guard：

```text
直接 DENY
```

无论：

```text
Role
Share
Direct Grant
Owner
```

有多少 ALLOW。

---

# 39. Operation Disabled 闭环

同理：

```text
Operation disabled
```

Hard Guard：

```text
DENY
```

不能被高 priority ALLOW 覆盖。

---

# 40. API Mapping 变更闭环

例如：

```text
PUT /customer/{id}
```

从：

```text
UPDATE
```

错误配置为：

```text
QUERY
```

管理员修改回来：

```text
API Mapping Version++
 ↓
Gateway Cache invalidation
 ↓
New request uses new mapping
 ↓
Audit
```

---

# 41. PUBLIC API 风险闭环

API：

```text
AUTH_REQUIRED → PUBLIC
```

必须：

```text
High Risk Confirmation
 ↓
Save
 ↓
Audit
 ↓
Security Event
```

前端和后端都不能静默修改。

---

# 42. Authorization Explain 闭环

当用户反馈：

```text
“为什么我能看到这个客户？”
```

管理员输入：

```text
User
Resource
Operation
Instance
```

Explain 返回：

```text
Team Membership
 ↓
Team Role
 ↓
Permission
 ↓
Share
 ↓
Data Scope
 ↓
Field Policy
 ↓
ALLOW
```

Explain 与真实引擎：

```text
同一套算法
```

---

# 43. Deny Explain 闭环

例如：

```text
DENY_DATA_SCOPE
```

管理员可看到：

```text
Operation allowed
but
instance owner team not in effective teams
and no matching share
```

普通用户只得到：

```text
403 Forbidden
```

防止泄露权限结构。

---

# 44. 审计闭环

高价值操作：

```text
User Disable
Role Permission Change
Data Scope Expand
Field Raw Access Expand
Share Create
Share Revoke
PUBLIC API Change
Force Logout
DLQ Replay
Projection Rebuild
```

必须：

```text
Business DB
+
Outbox
+
Audit Event
```

确保不可轻易丢失。

---

# 45. 审计查询闭环

管理员可以从：

```text
User
Role
Resource
TraceId
DecisionId
ShareId
```

反查：

```text
谁做了什么
什么时候
Before
After
影响谁
```

---

# 46. Security Event 闭环

例如：

```text
Refresh Token Reuse
```

流程：

```text
Detection
 ↓
Security Event CRITICAL
 ↓
Session Revoke
 ↓
Audit
 ↓
Security Center OPEN
 ↓
Security Admin ACK / RESOLVE
```

Security Admin 的处理动作本身：

```text
继续被审计
```

---

# 47. Idempotency 闭环

关键写 API：

```text
Create User
Create Share
Grant Permission
```

客户端：

```text
Idempotency-Key
```

服务：

```text
Acquire
 ↓
Business TX
 ↓
SUCCESS
```

网络响应丢失：

```text
same key retry
 ↓
Replay original result
```

---

# 48. MQ 闭环

业务提交：

```text
Local TX
+
Outbox
```

Outbox Relay：

```text
RabbitMQ
```

Consumer：

```text
Inbox/ConsumeRecord
 ↓
Projection Update
 ↓
Commit
 ↓
ACK
```

重复消息：

```text
No duplicate business effect
```

---

# 49. MQ 乱序闭环

Projection：

```text
current version = 3
```

收到：

```text
v2
```

直接：

```text
ignore
```

收到：

```text
v5
```

如果为完整状态事件：

```text
apply
+
gap metric
+
reconcile
```

---

# 50. Outbox 故障闭环

RabbitMQ 宕机：

```text
Business TX 仍提交
Outbox NEW/FAILED
```

系统：

```text
持续重试
```

MQ 恢复：

```text
Event Publish
Projection Converge
```

监控：

```text
Outbox oldest pending
```

超过阈值告警。

---

# 51. PowerJob 故障闭环

PowerJob 停止：

```text
Share 到期仍 DENY
Temporary Grant 到期仍 DENY
Refresh Token 到期仍 DENY
```

PowerJob 恢复：

```text
状态收敛
清理
归档
Reconcile
```

---

# 52. Redis 故障闭环

Redis Session 不可用：

```text
AUTH_REQUIRED
```

不能退化为：

```text
JWT valid => ALLOW
```

应：

```text
503 / Fail Closed
```

授权缓存不可用：

```text
如果能安全回源 DB/Projection
→ Recompute

否则
→ DENY/503
```

---

# 53. Authorization Service 故障闭环

业务服务：

```text
Authorization call failed
```

只有存在：

```text
Version-valid
not expired
safe cache
```

时才允许使用缓存。

无法证明：

```text
DENY / 503
```

绝不：

```text
fallback allow
```

---

# 54. Audit Service 故障闭环

Audit 服务故障：

```text
实时 Authorization
```

继续工作。

高价值审计：

```text
Outbox / MQ 中等待
```

恢复后补齐。

---

# 55. Projection 故障闭环

ACL Projection 丢失或不一致：

```text
Reconcile / Rebuild
```

必须可从：

```text
Sharing Source of Truth
```

重建。

Projection 永远不是唯一事实源。

---

# 56. 用户工作台闭环

React 登录后：

```text
Bootstrap
 ↓
Current User
 ↓
Navigation
 ↓
Authorization Context
 ↓
Dynamic Routes
```

用户：

```text
只看到允许页面
```

但后端仍独立安全校验。

---

# 57. IAM 管理员配置闭环

IAM Admin 可依次完成：

```text
Create Resource
 ↓
Create Operations
 ↓
Map APIs
 ↓
Create Role
 ↓
Grant Operations
 ↓
Configure Data Scope
 ↓
Configure Fields
 ↓
Assign User / Team Role
 ↓
Test with Authorization Explain
```

不需要修改 Java 代码。

这是 V1 最重要业务闭环之一。

---

# 58. 新业务系统接入闭环

一个新的业务微服务接入 IAM：

```text
Add IAM Security Starter
 ↓
Register Service
 ↓
API Discovery
 ↓
Create/Map Resource
 ↓
Create Operations
 ↓
Register ResourceDataSchema
 ↓
Register ResourceFields
 ↓
Configure Role Permission
 ↓
Run Security Tests
 ↓
Production
```

整个过程：

```text
不新增 @PreAuthorize permission string
```

---

# 59. 新 Resource 上线验收

必须通过：

```text
No Role → DENY
Role Grant → ALLOW
Role Revoke → immediate DENY
Wrong Tenant → DENY
Wrong Instance → DENY
Field Attack → DENY
```

才允许上线。

---

# 60. 资源 Owner 变更闭环

业务资源：

```text
ownerTeam A → ownerTeam B
```

业务服务发布：

```text
ResourceOwnershipChangedEvent
```

Sharing Policy：

```text
KEEP_ON_OWNER_TRANSFER
or
REVOKE_ON_OWNER_TRANSFER
```

Sharing 根据 Grant Basis 处理旧 Owner 派生 Share。

---

# 61. Resource 删除闭环

业务资源删除：

```text
ResourceDeletedEvent
```

效果：

```text
所有 instance Share 不再授权
 ↓
Projection 收敛
 ↓
Audit
```

重复事件：

```text
幂等
```

---

# 62. Tenant Suspend 闭环

Tenant：

```text
ACTIVE → SUSPENDED
```

Hard Guard：

```text
所有用户业务访问 DENY
```

但安全管理员/平台运维是否可进入恢复页面：

```text
由独立平台安全域策略决定
```

V1 不使用：

```text
tenantId=0
```

绕过租户。

---

# 63. 租户恢复

```text
SUSPENDED → ACTIVE
```

不恢复旧不安全 Session。

建议：

```text
高风险场景全部用户重新登录
```

具体由 Tenant Security Policy 控制。

---

# 64. 业务状态与权限状态区分

例如：

```text
Contract status = TERMINATED
```

这属于：

```text
Business State
```

不是 IAM Role。

按钮是否可操作：

```text
Authorization Allowed
AND
Business State Allowed
```

避免 IAM 承担全部业务工作流。

---

# 65. Condition Policy 边界

真正需要统一权限条件时：

```text
Operation UPDATE
only when resource.status = DRAFT
```

可通过受限 Condition DSL。

禁止：

```text
SpEL
Groovy
JavaScript
任意 SQL
```

---

# 66. 全链 Trace

一次高风险授权修改：

```text
HTTP traceId
 ↓
Business Mutation
 ↓
PermissionVersion
 ↓
Outbox eventId
 ↓
MQ
 ↓
Consumer
 ↓
Projection
 ↓
Audit
```

管理员可根据：

```text
traceId
```

追完整链路。

---

# 67. 核心业务状态机汇总

## Tenant

```text
INITIALIZING
ACTIVE
SUSPENDED
DISABLED
INITIALIZATION_FAILED
```

## User

```text
INVITED
ACTIVE
LOCKED
DISABLED
ARCHIVED
```

## Session

```text
ACTIVE
LOGGED_OUT
FORCED_LOGOUT
PASSWORD_CHANGED
USER_DISABLED
COMPROMISED
EXPIRED
```

## Share

```text
WAITING
ACTIVE
EXPIRED
REVOKED
```

## Refresh Token

```text
ACTIVE
ROTATED
REVOKED
EXPIRED
COMPROMISED
```

## Security Event

```text
OPEN
ACKNOWLEDGED
RESOLVED
FALSE_POSITIVE
```

---

# 68. 业务闭环 API 分组

建议最终 API 域：

```text
/api/v1/auth/**
/api/v1/me/**
/api/v1/users/**
/api/v1/roles/**
/api/v1/organizations/**
/api/v1/teams/**
/api/v1/resources/**
/api/v1/operations/**
/api/v1/permissions/**
/api/v1/resource-shares/**
```

管理员：

```text
/admin/v1/api-mappings/**
/admin/v1/data-scopes/**
/admin/v1/field-policies/**
/admin/v1/authorization/**
/admin/v1/audit/**
/admin/v1/security/**
/admin/v1/infrastructure/**
```

内部：

```text
/internal/v1/**
```

禁止公网。

---

# 69. React 业务闭环页面

React 必须至少支持以下完整流程：

```text
登录
 ↓
用户管理
 ↓
角色管理
 ↓
团队管理
 ↓
资源/Operation
 ↓
API Mapping
 ↓
Permission Matrix
 ↓
Data Scope
 ↓
Field Policy
 ↓
Resource Share
 ↓
Authorization Explain
 ↓
Audit
 ↓
Security Event
```

不能只做独立 CRUD 页面而无法串联操作。

---

# 70. User Detail 闭环

用户详情页至少：

```text
基本信息
角色
团队
Team Role
Session
Direct/Temporary Grant
Permission Source
Permission History
Audit
```

管理员可以从一个用户进入完整权限诊断。

---

# 71. Role Detail 闭环

Role 详情：

```text
成员
Operation Permission
Data Scope
Field Policy
Change History
```

修改后直接：

```text
Authorization Explain
```

验证结果。

---

# 72. Team Detail 闭环

Team 详情：

```text
Members
Team Roles
Team Role Permission
Shared Resources
Audit
```

形成团队管理与 ACL 闭环。

---

# 73. Resource Detail 闭环

Resource 详情：

```text
Operations
API Mapping
Data Schema
Fields
Sharing Policy
Audit
```

从 Resource 可进入：

```text
Role Permission
Share
Explain
```

---

# 74. Security Center 闭环

Security Event：

```text
OPEN
```

Security Admin：

```text
查看详情
 ↓
关联 Trace / User / Session
 ↓
Acknowledge
 ↓
处理
 ↓
Resolve / False Positive
```

操作全部审计。

---

# 75. Infrastructure Recovery 闭环

例如 DLQ：

```text
DLQ message
 ↓
Security/Infra Admin inspect
 ↓
Fix root cause
 ↓
Replay
 ↓
Consumer idempotency
 ↓
Projection corrected
 ↓
Infra Audit
```

---

# 76. Projection Rebuild 闭环

管理员：

```text
Trigger Rebuild
```

需要：

```text
MAINTAIN/REBUILD Operation
Step-Up(optional high-risk)
```

Job：

```text
Source Truth
 ↓
Rebuild Projection
 ↓
Version Verify
 ↓
Health Normal
 ↓
Audit
```

---

# 77. 业务闭环 Definition of Done

一个 IAM 能力不能只因为：

```text
Controller 写完
```

就算完成。

必须满足：

```text
Create/Change
+
Authorization Effect
+
Immediate Revoke
+
Audit
+
Explain
+
Idempotency
+
Failure Recovery
+
React Operation
+
Automated Test
```

至少覆盖适用部分。

---

# 78. V1.0 核心业务闭环 1 — RBAC

```text
Create User
 ↓
Create Role
 ↓
Create Resource/Operation
 ↓
Grant Role Permission
 ↓
Assign User
 ↓
Login
 ↓
API ALLOW
 ↓
Revoke Permission
 ↓
Next API DENY
 ↓
Audit + Explain
```

必须 100% 自动化 E2E。

---

# 79. V1.0 核心业务闭环 2 — Team

```text
User joins Team
 ↓
Assign Team Role
 ↓
TeamRolePermission
 ↓
API ALLOW
 ↓
User leaves Team
 ↓
Next API DENY
```

---

# 80. V1.0 核心业务闭环 3 — Data Permission

```text
Role QUERY allowed
 ↓
DataScope TEAM
 ↓
User sees Team A data
 ↓
Attempts Team B instance
 ↓
DENY
 ↓
Change Scope ALL
 ↓
Version change
 ↓
New access effective
```

---

# 81. V1.0 核心业务闭环 4 — Field Permission

```text
phone MASK
salary HIDDEN
salary WRITE DENY
 ↓
UI masked/hidden
 ↓
Manual salary PATCH
 ↓
403
 ↓
DB unchanged
 ↓
Audit
```

---

# 82. V1.0 核心业务闭环 5 — Cross-Team Share

```text
Team A cannot access resource
 ↓
Team B shares READ
 ↓
Team A can access
 ↓
Field policy converges
 ↓
Share revoked/expired
 ↓
Immediate DENY
```

---

# 83. V1.0 核心业务闭环 6 — Authentication

```text
Login
 ↓
JWT + Session
 ↓
Refresh Rotation
 ↓
Force Logout
 ↓
Old JWT DENY
```

以及：

```text
Refresh Reuse
 ↓
Compromised
 ↓
Security Event
```

---

# 84. V1.0 核心业务闭环 7 — Distributed Consistency

```text
Create Share
 ↓
HTTP response lost
 ↓
Retry same idempotency key
 ↓
same share result
 ↓
Outbox
 ↓
MQ duplicate
 ↓
one projection effect
```

---

# 85. V1.0 核心业务闭环 8 — Failure Recovery

```text
RabbitMQ down
 ↓
business commit + outbox
 ↓
RabbitMQ recover
 ↓
projection converge
```

以及：

```text
PowerJob down
 ↓
expired grant still DENY
```

---

# 86. V1.0 核心业务闭环 9 — Audit

```text
Permission change
 ↓
Before/After
 ↓
PermissionVersion
 ↓
Audit event
 ↓
Audit Center query
 ↓
Trace correlation
```

---

# 87. V1.0 核心业务闭环 10 — New Business Integration

```text
New microservice
 ↓
IAM Starter
 ↓
API Discovery
 ↓
Resource/Operation Mapping
 ↓
Field/Data Schema
 ↓
Role Grant
 ↓
Security Golden Test
 ↓
Production
```

证明平台真正可复用，而不是只服务 IAM 自己。

---

# 88. 验收数据集

必须提供 Demo Tenant：

```text
tenant-alpha
tenant-beta
```

用户：

```text
alice
bob
security-admin
```

Team：

```text
sales-a
sales-b
finance
```

Role：

```text
sales-manager
sales-member
audit-viewer
```

Resource：

```text
customer
contract
```

用于自动 E2E。

---

# 89. Demo 闭环场景

演示：

```text
Alice = Sales A Manager
Bob   = Sales B Member

Customer C1 belongs Sales B
```

步骤：

```text
1 Alice cannot access C1
2 Bob/authorized manager shares C1 to Sales A
3 Alice sees C1
4 phone masked
5 Alice cannot modify salary
6 revoke share
7 Alice immediately denied
8 Explain shows revoked share
9 Audit shows creator/revoker
```

这是 V1 最重要 Demo。

---

# 90. 业务闭环测试目录

建议新增：

```text
tests/e2e/business-closed-loop/
├── tenant-bootstrap.spec.ts
├── user-onboarding.spec.ts
├── rbac-loop.spec.ts
├── team-role-loop.spec.ts
├── data-permission-loop.spec.ts
├── field-permission-loop.spec.ts
├── cross-team-share-loop.spec.ts
├── immediate-revoke.spec.ts
├── auth-session-loop.spec.ts
└── audit-explain-loop.spec.ts
```

---

# 91. CODE PHASE 开发约束

后续生成代码时，每个 Phase 都必须至少完成一个闭环。

禁止开发方式：

```text
先写完 80 张表
再写所有 Mapper
再写所有 Controller
最后才联调
```

正式采用：

```text
Vertical Slice
```

例如：

```text
User → Role → Permission → Login → API → Revoke
```

先跑通。

---

# 92. 第一个闭环实现顺序

CODE PHASE 推荐第一个真正闭环：

```text
Tenant
 ↓
User
 ↓
Role
 ↓
Resource
 ↓
Operation
 ↓
RolePermission
 ↓
Login
 ↓
Gateway
 ↓
Authorization
 ↓
Demo Protected API
 ↓
Revoke
```

这是架构正确性的第一证明。

---

# 93. 第二个闭环

```text
Team
 ↓
TeamMember
 ↓
TeamRole
 ↓
TeamRolePermission
 ↓
Authorization
```

---

# 94. 第三个闭环

```text
DataScope
 ↓
Business Demo Table
 ↓
MyBatis Interceptor
 ↓
List/Detail/Update
```

---

# 95. 第四个闭环

```text
Field Metadata
 ↓
Field Policy
 ↓
React Dynamic Form
 ↓
Backend WRITE Guard
 ↓
Response Mask
```

---

# 96. 第五个闭环

```text
Share
 ↓
Outbox
 ↓
RabbitMQ
 ↓
ACL Projection
 ↓
Cross-Team Query
 ↓
Revoke
```

---

# 97. 不再按“模块百分比”汇报进度

错误：

```text
Auth 80%
Role 70%
Team 60%
```

正确：

```text
RBAC Closed Loop = DONE
Team Closed Loop = DONE
Data Permission Closed Loop = DOING
```

业务闭环比模块完成率更有意义。

---

# 98. V1.0 发布阻断条件

以下任何一个闭环失败：

```text
Tenant isolation
Immediate revoke
Field write deny
Cross-team ACL revoke
Refresh/session revoke
HTTP idempotency
MQ duplicate
Audit trace
```

均：

```text
BLOCK RELEASE
```

---

# 99. SPEC 25 冻结结论

V1.0 业务实现不允许退化成：

```text
一堆独立 CRUD 页面
+
一堆权限表
```

而必须形成：

```text
身份生命周期
+
组织生命周期
+
权限生命周期
+
资源访问生命周期
+
分享生命周期
+
会话生命周期
+
审计生命周期
+
故障恢复生命周期
```

真正闭环。

---

# 100. 下一步 CODE PHASE

SPEC 25 完成后，设计层真正具备业务闭环。

下一步应进入：

# CODE PHASE 01 — Foundation + First RBAC Closed Loop

第一阶段代码验收目标：

```text
Infrastructure Healthy

Backend Maven Build Green

Tenant/User/Role/Resource/Operation DDL Ready

Auth Login Works

Gateway Auth Works

Authorization Engine Works

Demo Protected API Works

Role Grant → ALLOW

Role Revoke → Immediate DENY

Audit Trace Exists
```

只有完成这一条闭环后，才继续 Team/Data/Field/ACL。
