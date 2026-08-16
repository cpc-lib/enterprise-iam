# Enterprise IAM & Dynamic Authorization Platform
## 26 — API Contract, Business State Machine & End-to-End Acceptance SPEC 1.0

> 本文将 SPEC 25 的业务闭环继续下沉为可开发的 API Contract、状态机、错误码与端到端验收标准。
>
> 目标：任何一个核心业务闭环，都可以从本文直接拆成 Controller/Application/Domain/Test/React Story，而不再依赖口头理解。

---

# 1. API 设计总原则

统一：

```text
RESTful + Command-style endpoints
```

适合 CRUD 的场景：

```text
POST   /resources
GET    /resources/{id}
PUT    /resources/{id}
GET    /resources
```

适合明确状态迁移的场景：

```text
POST /users/{id}/disable
POST /shares/{id}/revoke
POST /sessions/{id}/force-logout
POST /security-events/{id}/acknowledge
```

避免：

```text
DELETE /share/{id}
```

直接表达“撤销”，因为 Share 有生命周期与审计语义。

---

# 2. API Prefix

外部业务：

```text
/api/v1/**
```

管理员：

```text
/admin/v1/**
```

内部：

```text
/internal/v1/**
```

内部接口默认：

```text
INTERNAL_ONLY
```

禁止外部用户 Token 直接调用。

---

# 3. 统一响应结构

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "..."
}
```

错误：

```json
{
  "success": false,
  "code": "IAM_AUTHZ_DENIED",
  "message": "Permission denied",
  "traceId": "..."
}
```

---

# 4. HTTP 状态语义

```text
200  成功
201  创建成功
204  无返回体成功
400  请求格式/参数错误
401  未认证/Token 无效
403  已认证但无权限
404  资源不可见/不存在
409  状态冲突/版本冲突/幂等冲突
422  业务语义校验失败
429  限流
503  安全基础设施不可用
```

---

# 5. Trace Contract

所有外部响应建议返回：

```text
traceId
```

后端日志、审计、授权 Explain：

```text
必须能够关联
```

高风险授权还返回内部：

```text
decisionId
```

普通客户端可不展示。

---

# 6. Tenant Bootstrap API

## Create

```http
POST /admin/v1/tenants/bootstrap
Idempotency-Key: <uuid>
```

Request：

```json
{
  "tenantCode": "tenant-alpha",
  "tenantName": "Tenant Alpha",
  "initialAdmin": {
    "identityType": "USERNAME",
    "identity": "admin",
    "displayName": "Tenant Admin"
  }
}
```

Response：

```json
{
  "tenantId": "1001",
  "status": "INITIALIZING"
}
```

后台 Saga 完成后：

```text
ACTIVE
```

---

# 7. Tenant State Machine

```text
INITIALIZING
   ├── ACTIVE
   └── INITIALIZATION_FAILED

ACTIVE
   ├── SUSPENDED
   └── DISABLED

SUSPENDED
   ├── ACTIVE
   └── DISABLED

DISABLED
   └── terminal
```

非法：

```text
DISABLED → ACTIVE
```

V1 默认不允许。

---

# 8. Tenant Commands

```http
POST /admin/v1/tenants/{id}/suspend
POST /admin/v1/tenants/{id}/resume
POST /admin/v1/tenants/{id}/disable
```

这些全部：

```text
需要 version
需要 Audit
需要高风险权限
```

---

# 9. User API

```http
POST /admin/v1/users
GET  /admin/v1/users/{id}
GET  /admin/v1/users
PUT  /admin/v1/users/{id}
POST /admin/v1/users/{id}/disable
POST /admin/v1/users/{id}/enable
POST /admin/v1/users/{id}/force-logout
```

---

# 10. Create User Contract

```json
{
  "username": "alice",
  "displayName": "Alice",
  "email": "alice@example.com"
}
```

Response：

```json
{
  "id": "100",
  "status": "INVITED",
  "version": 1
}
```

---

# 11. User State Machine

```text
INVITED
  ├── ACTIVE
  └── DISABLED

ACTIVE
  ├── LOCKED
  ├── DISABLED
  └── ARCHIVED

LOCKED
  ├── ACTIVE
  └── DISABLED

DISABLED
  ├── ACTIVE
  └── ARCHIVED

ARCHIVED
  └── terminal
```

---

# 12. Disable User Effects

`POST /users/{id}/disable`

必须原子/一致性地产生：

```text
user.status = DISABLED
userSecurity.tokenVersion++
revoke sessions
outbox(UserStatusChanged)
audit
```

验收：

```text
旧 Access Token 下一请求失效
```

---

# 13. Role API

```http
POST /admin/v1/roles
GET  /admin/v1/roles/{id}
GET  /admin/v1/roles
PUT  /admin/v1/roles/{id}
POST /admin/v1/roles/{id}/disable
POST /admin/v1/roles/{id}/enable
```

---

# 14. Role Permission API

```http
GET  /admin/v1/roles/{roleId}/permissions
PUT  /admin/v1/roles/{roleId}/permissions
```

Request：

```json
{
  "version": 7,
  "permissions": [
    {
      "resourceId": "500",
      "operationId": "10",
      "effect": "ALLOW",
      "priority": 500
    }
  ]
}
```

保存后：

```text
RoleVersion++
Permission Change Audit
Outbox
```

---

# 15. User Role Binding

```http
PUT /admin/v1/users/{userId}/roles
```

Request：

```json
{
  "roleIds": ["10", "11"],
  "version": 3
}
```

采用：

```text
replace-set semantics
```

即请求代表最终全集。

服务端计算：

```text
added
removed
```

用于审计。

---

# 16. Organization API

```http
POST /admin/v1/organizations
GET  /admin/v1/organizations/{id}
GET  /admin/v1/organizations/tree
PUT  /admin/v1/organizations/{id}
```

---

# 17. Team API

```http
POST /admin/v1/teams
GET  /admin/v1/teams/{id}
GET  /admin/v1/teams
PUT  /admin/v1/teams/{id}
POST /admin/v1/teams/{id}/disable
```

---

# 18. Team Member API

```http
POST   /admin/v1/teams/{teamId}/members
GET    /admin/v1/teams/{teamId}/members
DELETE /admin/v1/teams/{teamId}/members/{userId}
```

如果使用 DELETE：

语义明确是：

```text
remove membership
```

不是删除用户。

---

# 19. Team Role API

```http
POST /admin/v1/teams/{teamId}/roles
GET  /admin/v1/teams/{teamId}/roles
PUT  /admin/v1/teams/{teamId}/roles/{teamRoleId}
```

---

# 20. Team Member Role Binding

```http
PUT /admin/v1/teams/{teamId}/members/{userId}/roles
```

Request：

```json
{
  "teamRoleIds": ["301"],
  "version": 5
}
```

---

# 21. Application API

```http
POST /admin/v1/applications
GET  /admin/v1/applications
GET  /admin/v1/applications/{id}
PUT  /admin/v1/applications/{id}
```

---

# 22. Service Metadata API

```http
POST /admin/v1/applications/{appId}/services
GET  /admin/v1/applications/{appId}/services
```

用于注册逻辑服务，不等同于 Nacos 实例。

---

# 23. Resource API

```http
POST /admin/v1/resources
GET  /admin/v1/resources
GET  /admin/v1/resources/{id}
PUT  /admin/v1/resources/{id}
POST /admin/v1/resources/{id}/disable
```

---

# 24. Resource Definition

Request：

```json
{
  "applicationId": "1",
  "serviceId": "20",
  "resourceCode": "CUSTOMER",
  "displayName": "Customer",
  "resourceType": "BUSINESS"
}
```

`resourceCode` 是管理元数据。

允许存在数据库。

禁止业务 Java 代码依赖这个 code 做授权。

---

# 25. Operation API

```http
POST /admin/v1/resources/{resourceId}/operations
GET  /admin/v1/resources/{resourceId}/operations
PUT  /admin/v1/operations/{operationId}
POST /admin/v1/operations/{operationId}/disable
```

---

# 26. Operation Definition

```json
{
  "operationCode": "UPDATE",
  "displayName": "Update",
  "riskLevel": "MEDIUM",
  "enabled": true
}
```

同样：

```text
code 属于元数据
```

授权运行时通过：

```text
resourceId + operationId
```

判断。

---

# 27. API Discovery Contract

业务服务启动后注册：

```http
POST /internal/v1/api-definitions/discover
```

Request 示例：

```json
{
  "serviceId": "crm-service",
  "apis": [
    {
      "httpMethod": "PUT",
      "pathPattern": "/customers/{id}",
      "handler": "CustomerController#update"
    }
  ]
}
```

重复发现：

```text
幂等 upsert
```

---

# 28. API Mapping Admin

```http
PUT /admin/v1/api-definitions/{apiId}/mapping
```

Request：

```json
{
  "resourceId": "500",
  "operationId": "10",
  "securityPolicy": "AUTH_REQUIRED"
}
```

---

# 29. API Mapping State

```text
DISCOVERED_UNMAPPED
MAPPED
STALE
DISABLED
```

`DISCOVERED_UNMAPPED`：

```text
受保护入口默认 DENY
```

---

# 30. Data Scope API

```http
GET /admin/v1/roles/{roleId}/data-scopes
PUT /admin/v1/roles/{roleId}/permissions/{rolePermissionId}/data-scope
```

TeamRole：

```http
PUT /admin/v1/team-role-permissions/{teamRolePermissionId}/data-scope
```

---

# 31. Data Scope Contract

```json
{
  "scopeType": "TEAM_AND_CHILDREN",
  "teamIds": [],
  "customPolicyId": null,
  "version": 3
}
```

`SPECIFIED_TEAM` 才允许：

```text
teamIds 非空
```

---

# 32. Field Metadata API

```http
GET  /admin/v1/resources/{resourceId}/fields
POST /admin/v1/resources/{resourceId}/fields/{fieldId}/activate
PUT  /admin/v1/resources/{resourceId}/fields/{fieldId}
```

---

# 33. Field Policy API

Role：

```http
PUT /admin/v1/role-permissions/{rolePermissionId}/field-policies
```

TeamRole：

```http
PUT /admin/v1/team-role-permissions/{id}/field-policies
```

---

# 34. Field Policy Contract

```json
{
  "operationId": "10",
  "fields": [
    {
      "fieldId": "1001",
      "readable": true,
      "writable": false,
      "hidden": false,
      "maskStrategyId": "20"
    }
  ],
  "version": 4
}
```

---

# 35. Share API

```http
POST /api/v1/resource-shares
GET  /api/v1/resource-shares/{id}
GET  /api/v1/resource-shares/created-by-me
GET  /api/v1/resource-shares/shared-with-me
POST /api/v1/resource-shares/{id}/revoke
POST /api/v1/resource-shares/{id}/reshare
```

---

# 36. Create Share Request

```json
{
  "resourceId": "500",
  "resourceInstanceKey": "C10001",
  "target": {
    "type": "TEAM",
    "id": "200"
  },
  "operationIds": ["10"],
  "fields": [
    {
      "operationId": "10",
      "fieldId": "1001",
      "readable": true,
      "writable": false,
      "maskStrategyId": "20"
    }
  ],
  "startTime": null,
  "expireTime": "2026-12-31T23:59:59Z",
  "canReshare": false
}
```

---

# 37. Share State Machine

```text
WAITING
  ├── ACTIVE
  └── REVOKED

ACTIVE
  ├── EXPIRED
  └── REVOKED

EXPIRED
  └── terminal

REVOKED
  └── terminal
```

有效性：

```text
status ∈ {WAITING, ACTIVE}
AND
start <= now
AND
(expire is null OR now < expire)
AND
parent/root valid
```

---

# 38. Revoke Share Contract

```http
POST /api/v1/resource-shares/{id}/revoke
```

Request：

```json
{
  "reason": "project completed",
  "version": 5
}
```

重复 revoke：

```text
幂等成功
```

但不能重复生成业务状态变化事件。

---

# 39. Reshare Contract

```http
POST /api/v1/resource-shares/{id}/reshare
```

必须服务端自动：

```text
parentShareId
depth
rootShareId
```

客户端不可伪造。

---

# 40. Login API

```http
POST /api/v1/auth/login
```

```json
{
  "tenantCode": "tenant-alpha",
  "identityType": "USERNAME",
  "identity": "alice",
  "credential": "******"
}
```

---

# 41. Refresh API

```http
POST /api/v1/auth/refresh
```

Web 推荐 Refresh Token：

```text
HttpOnly Cookie
```

Response：

```json
{
  "accessToken": "...",
  "expiresIn": 900
}
```

---

# 42. Logout API

```http
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
```

均：

```text
幂等
```

---

# 43. Session Admin API

```http
GET  /api/v1/me/sessions
POST /api/v1/me/sessions/{sessionId}/revoke
```

管理员：

```http
POST /admin/v1/users/{userId}/sessions/{sessionId}/force-logout
```

---

# 44. Current User API

```http
GET /api/v1/me
GET /api/v1/me/navigation
GET /api/v1/me/authorization-context
```

登录响应不返回完整权限全集。

---

# 45. Authorization Internal API

```http
POST /internal/v1/authorization/check
POST /internal/v1/authorization/batch-check
POST /internal/v1/authorization/explain
```

外部 Admin Explain 可以通过 Admin API 转发。

---

# 46. Authorization Check Request

```json
{
  "resourceId": "500",
  "operationId": "10",
  "resourceInstanceKey": "C10001",
  "context": {
    "actingTeamId": "200"
  }
}
```

可信：

```text
tenantId/userId
```

由 Signed Security Context 获取。

客户端不能提供。

---

# 47. Authorization Result

```json
{
  "decision": "ALLOW",
  "decisionCode": "ALLOW_TEAM_ROLE",
  "decisionId": "01J...",
  "permissionVersion": "...",
  "dataPermission": {},
  "fieldPermission": {}
}
```

---

# 48. Explain API

```http
POST /admin/v1/authorization/explain
```

Request：

```json
{
  "subjectUserId": "100",
  "resourceId": "500",
  "operationId": "10",
  "resourceInstanceKey": "C10001"
}
```

---

# 49. Explain Contract

Response：

```json
{
  "decision": "ALLOW",
  "decisionCode": "ALLOW_RESOURCE_SHARE",
  "steps": [],
  "winningGrants": [],
  "dataPermission": {},
  "fieldPermission": {}
}
```

必须：

```text
与真实 authorize 同结果
```

---

# 50. Security Event API

```http
GET  /admin/v1/security/events
GET  /admin/v1/security/events/{id}
POST /admin/v1/security/events/{id}/acknowledge
POST /admin/v1/security/events/{id}/resolve
POST /admin/v1/security/events/{id}/false-positive
```

---

# 51. Security Event State Machine

```text
OPEN
  ├── ACKNOWLEDGED
  ├── RESOLVED
  └── FALSE_POSITIVE

ACKNOWLEDGED
  ├── RESOLVED
  └── FALSE_POSITIVE

RESOLVED
  └── terminal

FALSE_POSITIVE
  └── terminal
```

---

# 52. Audit API

```http
GET /admin/v1/audit/login
GET /admin/v1/audit/admin-operations
GET /admin/v1/audit/permission-changes
GET /admin/v1/audit/authorization
GET /admin/v1/audit/resource-access
GET /admin/v1/audit/sensitive-fields
```

---

# 53. Audit Query Contract

统一支持：

```text
startTime
endTime
userId
resourceId
operationId
traceId
decision
cursor
limit
```

大日志：

```text
cursor pagination
```

---

# 54. Infrastructure Admin API

```http
GET  /admin/v1/infrastructure/outbox
GET  /admin/v1/infrastructure/dlq
POST /admin/v1/infrastructure/dlq/{id}/replay
GET  /admin/v1/infrastructure/projections
POST /admin/v1/infrastructure/projections/{projection}/rebuild
```

这些属于：

```text
高风险 Operation
```

---

# 55. Optimistic Lock Contract

可编辑资源均返回：

```text
version
```

更新请求必须携带。

冲突：

```text
409 IAM_VERSION_CONFLICT
```

React：

```text
提示重新加载
```

---

# 56. Idempotency Contract

强幂等写：

```text
POST create
grant
share
bootstrap
```

Header：

```text
Idempotency-Key
```

缺失：

```text
400 IAM_IDEMPOTENCY_KEY_REQUIRED
```

同 Key 不同请求：

```text
409 IAM_IDEMPOTENCY_KEY_CONFLICT
```

---

# 57. Error Code 命名规则

统一：

```text
IAM_{DOMAIN}_{MEANING}
```

例如：

```text
IAM_AUTH_INVALID_CREDENTIALS
IAM_AUTH_TOKEN_EXPIRED
IAM_AUTH_REFRESH_REUSED

IAM_AUTHZ_DENIED
IAM_AUTHZ_RESOURCE_DISABLED
IAM_AUTHZ_DATA_SCOPE_DENIED

IAM_FIELD_WRITE_DENIED
IAM_FIELD_UNKNOWN

IAM_SHARE_OPERATION_ESCALATION
IAM_SHARE_FIELD_ESCALATION
IAM_SHARE_PARENT_INVALID

IAM_IDEMPOTENCY_KEY_CONFLICT
IAM_VERSION_CONFLICT
```

---

# 58. 错误码不可依赖文案

前端：

```text
switch(error.code)
```

处理。

禁止：

```text
message.includes(...)
```

---

# 59. 业务状态机实现规则

所有状态变化必须通过：

```text
Domain Method
```

例如：

```java
share.revoke(...)
session.forceLogout(...)
securityEvent.resolve(...)
```

禁止 Application：

```java
entity.setStatus(...)
```

直接修改复杂 Aggregate 状态。

---

# 60. 状态机必须带 Guard

例如 Share：

```text
ACTIVE → REVOKED
```

Guard：

```text
operator authorized
version matches
not terminal
```

---

# 61. 状态事件原则

只有真正发生变化：

```text
before != after
```

才生成 Domain Event。

幂等重复命令：

```text
返回当前结果
```

不重复生成事件。

---

# 62. E2E Acceptance — Tenant Bootstrap

Given：

```text
系统为空
```

When：

```text
bootstrap tenant-alpha
```

Then：

```text
Tenant ACTIVE
Initial Admin ACTIVE
Root Org exists
Default Team exists
Bootstrap Role assigned
Admin login succeeds
Audit exists
```

重复相同 Key：

```text
无重复 Tenant
```

---

# 63. E2E Acceptance — RBAC

Given：

```text
Alice no permission
```

When：

```text
Role grants Customer.UPDATE
Alice assigned Role
```

Then：

```text
Alice update Customer allowed
```

When：

```text
RolePermission revoked
```

Then：

```text
Alice next request denied without relogin
```

---

# 64. E2E Acceptance — Team Role

Given：

```text
Alice belongs Team A
TeamRole Manager has UPDATE
```

Then：

```text
ALLOW
```

When：

```text
Alice removed from Team A
```

Then：

```text
immediate DENY
```

---

# 65. E2E Acceptance — Data Scope

Given：

```text
Customer C1 ownerTeam=A
Customer C2 ownerTeam=B
Alice DataScope=TEAM(A)
```

Then：

```text
list only C1
detail C2 denied
update C2 denied
count excludes C2
```

---

# 66. E2E Acceptance — Field Permission

Given：

```text
phone MASK
salary HIDDEN
salary WRITE=false
```

Then：

```text
Response phone masked
salary absent
```

When：

```text
manual PATCH salary
```

Then：

```text
403 IAM_FIELD_WRITE_DENIED
DB unchanged
```

---

# 67. E2E Acceptance — Share

Given：

```text
Alice Team A
C2 belongs Team B
```

Initially：

```text
DENY
```

When：

```text
C2 shared to Team A READ
```

Then：

```text
Alice READ allowed
```

When：

```text
share revoked
```

Then：

```text
Alice next READ denied
```

---

# 68. E2E Acceptance — Share Expire

Share expires at T.

At：

```text
T + 1ms
```

even if PowerJob stopped：

```text
DENY
```

---

# 69. E2E Acceptance — Refresh

Given：

```text
RT1
```

When：

```text
refresh
```

Then：

```text
RT1 ROTATED
RT2 ACTIVE
```

When RT1 reused after detection window：

```text
session compromised
security event created
```

---

# 70. E2E Acceptance — Idempotency

When：

```text
Create Share succeeds
response lost
client retries same key
```

Then：

```text
one share
same shareId returned
```

---

# 71. E2E Acceptance — MQ Duplicate

When：

```text
same ShareCreated event delivered 10 times
```

Then：

```text
one projection effect
consume record safe
```

---

# 72. E2E Acceptance — RabbitMQ Failure

Given：

```text
RabbitMQ down
```

When：

```text
permission change committed
```

Then：

```text
outbox pending
business data committed
```

When RabbitMQ restored：

```text
event published
projection converges
```

---

# 73. E2E Acceptance — PowerJob Failure

Given：

```text
PowerJob down
```

When：

```text
temporary grant expires
```

Then：

```text
authorization denies
```

When PowerJob restored：

```text
status converges EXPIRED
```

---

# 74. E2E Acceptance — Audit Explain

When：

```text
Alice allowed by TeamRole
```

Then：

```text
Explain shows Team -> TeamRole -> Permission
Audit contains decision
traceId correlates
```

---

# 75. Acceptance — Cross Tenant

任何：

```text
tenant-alpha user
```

访问：

```text
tenant-beta resource
```

必须：

```text
DENY
Security Event
Audit
```

无任何普通 Grant 可以覆盖。

---

# 76. Acceptance — Spoof Header

外部请求添加：

```text
X-User-Id: admin
X-Tenant-Id: other
```

Gateway：

```text
strip
```

最终 Subject 仍来自可信 JWT。

---

# 77. Acceptance — Unknown API

业务服务发现新：

```text
DELETE /customers/{id}
```

但未 Mapping。

请求：

```text
DENY
```

直到管理员显式配置。

---

# 78. Acceptance — PUBLIC API Change

When：

```text
AUTH_REQUIRED -> PUBLIC
```

Then：

```text
High Risk Audit
Security Event
Mapping Version changes
```

---

# 79. Acceptance — Optimistic Conflict

Admin A 和 B 同时编辑 Role v10。

A：

```text
save → v11
```

B：

```text
save v10
```

结果：

```text
409 IAM_VERSION_CONFLICT
```

不得覆盖 A。

---

# 80. Acceptance — Resource Disabled

存在：

```text
Role ALLOW
Share ALLOW
Direct ALLOW
```

但 Resource：

```text
disabled
```

结果：

```text
DENY
```

---

# 81. Acceptance — Operation Disabled

同上：

```text
Hard Guard DENY
```

---

# 82. Acceptance — Audit Failure

Audit Service down。

高价值权限变更：

```text
业务成功
审计事件留在 Outbox/MQ
```

恢复：

```text
Audit eventually persisted
```

---

# 83. Acceptance — Authorization Failure

Authorization Service down。

无安全有效缓存：

```text
503 / DENY
```

禁止：

```text
fallback allow
```

---

# 84. React Acceptance — Login

```text
Login
Navigation
Dynamic Route
Refresh
Logout
```

完整闭环。

---

# 85. React Acceptance — Permission Matrix

管理员修改：

```text
RolePermission
```

UI 展示：

```text
Added
Removed
Risk
```

保存成功后：

```text
version 更新
```

---

# 86. React Acceptance — Field Policy

UI：

```text
HIDDEN
MASK
READ
WRITE
```

动态配置。

非法组合前端阻止，

但后端仍二次校验。

---

# 87. React Acceptance — Share

Drawer：

```text
Target
Operations
Fields
Time
Reshare
Preview
Submit
```

重试：

```text
同一 Idempotency-Key
```

---

# 88. React Acceptance — Explain

选择：

```text
User
Resource
Operation
Instance
```

返回：

```text
Decision
Timeline
Source
Data Scope
Field Policy
```

---

# 89. React Acceptance — Security Center

Security Event：

```text
OPEN
```

管理员：

```text
ACK
RESOLVE
```

状态正确，

动作被审计。

---

# 90. Contract Test 要求

所有跨服务内部 API：

```text
必须 OpenAPI / Contract Test
```

重点：

```text
Auth ↔ Gateway
Business ↔ Authorization
Sharing ↔ Authorization
Sharing ↔ Resource Metadata
```

---

# 91. Event Contract 要求

所有 MQ Event：

```text
eventType
schemaVersion
```

固定。

Consumer 必须兼容滚动升级窗口。

---

# 92. Backward Compatibility

滚动发布期间：

```text
old producer
new consumer
new producer
old consumer
```

需要兼容。

禁止：

```text
同版本直接改字段语义
```

---

# 93. API Deprecation

未来删除 API：

先：

```text
deprecated=true
```

至少保留一个版本窗口。

再：

```text
remove in next major/minor policy
```

---

# 94. Business Closed Loop Release Gate

Release 前至少执行：

```text
tenant-bootstrap
rbac-loop
team-role-loop
data-permission-loop
field-permission-loop
cross-team-share-loop
immediate-revoke
auth-session-loop
idempotency-loop
audit-explain-loop
```

全部：

```text
PASS
```

---

# 95. CODE PHASE Story Mapping

每个 API Story 必须同时包含：

```text
Request/Response DTO
Application Command
Domain Method
Repository
Error Code
Audit
Idempotency if needed
Unit Test
Integration Test
React if user-facing
```

---

# 96. 第一阶段 API 实现范围

CODE PHASE 01/02 只先实现：

```text
Tenant Bootstrap
User
Role
UserRole
Application
Resource
Operation
RolePermission
Login
Refresh
Authorization Check
Demo Business Endpoint
Explain Basic
```

完成第一条 RBAC 闭环。

---

# 97. 第二阶段 API

```text
Organization
Team
Team Member
Team Role
TeamRolePermission
```

完成 Team 闭环。

---

# 98. 第三阶段 API

```text
Data Scope
Resource Data Schema
Field Metadata
Field Policy
```

完成 Data + Field 闭环。

---

# 99. 第四阶段 API

```text
Resource Share
Revoke
Reshare
ACL Projection
```

完成跨团队闭环。

---

# 100. SPEC 26 冻结结论

从此以后，V1.0 每个核心能力都必须同时具备：

```text
API Contract
+
Domain State Machine
+
Error Code
+
Audit
+
Version
+
Idempotency
+
Security Test
+
E2E Acceptance
```

而不是只有：

```text
Controller + Mapper
```

这份 SPEC 是后续代码生成和验收的直接契约。
