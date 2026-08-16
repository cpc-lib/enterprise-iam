# Enterprise IAM & Dynamic Authorization Platform
## 29 — Query Model, Repository Contract & Persistence Access SPEC 1.0

> 本文用于冻结 Enterprise IAM V1.0 的 Repository Contract、Query Model、Persistence Access Boundary 与关键 SQL 访问策略。
>
> 目标不是把每张表生成一个 `BaseMapper<T>`，而是让持久化层直接服务于：
>
> - 授权热路径；
> - 多租户隔离；
> - Data Permission；
> - Field Permission；
> - Share / ACL；
> - 幂等；
> - Outbox；
> - Audit；
> - Job / Reconcile；
> - 高并发与 Seek Pagination。
>
> 本文冻结后，后续实现 Repository / Mapper / SQL 必须围绕业务 Use Case 设计，而不是围绕表结构设计。

---

# 1. 持久化层总原则

正式冻结：

```text
Domain Repository
    !=
MyBatis Mapper

Query Model
    !=
Domain Aggregate

Write Model
    !=
Read Model
```

Repository 面向：

```text
Aggregate
Business Invariant
Use Case
```

Mapper 面向：

```text
SQL
Table
Projection
```

---

# 2. 分层依赖

```text
interfaces
    ↓
application
    ↓
domain
    ↑
infrastructure
```

Domain 定义：

```text
Repository Interface
```

Infrastructure 实现：

```text
MyBatis Repository Adapter
```

例如：

```java
public interface ResourceShareRepository {

    Optional<ResourceShare> findById(
        TenantId tenantId,
        ShareId shareId
    );

    void save(ResourceShare share);

    boolean existsActiveShare(
        TenantId tenantId,
        ResourceId resourceId,
        ResourceInstanceKey instanceKey,
        ShareTarget target
    );
}
```

Domain 不允许依赖：

```text
BaseMapper
LambdaQueryWrapper
Page
IPage
```

---

# 3. Mapper 归属

MyBatis Mapper 只能存在：

```text
infrastructure.persistence.mapper
```

例如：

```text
IamResourceShareMapper
IamResourceShareOperationMapper
IamResourceShareFieldMapper
```

Application/Domain 禁止直接注入 Mapper。

---

# 4. Repository Adapter

推荐：

```text
ResourceShareRepository
        ↑
MybatisResourceShareRepository
```

Repository Adapter 负责：

```text
Aggregate load
Aggregate persistence
Entity/PO conversion
Optimistic lock
Relation persistence coordination
```

---

# 5. PO / Domain Model 分离

数据库对象：

```text
ResourceShareDO
```

Domain：

```text
ResourceShare
```

禁止直接：

```text
@TableName
@TableField
```

标注 Domain Aggregate。

---

# 6. Query Model

复杂查询不强制：

```text
Aggregate load
```

使用：

```text
Query Service
+
Read DTO
```

例如：

```text
UserAuthorizationOverviewQueryService
ShareListQueryService
AuditQueryService
```

---

# 7. CQRS Lite

V1 正式采用：

```text
Command Model
+
Query Model
```

但不引入独立 CQRS 框架。

写：

```text
Application Command Service
Domain Aggregate
Repository
```

读：

```text
Application Query Service
Query Mapper
Read DTO
```

---

# 8. 为什么查询不能全走 Domain Repository

例如：

```text
用户详情页
```

需要：

```text
User
Roles
Teams
Sessions
Permission Sources
Audit Summary
```

如果全部加载 Aggregate：

```text
RPC/SQL 数量过多
```

应该使用：

```text
专用 Query Model
```

---

# 9. Repository 命名规则

Domain：

```text
UserRepository
RoleRepository
TeamRepository
PermissionRepository
ResourceShareRepository
SessionRepository
RefreshTokenRepository
```

禁止：

```text
UserDao
UserMapper
```

出现在 Domain。

---

# 10. Query Mapper 命名

Infrastructure：

```text
UserQueryMapper
AuthorizationQueryMapper
ShareQueryMapper
AuditQueryMapper
```

强调：

```text
Read Purpose
```

---

# 11. Tenant Enforcement

所有 Tenant 数据 Repository：

```text
必须要求 TenantId
```

推荐接口：

```java
findById(
    TenantId tenantId,
    UserId userId
)
```

不推荐：

```java
findById(
    UserId userId
)
```

然后依赖 ThreadLocal 自动补租户作为唯一安全边界。

---

# 12. Tenant Interceptor

MyBatis Tenant Interceptor：

```text
是第二道防线
```

Repository Contract 显式 TenantId：

```text
是第一道语义防线
```

两者同时存在。

---

# 13. Platform Scope

平台级：

```text
iam_tenant
```

某些查询不带 tenant_id。

必须放入：

```text
PlatformTenantRepository
```

或明确：

```text
PlatformScope
```

不能普通业务 Repository 随意绕过 Tenant Interceptor。

---

# 14. Repository 返回值

单条：

```text
Optional<T>
```

不存在：

```text
Optional.empty()
```

不要：

```text
return null
```

---

# 15. 多条

返回：

```text
List<T>
```

无数据：

```text
empty list
```

禁止返回 null。

---

# 16. 分页

普通管理后台小表可以：

```text
PageQuery
```

但大表默认：

```text
Cursor / Seek Pagination
```

尤其：

```text
Audit
Outbox
Security Event
Share
ACL
```

---

# 17. Offset Pagination 边界

允许：

```text
Role
Resource
Operation
Team
```

等 Metadata 小表使用：

```text
pageNo/pageSize
```

禁止大型日志：

```text
OFFSET 1000000
```

---

# 18. Cursor Contract

统一：

```java
record CursorPageRequest(
    String cursor,
    int limit
) {}
```

响应：

```java
record CursorPageResult<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {}
```

---

# 19. Cursor 编码

Cursor：

```text
opaque
```

前端不能解析。

内部可以编码：

```text
createdAt
id
```

建议：

```text
Base64Url(JSON)
```

并校验格式。

---

# 20. Seek SQL

例如 Audit：

```sql
WHERE tenant_id = ?
AND (
    created_at < ?
    OR (
        created_at = ?
        AND id < ?
    )
)
ORDER BY created_at DESC, id DESC
LIMIT ?
```

---

# 21. User Repository Contract

至少：

```text
findById
findByUsername
existsByUsername
save
updateStatus
```

复杂：

```text
pageUsers
```

走：

```text
UserQueryService
```

---

# 22. User Identity Lookup

登录热路径：

```text
tenant + identityType + identityKey
```

必须单 SQL 命中。

禁止：

```text
先查 username
再查 email
再查 phone
```

多次探测。

---

# 23. Auth Credential Query

Auth 需要：

```text
UserIdentity
+
User basic security status
```

推荐内部查询：

```text
IdentityAuthenticationView
```

一次返回：

```text
userId
identityId
credentialRef/hash metadata
userStatus
tenantStatus
```

减少 Auth 登录 RPC。

---

# 24. Role Repository

写模型：

```text
Role
```

关系：

```text
UserRole
```

分别 Repository。

不要 Role Aggregate 一次加载：

```text
10万 UserRole
```

---

# 25. UserRole Repository

接口：

```text
bind
unbind
findActiveRoleIdsByUser
replaceRoles
```

replaceRoles：

```text
在 Application Service 计算 diff
```

或 Repository 提供专用：

```text
replaceActiveBindings
```

但必须保留：

```text
added
removed
```

供 Audit/Event。

---

# 26. Team Repository

写：

```text
TeamRepository
TeamMemberRepository
TeamRoleRepository
TeamMemberRoleRepository
```

不要一个巨大：

```text
OrganizationRepository
```

管理所有表。

---

# 27. Team Tree Query

管理端 Tree：

```text
专用 TeamTreeQueryMapper
```

V1 Materialized Path：

```text
支持 children/subtree
```

禁止递归 N+1：

```text
select child for each node
```

---

# 28. Authorization Resource Repository

分：

```text
ApplicationRepository
ServiceMetadataRepository
ResourceRepository
OperationRepository
PermissionRepository
```

但授权运行时不应每请求分别查这些 Repository。

---

# 29. Authorization Runtime Read Model

核心查询：

```text
AuthorizationSubjectView
AuthorizationResourceView
PermissionGrantView
```

运行时：

```text
Caffeine
Redis
Read Model
```

不是：

```text
逐表 Repository N+1
```

---

# 30. Subject Read Model

建议包含：

```text
tenantId
userId
userStatus
roleIds
teamIds
teamRoleIds
versionVector
```

不直接包含：

```text
所有 PermissionGrant
```

Grant 独立按 Resource/Operation 拉取或缓存。

---

# 31. Grant Query

核心接口：

```java
List<PermissionGrantView> findApplicableGrants(
    AuthorizationSubjectView subject,
    ResourceId resourceId,
    OperationId operationId,
    Instant now
);
```

Infrastructure 可以：

```text
多 SQL 合并
或本地 Projection 查询
```

但 Application 不关心表结构。

---

# 32. Grant 查询目标

必须支持：

```text
DIRECT_USER
USER_ROLE
TEAM_ROLE
RESOURCE_OWNER
RESOURCE_SHARE
TEMPORARY_GRANT
```

V1 Runtime Resolver 可以按来源拆成：

```text
GrantResolver SPI
```

---

# 33. RolePermission Query

高频索引：

```text
tenant_id
role_id
permission_id
status
```

Permission：

```text
resource_id + operation_id
```

建议运行时通过：

```text
permission_id resolution cache
```

减少 join。

---

# 34. TeamRolePermission Query

同理：

```text
tenant_id
team_role_id
permission_id
status
```

---

# 35. Permission Definition Cache

```text
Resource + Operation
→ PermissionId
```

属于：

```text
稳定 Metadata Cache
```

Version：

```text
Resource/Operation/Permission metadata version
```

变化时失效。

---

# 36. Data Scope Repository Contract

运行时不返回：

```text
raw SQL
```

返回：

```text
DataScopeDefinition
```

例如：

```java
record DataScopeDefinition(
    DataScopeType type,
    Set<TeamId> specifiedTeams,
    PolicyId customPolicyId
) {}
```

---

# 37. Data Permission Plan Query

Authorization Service 输出：

```text
DataPermissionPlan
```

Business Starter 不直接查：

```text
iam_role_data_scope
```

否则业务服务耦合 IAM DB。

---

# 38. ResourceDataSchema Repository

Authorization 内：

```text
findByResource
```

Business Starter：

通过：

```text
cached metadata client
```

获取。

不能业务代码：

```text
hardcode owner_team_id
```

---

# 39. Field Permission Repository

运行时查询必须关联：

```text
Grant Binding
```

即：

```text
role_permission_id
team_role_permission_id
```

不能仅：

```text
role_id + resource_id
```

否则 Operation 级 Field Policy 会混淆。

---

# 40. Field Metadata Query

接口：

```text
findActiveFieldsByResource
```

应支持：

```text
propertyPath
columnName
systemManaged
sensitiveLevel
```

缓存：

```text
Resource Metadata Version
```

---

# 41. API Definition Query

Gateway 热路径：

```text
service + HTTP method + normalized path
```

映射：

```text
ApiAuthorizationMappingView
```

包括：

```text
apiDefinitionId
securityPolicy
resourceId
operationId
riskLevel
idempotencyPolicy
mappingVersion
```

---

# 42. Path Match

不能每请求：

```text
SELECT * FROM iam_api_definition
```

再 Java 遍历全部 PathPattern。

Gateway 必须：

```text
本地 route/path matcher index
```

DB 只负责 Source of Truth。

---

# 43. API Mapping Cache

加载方式：

```text
startup/load
event update
periodic reconcile
```

Key：

```text
service + method + normalized path pattern
```

---

# 44. Share Repository

Aggregate Load：

```text
share header
operations
fields
basis
```

对于 Command：

```text
Create
Revoke
Reshare
```

需要完整 Aggregate。

---

# 45. Share List Query

管理列表不加载完整 Aggregate。

用：

```text
ShareSummaryQueryMapper
```

返回：

```text
shareId
resource
instanceKey
target
status
start
expire
creator
```

---

# 46. Share Active Duplicate Query

必须：

```text
DB unique constraint
```

最终保证。

Repository 可提前：

```text
existsActiveShare
```

用于友好错误。

但：

```text
pre-check 不是 correctness boundary
```

---

# 47. Share Lock Strategy

Create：

```text
DB Unique
```

优先。

Revoke：

```text
optimistic version + status CAS
```

Reshare：

读取 Parent：

```text
必要时 SELECT ... FOR UPDATE
```

仅在同一短事务内保证：

```text
parent state / depth / expiry
```

一致。

---

# 48. Share SELECT FOR UPDATE

允许：

```text
create derived share
```

时锁 Parent Share 短事务。

禁止：

```text
锁 Parent
→ RPC Authorization
→ RPC Business Service
→ 再保存
```

RPC 必须在进入短 DB Lock 前完成，或采用版本二次校验。

---

# 49. TOCTOU 处理

推荐：

```text
1. 外部授权检查
2. 读取 resource/share version
3. 进入本地事务
4. CAS/lock re-check local source version
5. commit
```

跨服务 Resource 状态通过：

```text
expectedResourceVersion
```

检测。

---

# 50. Share Basis Query

Explain / Reshare：

```text
share_id
```

查询：

```text
basis_source_type
basis_source_id
basis_permission_version
parent_share_id
```

不是授权热路径每请求必查。

---

# 51. ACL Projection Query

列表 SHARED 分支：

不返回 ACL ids 后再 Java post-filter。

必须 SQL：

```text
EXISTS local ACL projection
```

或等效 join。

---

# 52. ACL List Predicate

示意：

```sql
EXISTS (
  SELECT 1
  FROM iam_resource_acl_projection acl
  WHERE acl.tenant_id = biz.tenant_id
    AND acl.resource_id = ?
    AND acl.resource_instance_key = biz.id
    AND acl.operation_id = ?
    AND acl.status = 'ACTIVE'
    AND acl.start_time <= ?
    AND (acl.expire_time IS NULL OR acl.expire_time > ?)
    AND (
         (acl.subject_type='USER' AND acl.subject_id=?)
      OR (acl.subject_type='TEAM' AND acl.subject_id IN (...))
      OR (acl.subject_type='ROLE' AND acl.subject_id IN (...))
      OR (acl.subject_type='TEAM_ROLE' AND acl.subject_id IN (...))
    )
)
```

实际 SQL 必须：

```text
AST + parameter binding
```

不能字符串拼接 subject ids。

---

# 53. ACL Instance Query

实例授权：

Redis 可加速。

但 DB Projection 应支持：

```text
tenant
resource
instance
operation
subject
```

联合索引。

---

# 54. ACL Checkpoint Query

DataPermissionPlan 带：

```text
expectedShareEpoch
```

业务本地：

```text
checkpoint
```

比较：

```text
checkpoint < expected
→ omit SHARED / fail closed
```

---

# 55. Session Repository

Auth DB：

```text
SessionRepository
```

负责：

```text
create
find
revoke
revokeAllByUser
updateLastAccess
```

Redis：

```text
SessionCacheRepository
```

分开。

---

# 56. Session Runtime Lookup

每请求：

```text
Redis
```

不能：

```text
MySQL every request
```

MySQL 是：

```text
Source of Truth / audit recovery
```

---

# 57. Session Redis Missing

若 DB 有 ACTIVE、Redis 无：

V1：

```text
Fail Closed
```

不每请求自动回源恢复 Session。

避免：

```text
Redis outage → DB stampede
```

---

# 58. RefreshToken Repository

关键：

```text
findByTokenHashForUpdate
rotateAtomically
revokeFamily
```

Rotation：

```text
单事务
```

---

# 59. Refresh Lock

可以：

```text
SELECT ... FOR UPDATE
```

锁当前 Token 行。

同时：

```text
status='ACTIVE'
```

校验。

---

# 60. UserSecurityState Repository

需要：

```text
atomicIncrementTokenVersion
```

不能：

```text
read version
version++
save
```

产生 lost update。

---

# 61. PermissionVersion Repository

核心接口：

```java
long increment(
    TenantId tenantId,
    SubjectType type,
    String subjectId
);
```

SQL：

```text
atomic increment
```

并返回新版本。

---

# 62. Version Batch Query

用户 Effective Version 可能需要：

```text
Role/Team/TeamRole versions
```

禁止：

```text
N+1 select
```

提供：

```text
batchFindVersions
```

---

# 63. Authorization Snapshot Repository

不是运行时主链。

用途：

```text
history
compare
incident
```

查询：

```text
user + snapshotVersion/time
```

---

# 64. Audit Query Model

Audit 不使用 Domain Aggregate。

直接：

```text
AuditQueryMapper
```

按：

```text
tenant
time
user
resource
decision
trace
```

查询。

---

# 65. Audit Cursor

默认：

```text
created_at DESC, id DESC
```

稳定分页。

---

# 66. Audit Export Query

禁止一次：

```text
SELECT 10000000 rows
```

装内存。

采用：

```text
cursor batch stream
```

例如：

```text
1000~5000 rows/batch
```

由 Job 写 MinIO。

---

# 67. Outbox Repository

关键方法：

```text
append
claimBatch
markPublished
markRetry
markDead
releaseExpiredClaims
```

---

# 68. Outbox Claim

MySQL 8：

```text
SELECT ... FOR UPDATE SKIP LOCKED
```

短事务 Claim：

```text
NEW/FAILED
→ PUBLISHING
claim_owner
claim_until
```

Commit 后：

```text
publish outside transaction
```

---

# 69. Outbox Publish 顺序

同 Aggregate 顺序重要时：

```text
ORDER BY aggregate_id, aggregate_version
```

但多 Worker 下仍不能只靠扫描顺序保证全局顺序。

Consumer：

```text
aggregateVersion
```

是最终防线。

---

# 70. Idempotency Repository

关键：

```text
tryAcquire
find
markSuccess
markFinalFailure
markRetryableFailure
takeOverExpiredLease
```

---

# 71. Idempotency Acquire

最终并发边界：

```text
UNIQUE
```

流程：

```text
INSERT PROCESSING
```

Duplicate：

```text
load existing
compare requestHash
```

---

# 72. Idempotency Response

Repository 不负责业务对象序列化。

由：

```text
ResponseSnapshotCodec
```

处理。

Repository 保存：

```text
status
body/reference
headers allowlist
```

---

# 73. MQ Consume Repository

短事务 Consumer：

```text
tryInsertSuccessMarker
```

实际上推荐：

```text
insert consume record
business projection
commit
```

若 duplicate key：

```text
already consumed
```

直接 ACK。

---

# 74. Job Repository

扫描 Job Item：

业务状态优先。

`sys_job_business_record` 用于：

```text
execution visibility
lease
retry
```

不能取代：

```text
business WHERE status/version
```

---

# 75. Job Scan Query

必须 Seek：

```text
expire_time
id
```

或者：

```text
id
```

禁止大 OFFSET。

---

# 76. Batch Size

初始建议：

```text
100~500
```

按任务压测调整。

不能：

```text
一次 100000
```

同事务。

---

# 77. MyBatis Interceptor 顺序

业务服务建议：

```text
1 Tenant
2 Data Permission
3 Pagination
```

Field Update Guard：

```text
在 UPDATE SQL 最终执行前
```

检查 SET Columns。

具体插件顺序必须集成测试验证。

---

# 78. Data Permission 拦截点

只拦：

```text
受保护 Business Mapper
```

Authorization 自己的 Metadata Mapper：

```text
不走 Business DataScope
```

但仍走：

```text
Tenant Isolation
```

---

# 79. Mapper Classification

建议标记/注册：

```text
PROTECTED_BUSINESS
IAM_METADATA
SYSTEM_INTERNAL
```

不能业务开发者随意：

```text
@IgnoreDataPermission
```

---

# 80. Statement Mapping

复杂 SQL：

```text
iam_data_permission_statement_mapping
```

可配置：

```text
mapperStatement
resourceId
mode
```

Mode：

```text
AUTO
PRIMARY
SYSTEM_INTERNAL
```

`SYSTEM_INTERNAL`：

```text
只能受信任配置
+
Audit
```

---

# 81. Native SQL Boundary

V1 受保护业务模块：

```text
MyBatis only
```

禁止：

```text
JdbcTemplate
JPA
native Connection
```

绕过权限 Interceptor。

---

# 82. XML Mapper

允许：

```text
MyBatis XML
```

但仍必须被 Interceptor 保护。

---

# 83. MyBatis-Plus Wrapper

允许：

```text
Wrapper
```

但 DataPermission 不依赖 Wrapper 内容。

最终 SQL 统一 AST 处理。

---

# 84. Count Query

必须应用：

```text
Tenant
DataPermission
```

分页 total 与列表一致。

---

# 85. Exists Query

资源 existence：

如果直接返回：

```text
exists=true
```

可能泄漏无权限资源。

对用户请求：

```text
existence query
```

同样必须 Data Permission。

---

# 86. Unique Validation

例如业务 Resource code：

若用户无权限看到某条记录，

后台唯一性检查仍由业务写逻辑执行，

但不能把：

```text
“值已存在于 Team B”
```

泄露给无权限用户。

---

# 87. Aggregation Query

SUM/COUNT/GROUP BY：

同样：

```text
Data Permission before aggregation
```

否则会通过统计泄露数据。

---

# 88. Export Query

导出：

```text
Operation EXPORT
+
Data Permission
+
Field Permission
```

Query Mapper 不允许：

```text
直接全表 export
```

---

# 89. Search Integration

未来 Elasticsearch：

必须先获取：

```text
Authorization/Data Scope Plan
```

再转成 ES filter。

不能：

```text
ES 返回全量
Java post-filter
```

---

# 90. Repository Transaction Boundary

Repository：

```text
不自行 @Transactional
```

默认事务边界：

```text
Application Service
```

特殊 Infrastructure 原子操作：

```text
可以独立 RequiresNew
```

例如：

```text
Idempotency acquire
```

但必须由 SPEC 明确。

---

# 91. RequiresNew 使用边界

禁止随意：

```text
REQUIRES_NEW
```

导致：

```text
业务回滚
审计/状态却提交
```

只有：

```text
幂等 reservation
特定技术 lease
```

等明确场景使用。

---

# 92. Lock Repository Contract

若必须锁：

接口显式：

```text
findByIdForUpdate
```

不要 Repository 内偷偷：

```text
SELECT FOR UPDATE
```

让调用者不知道。

---

# 93. Pessimistic Lock 适用

适用：

```text
Refresh rotation
Derived Share parent invariant
Rare high-contention state
```

不适用：

```text
普通 Role edit
User edit
Metadata edit
```

这些用 Optimistic Lock。

---

# 94. Optimistic Lock Contract

更新必须：

```text
WHERE id=?
AND tenant_id=?
AND version=?
```

成功：

```text
version=version+1
```

affected=0：

```text
IAM_VERSION_CONFLICT
```

---

# 95. Status CAS

状态迁移同时：

```text
WHERE status IN (...)
AND version=?
```

防止非法并发状态。

---

# 96. Repository Error Translation

Infrastructure：

```text
DuplicateKeyException
Deadlock
LockTimeout
```

转换为：

```text
PersistenceConflict
PersistenceUnavailable
```

Application 再映射业务错误。

禁止 SQL 异常直接穿透 Controller。

---

# 97. Deadlock Retry

短本地事务如果 MySQL Deadlock：

可：

```text
有限自动 retry
```

前提：

```text
Command 本身幂等/安全
```

默认：

```text
1~2 次
```

不能无限重试。

---

# 98. Slow Query

所有核心 Query：

```text
Micrometer timer
```

超过阈值：

```text
structured warning
```

但日志不输出敏感参数。

---

# 99. N+1 Gate

Code Review / Integration Test 重点防：

```text
for each user → select roles
for each team → select members
for each share → select operations
```

列表页：

```text
batch query
join read model
```

解决。

---

# 100. Batch Query Contract

Repository/Query Service 应提供：

```text
findByIds
batchFindRoleIdsByUsers
batchFindTeamIdsByUsers
batchFindPermissions
```

而不是 Application 自己循环单查。

---

# 101. Large IN Boundary

`IN (...)`：

初始阈值：

```text
<= 500
```

超过：

```text
projection table
temporary strategy
split batch
```

不能一次拼：

```text
100000 ids
```

---

# 102. Team Membership Projection

大组织 Data Permission：

```text
iam_subject_team_projection
```

可由业务服务本地维护。

字段：

```text
tenant_id
user_id
team_id
relation_type
version
```

用于避免巨大 IN。

V1 小规模可以先 Inline IDs。

---

# 103. Query Security Invariant

任何业务 Query 的最终安全谓词：

```text
Tenant
AND
BusinessPredicate
AND
AllowPredicate
AND
NOT DenyPredicate
```

不能把 Tenant 放进：

```text
Allow OR
```

中。

---

# 104. Empty Allow

如果没有任何可访问集合：

SQL：

```text
AND 1 = 0
```

列表：

```text
200 []
```

实例：

```text
403/404
```

按 Resource Policy。

---

# 105. Hide Unauthorized As Not Found

Resource metadata：

```text
hideUnauthorizedAsNotFound
```

决定：

```text
403
or
404
```

但 Audit 内部仍记录：

```text
AUTHZ DENY
```

---

# 106. Repository Contract Test

每个 Repository：

至少：

```text
tenant isolation
save/load
optimistic conflict
unique conflict
status transition
```

---

# 107. Query Mapper Test

核心 Query：

```text
real MySQL Testcontainers
```

验证：

```text
结果
SQL correctness
indexes/EXPLAIN where critical
```

---

# 108. Data Permission Test

必须包含：

```text
simple SELECT
COUNT
pagination
JOIN
UPDATE
DELETE
OR precedence
subquery
unsupported SQL fail closed
```

---

# 109. ACL Query Test

至少：

```text
USER target
TEAM target
ROLE target
TEAM_ROLE target
expired
revoked
stale checkpoint
```

---

# 110. Audit Query Test

至少：

```text
cursor
same timestamp multiple ids
tenant isolation
trace search
decision search
```

---

# 111. Outbox Query Test

并发多个 Relay：

```text
SKIP LOCKED
```

保证：

```text
claim partition
```

即使重复 publish：

Consumer 仍幂等。

---

# 112. Refresh Query Test

100 concurrent refresh：

```text
one active successor
```

验证：

```text
SELECT FOR UPDATE / unique/CAS
```

实际行为。

---

# 113. Repository Architecture Test

ArchUnit：

```text
domain..repository
```

不得依赖：

```text
org.apache.ibatis
com.baomidou.mybatisplus
springframework.data
```

---

# 114. Mapper Architecture Test

Mapper：

```text
只能 infrastructure.persistence
```

Controller/Application：

```text
不能直接依赖 mapper package
```

---

# 115. Query Service Architecture

Query Service：

```text
application.query
```

可以依赖：

```text
Query Port
```

实现位于：

```text
infrastructure.persistence.query
```

---

# 116. Repository Package Template

每服务：

```text
domain/repository/
    XxxRepository.java

infrastructure/persistence/dataobject/
    XxxDO.java

infrastructure/persistence/mapper/
    XxxMapper.java

infrastructure/repository/
    MybatisXxxRepository.java

application/query/
    XxxQueryService.java

infrastructure/persistence/query/
    XxxQueryMapper.java
```

---

# 117. Identity Repository Set

V1：

```text
TenantRepository
UserRepository
UserIdentityRepository
RoleRepository
UserRoleRepository
```

Query：

```text
UserQueryPort
AuthenticationIdentityQueryPort
RoleQueryPort
```

---

# 118. Organization Repository Set

```text
OrganizationRepository
TeamRepository
TeamMemberRepository
TeamRoleRepository
TeamMemberRoleRepository
```

Query：

```text
OrganizationTreeQueryPort
TeamQueryPort
MembershipQueryPort
```

---

# 119. Authorization Repository Set

```text
ApplicationRepository
ServiceMetadataRepository
ResourceRepository
OperationRepository
PermissionRepository
RolePermissionRepository
TeamRolePermissionRepository
DataScopeRepository
FieldPolicyRepository
ApiDefinitionRepository
ApiMappingRepository
DirectGrantRepository
TemporaryGrantRepository
PermissionVersionRepository
AuthorizationSnapshotRepository
```

Runtime Query：

```text
AuthorizationSubjectQueryPort
AuthorizationGrantQueryPort
AuthorizationMetadataQueryPort
```

---

# 120. Sharing Repository Set

```text
ResourceShareRepository
SharingPolicyRepository
ShareBasisRepository
ShareProjectionEpochRepository
```

Query：

```text
ShareQueryPort
ShareGraphQueryPort
GrantableShareQueryPort
```

---

# 121. Auth Repository Set

```text
SessionRepository
RefreshTokenRepository
UserSecurityStateRepository
OneTimeSecurityTokenRepository
```

Infra：

```text
SessionCacheRepository
ReplayProtectionRepository
```

---

# 122. Audit Query Set

主要：

```text
LoginAuditQueryPort
AdminAuditQueryPort
PermissionChangeQueryPort
AuthorizationAuditQueryPort
SecurityEventQueryPort
```

Audit 写入可直接：

```text
AuditStore
```

而非复杂 Domain Repository。

---

# 123. Infrastructure Repository Set

每服务：

```text
OutboxRepository
IdempotencyRepository
MessageConsumeRepository
```

Job：

```text
JobBusinessRecordRepository
```

---

# 124. Query Model Naming

读模型后缀：

```text
View
Summary
Detail
Row
```

例如：

```text
UserSummary
UserDetailView
ShareSummary
AuthorizationDecisionView
```

避免：

```text
UserVO/UserDTO/UserResp/UserInfo
```

全部混用。

---

# 125. API DTO 与 Query Model

Query Model：

```text
Application internal
```

API Response：

```text
interfaces.rest.response
```

可以通过 Mapper 转换。

不要把 DB Row：

```text
直接 JSON 返回
```

---

# 126. MapStruct

可选使用：

```text
MapStruct
```

做简单转换。

但复杂 Domain Aggregate：

```text
建议手写 assembler
```

保证语义明确。

---

# 127. Repository 不做权限判断

Repository 不应该：

```text
if admin then query all
```

授权计划在：

```text
Application/Starter/DataPermission
```

上层准备。

Repository 只执行：

```text
安全约束后的 Query Contract
```

---

# 128. Query Filter

普通管理查询：

```text
UserPageFilter
RolePageFilter
AuditFilter
```

必须：

```text
typed object
```

禁止把前端：

```text
sortField
```

直接拼 SQL column。

---

# 129. Sort Allowlist

排序：

```text
前端 sortKey
```

映射：

```text
server-side allowlist
```

例如：

```text
CREATED_AT → created_at
NAME → display_name
```

禁止直接：

```text
ORDER BY ${sortField}
```

---

# 130. Search Keyword

LIKE：

```text
parameter binding
```

必须 escape：

```text
% _
```

按产品语义决定是否作为 wildcard。

---

# 131. JSON Query

Condition/Audit JSON：

V1 不把复杂业务热查询建立在：

```text
JSON_EXTRACT
```

上。

关系热字段必须正规列。

---

# 132. Soft Delete Query

Metadata：

默认 Repository：

```text
delete_marker=0
```

历史审计查询：

```text
明确 includeDeleted
```

不能普通业务突然返回已删除记录。

---

# 133. Active Status Query

Grant/Share：

必须同时判断：

```text
status
start_time
expire_time
```

不能只：

```text
status='ACTIVE'
```

---

# 134. Clock

Repository 接收：

```text
Instant now
```

或 Application 生成统一 now。

避免一个授权请求内：

```text
多个 NOW()
```

导致边界时间不一致。

---

# 135. Database NOW

批量 Job 可以：

```text
DB NOW(3)
```

但主业务授权生命周期：

推荐：

```text
Application Clock
```

统一。

---

# 136. Transaction Isolation

默认：

```text
READ COMMITTED
```

或组织统一 MySQL 事务隔离级别。

需要更强时：

```text
显式 row lock/CAS
```

不通过全局提高 Serializable。

---

# 137. Read Replica

授权安全热查询：

```text
禁止使用高延迟 replica
```

Audit / Analytics：

```text
可使用 replica
```

---

# 138. SQL Comment / Trace

可选给关键 SQL 加：

```text
trace hint/comment
```

但注意不要把：

```text
user sensitive data
```

写进 SQL comment。

---

# 139. Query Metrics

至少：

```text
repository latency
query latency
rows returned
slow query count
optimistic conflict count
duplicate key count
```

---

# 140. Persistence Failure Strategy

DB timeout：

```text
Mutation fail
```

Authorization safety query：

```text
无法安全确认 → fail closed
```

Audit query：

```text
503/partial unavailable
```

不影响实时授权。

---

# 141. Bulk Permission Update

大量 RolePermission：

```text
diff
batch insert/update
version once per logical command
outbox state-carrying event
```

避免每条：

```text
version++
event
```

造成事件风暴。

---

# 142. Bulk Team Member Import

大批量：

```text
Batch Task
```

不是一个 HTTP 事务处理 10 万条。

每个 Batch：

```text
local tx
```

整体：

```text
task progress
```

---

# 143. Bulk Share

V1 若支持批量分享：

```text
每个 resource instance
```

拥有独立业务幂等/结果。

不能一个失败回滚 10 万个远程资源检查。

---

# 144. Query Model Release Gate

进入代码阶段后，以下查询必须先定义 Query Contract 再写 SQL：

```text
authorization grant resolution
user authorization overview
team tree
share list
shared-with-me
data permission list
ACL exists/list
audit cursor
outbox claim
refresh rotate
```

---

# 145. Repository Release Gate

禁止出现：

```text
Controller → Mapper
Application → BaseMapper
Domain → MyBatis
Domain Entity @TableName
Business Service cross-database query
```

任一出现：

```text
Architecture Test FAIL
```

---

# 146. SPEC 29 冻结结论

V1.0 持久化层正式采用：

```text
Domain Repository for Writes/Invariants
+
Query Ports for Read Models
+
MyBatis Infrastructure Adapters
+
Explicit Tenant Contracts
+
Seek Pagination for Large Tables
+
Optimistic Lock by Default
+
Pessimistic Lock Only for Narrow Critical Sections
+
Data Permission at SQL Boundary
+
Field Update Guard at SQL Boundary
+
No Cross-DB Access
+
No Java Post-Filtering
+
No N+1 Authorization Runtime Queries
```

后续生成 Mapper / Repository 时，必须先回答：

```text
这个 SQL 服务哪个业务闭环？
它的 Tenant 边界是什么？
它是否会被 Data Permission 拦截？
它需要哪个索引？
它的并发控制是什么？
它属于 Write Model 还是 Query Model？
```

如果回答不清楚，就不应该进入实现。
