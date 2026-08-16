# Enterprise IAM & Dynamic Authorization Platform
## 28 — Database Physical Schema & Flyway Migration Plan SPEC 1.0

> 本文把前述逻辑数据库设计正式冻结为 V1.0 物理 Schema、表归属、主键、唯一约束、版本字段、状态字段、索引策略与 Flyway Migration 计划。
>
> 本 SPEC 的目标不是替代最终 SQL，而是保证后续生成 Flyway DDL 时不会再次出现：
>
> - 同一张表被多个服务拥有；
> - 逻辑删除唯一索引错误；
> - Permission / TeamRole / Share 绑定关系含义不清；
> - Resource Share 使用错误的 resource_type_id；
> - Infrastructure Table 被错误中央化；
> - 缺少版本字段导致无法立即撤权；
> - 缺少索引导致 Data Scope / ACL / Audit 查询无法上线。

---

# 1. 数据库总体策略

开发环境：

```text
1 MySQL Instance
+
Multiple Databases
```

生产环境：

```text
Logical Database Per Service
```

V1.0 数据库：

```text
iam_auth
iam_identity
iam_organization
iam_authorization
iam_sharing
iam_audit
iam_job
```

禁止：

```text
跨 Database JOIN
```

即使开发阶段它们位于同一个 MySQL Server。

---

# 2. MySQL 基线

要求：

```text
MySQL 8.4 LTS+
InnoDB
utf8mb4
UTC
DATETIME(3)
```

默认：

```text
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
```

---

# 3. 主键策略

业务表统一：

```text
BIGINT
```

Java：

```text
Long
```

生成：

```text
Snowflake / MyBatis-Plus ASSIGN_ID
```

禁止依赖：

```text
跨服务数据库 auto_increment 顺序
```

事件：

```text
event_id VARCHAR(64/128)
```

使用：

```text
ULID / UUID
```

---

# 4. Tenant 字段

所有 Tenant Domain 数据表：

```text
tenant_id BIGINT NOT NULL
```

任何唯一业务键：

```text
tenant_id
```

必须进入唯一索引。

例如：

```text
UNIQUE(tenant_id, role_code, delete_marker)
```

不能：

```text
UNIQUE(role_code)
```

---

# 5. 通用审计字段

Metadata / Aggregate 推荐：

```text
id
tenant_id
created_at
updated_at
version
```

需要操作人时：

```text
created_by
updated_by
```

注意：

```text
created_by/updated_by
```

不是安全审计日志的替代品。

---

# 6. Version 字段

涉及并发状态或权限事实的表：

```text
version BIGINT NOT NULL DEFAULT 0
```

用于：

```text
Optimistic Lock
State Transition CAS
```

---

# 7. Delete Marker

可复用 Code 的 Metadata：

```text
delete_marker BIGINT NOT NULL DEFAULT 0
```

Active：

```text
0
```

删除：

```text
delete_marker = id
```

避免：

```text
UNIQUE(code, deleted)
```

在多次逻辑删除时冲突。

---

# 8. 状态事实不做逻辑删除

例如：

```text
ResourceShare
RefreshToken
Session
TemporaryGrant
```

使用状态：

```text
REVOKED
EXPIRED
DISABLED
```

保留完整历史。

---

# 9. JSON 使用边界

允许 JSON：

```text
condition expression AST
safe attributes
event payload
audit before/after
```

禁止 JSON 存：

```text
roleIds
teamIds
permissionIds
fieldIds
```

这类关系必须正规关系表。

---

# 10. iam_identity 数据库

Source of Truth：

```text
Tenant
User
UserIdentity
Role
UserRole
```

---

# 11. iam_tenant

关键字段：

```text
id
tenant_code
tenant_name
status
delete_marker
version
created_at
updated_at
```

唯一：

```text
uk_tenant_code(tenant_code, delete_marker)
```

平台级 tenant_code 唯一，不再加 tenant_id。

---

# 12. iam_user

字段：

```text
id
tenant_id
username
display_name
email
phone
status
delete_marker
version
created_at
updated_at
```

唯一：

```text
uk_user_username(
 tenant_id,
 username,
 delete_marker
)
```

可选：

```text
uk_user_email
```

是否启用取决于租户策略。

---

# 13. iam_user_identity

字段：

```text
id
tenant_id
user_id
identity_type
identity_key
credential_ref
status
version
```

唯一：

```text
tenant_id
identity_type
identity_key
delete_marker
```

`identity_key` 对邮箱/手机号建议存：

```text
normalized value / hash depending requirement
```

---

# 14. iam_role

字段：

```text
id
tenant_id
role_code
role_name
status
description
priority(optional metadata)
delete_marker
version
```

唯一：

```text
tenant_id
role_code
delete_marker
```

---

# 15. iam_user_role

关系：

```text
User N:N Role
```

字段：

```text
id
tenant_id
user_id
role_id
status
start_time
expire_time
version
```

唯一 Active 关系：

推荐：

```text
active_slot TINYINT NULL
```

Active：

```text
active_slot=1
```

Inactive：

```text
NULL
```

唯一：

```text
tenant_id,user_id,role_id,active_slot
```

或者采用：

```text
status + revoke generation
```

最终 SQL 生成阶段统一选择 active_slot。

---

# 16. iam_organization 数据库

拥有：

```text
iam_organization
iam_team
iam_team_member
iam_team_role
iam_team_member_role
```

---

# 17. iam_organization

字段：

```text
id
tenant_id
org_code
org_name
parent_id
materialized_path
status
delete_marker
version
```

索引：

```text
tenant_id,parent_id
tenant_id,materialized_path prefix
```

V1 使用：

```text
Materialized Path
```

大型组织未来 Closure Table。

---

# 18. iam_team

字段：

```text
id
tenant_id
organization_id
parent_team_id
team_code
team_name
materialized_path
status
delete_marker
version
```

唯一：

```text
tenant_id,team_code,delete_marker
```

---

# 19. iam_team_member

字段：

```text
id
tenant_id
team_id
user_id
membership_status
joined_at
left_at
version
```

Active Membership 唯一：

```text
tenant_id,team_id,user_id,active_slot
```

---

# 20. iam_team_role

Team 内角色定义：

```text
id
tenant_id
team_id
role_code
role_name
status
delete_marker
version
```

唯一：

```text
tenant_id,team_id,role_code,delete_marker
```

---

# 21. iam_team_member_role

字段：

```text
id
tenant_id
team_id
user_id
team_role_id
status
start_time
expire_time
version
```

唯一 Active：

```text
tenant_id,team_id,user_id,team_role_id,active_slot
```

---

# 22. iam_authorization 数据库

核心表：

```text
Application
Service
Resource
Operation
Permission
RolePermission
TeamRolePermission
API Mapping
DataScope
FieldPolicy
DirectGrant
TemporaryGrant
ConditionPolicy
PermissionVersion
Snapshot
```

---

# 23. iam_application

字段：

```text
id
tenant_id
app_code
app_name
status
delete_marker
version
```

唯一：

```text
tenant_id,app_code,delete_marker
```

---

# 24. iam_service

这里是：

```text
Logical IAM Service Metadata
```

不是 Nacos 实例。

字段：

```text
id
tenant_id
application_id
service_code
service_name
status
delete_marker
version
```

唯一：

```text
tenant_id,application_id,service_code,delete_marker
```

---

# 25. iam_resource

字段：

```text
id
tenant_id
application_id
service_id
resource_code
resource_name
resource_type
status
sharing_enabled
delete_marker
version
```

`resource_type`：

```text
BUSINESS
MENU
PAGE
BUTTON
DATASET
```

---

# 26. iam_operation

字段：

```text
id
tenant_id
operation_code
operation_name
risk_level
status
delete_marker
version
```

Operation 可租户级复用。

如果希望每 Application 独立 operation code：

最终 DDL 可加入：

```text
application_id
```

V1 推荐租户内动态 operation definition。

---

# 27. iam_resource_operation

字段：

```text
id
tenant_id
resource_id
operation_id
enabled
version
```

唯一：

```text
tenant_id,resource_id,operation_id
```

---

# 28. iam_permission

Permission 是：

```text
Resource + Operation entitlement definition
```

字段：

```text
id
tenant_id
resource_id
operation_id
effect_default(optional)
status
version
```

唯一：

```text
tenant_id,resource_id,operation_id
```

---

# 29. iam_role_permission

这是：

```text
Grant Binding
```

不仅是关系表。

字段：

```text
id
tenant_id
role_id
permission_id
effect
priority
condition_policy_id
status
start_time
expire_time
version
```

DataScope / FieldPolicy：

```text
必须引用 role_permission_id
```

而不是直接：

```text
role_id
```

否则无法区分同一个 Role 对不同 Operation 的策略。

---

# 30. iam_team_role_permission

字段：

```text
id
tenant_id
team_role_id
permission_id
effect
priority
condition_policy_id
status
start_time
expire_time
version
```

DataScope / FieldPolicy：

```text
引用 team_role_permission_id
```

---

# 31. iam_api_definition

技术 API：

```text
id
tenant_id
service_id
http_method
path_pattern
handler_signature
discovery_status
last_seen_at
version
```

唯一：

```text
tenant_id,service_id,http_method,path_pattern
```

状态：

```text
DISCOVERED_UNMAPPED
MAPPED
STALE
DISABLED
```

---

# 32. iam_api_resource_mapping

V1：

```text
one API → one primary resource + operation
```

字段：

```text
id
tenant_id
api_definition_id
resource_id
operation_id
mapping_status
version
```

唯一：

```text
tenant_id,api_definition_id
```

---

# 33. iam_api_security_policy

字段：

```text
id
tenant_id
api_definition_id
security_policy
idempotency_required
risk_level
version
```

security_policy：

```text
PUBLIC
AUTH_REQUIRED
INTERNAL_ONLY
```

---

# 34. iam_resource_data_schema

字段：

```text
id
tenant_id
resource_id
service_id
datasource_key
schema_name
table_name
primary_key_column
owner_user_column
owner_team_column
tenant_column
status
version
```

所有 identifier：

```text
必须做安全格式校验
```

不允许 raw SQL。

---

# 35. iam_data_scope

字段：

```text
id
tenant_id
scope_code
scope_name
scope_type
custom_policy_id
status
delete_marker
version
```

scope_type：

```text
ALL
SELF
TEAM
TEAM_AND_CHILDREN
SPECIFIED_TEAM
SHARED
CUSTOM_POLICY
```

---

# 36. iam_role_data_scope

字段：

```text
id
tenant_id
role_permission_id
data_scope_id
merge_mode
version
```

唯一：

```text
tenant_id,role_permission_id,data_scope_id
```

---

# 37. iam_team_role_data_scope

字段：

```text
id
tenant_id
team_role_permission_id
data_scope_id
merge_mode
version
```

---

# 38. Specific Team Binding

拆成两张明确表：

```text
iam_role_data_scope_team
iam_team_role_data_scope_team
```

前者：

```text
role_data_scope_id
team_id
include_children
```

后者：

```text
team_role_data_scope_id
team_id
include_children
```

---

# 39. iam_resource_field

字段：

```text
id
tenant_id
resource_id
field_code
property_path
column_name
data_type
sensitive_level
system_managed
discovery_status
default_mask_strategy_id
status
version
```

唯一：

```text
tenant_id,resource_id,field_code
```

---

# 40. iam_mask_strategy

字段：

```text
id
tenant_id
strategy_code
strategy_type
config_json
status
delete_marker
version
```

类型：

```text
PHONE
EMAIL
ID_CARD
BANK_CARD
NAME
ADDRESS
GENERIC_PARTIAL
```

---

# 41. iam_field_policy

字段：

```text
id
tenant_id
resource_id
operation_id
field_id
readable
writable
hidden
mask_strategy_id
priority
status
version
```

这是：

```text
可复用 Field Policy Definition
```

---

# 42. iam_role_field_policy

字段：

```text
id
tenant_id
role_permission_id
field_policy_id
version
```

唯一：

```text
tenant_id,role_permission_id,field_policy_id
```

---

# 43. iam_team_role_field_policy

字段：

```text
id
tenant_id
team_role_permission_id
field_policy_id
version
```

---

# 44. iam_condition_policy

存安全 DSL AST：

```text
id
tenant_id
policy_code
policy_name
expression_json
status
delete_marker
version
```

禁止：

```text
SpEL
MVEL
Groovy
JavaScript
raw SQL
```

---

# 45. iam_direct_grant

V1 用户级：

```text
subject_type = USER
```

字段：

```text
id
tenant_id
user_id
permission_id
effect
priority
condition_policy_id
start_time
expire_time
status
version
```

---

# 46. iam_temporary_grant

字段类似 DirectGrant：

```text
id
tenant_id
user_id
permission_id
effect
priority
condition_policy_id
start_time
expire_time
status
grant_reason
version
```

区别：

```text
TemporaryGrant expire_time required
```

---

# 47. iam_permission_version

泛型版本：

```text
id
tenant_id
subject_type
subject_id
version_value
updated_at
```

subject_type：

```text
USER
ROLE
TEAM
TEAM_ROLE
RESOURCE
APPLICATION
API_MAPPING
```

唯一：

```text
tenant_id,subject_type,subject_id
```

通过 CAS：

```text
version_value = version_value + 1
```

---

# 48. iam_authorization_snapshot

用于：

```text
历史权限对比
```

字段：

```text
id
tenant_id
user_id
snapshot_version
snapshot_json
reason
created_at
```

Snapshot：

```text
不是运行时授权事实源
```

---

# 49. iam_sharing 数据库

核心：

```text
iam_resource_share
iam_resource_share_operation
iam_resource_share_field
iam_resource_share_history
iam_resource_share_basis
iam_resource_sharing_policy
```

---

# 50. iam_resource_share

正式字段：

```text
id
tenant_id
resource_id
resource_instance_key

target_type
target_id

creator_user_id

parent_share_id
root_share_id
share_depth

status
start_time
expire_time
can_reshare

active_slot

revoke_reason
revoked_by
revoked_at

version
created_at
updated_at
```

注意：

```text
resource_id
```

不是：

```text
resource_type_id
```

---

# 51. Share Active Unique

同：

```text
resource
instance
target
```

最多一个 WAITING/ACTIVE Share。

采用：

```text
active_slot
```

WAITING/ACTIVE：

```text
1
```

EXPIRED/REVOKED：

```text
NULL
```

唯一：

```text
tenant_id,
resource_id,
resource_instance_key,
target_type,
target_id,
active_slot
```

---

# 52. iam_resource_share_operation

字段：

```text
id
tenant_id
share_id
operation_id
version
```

唯一：

```text
tenant_id,share_id,operation_id
```

---

# 53. iam_resource_share_field

字段：

```text
id
tenant_id
share_id
operation_id
field_id
readable
writable
hidden
mask_strategy_id
version
```

唯一：

```text
tenant_id,share_id,operation_id,field_id
```

---

# 54. iam_resource_share_history

Append-only：

```text
id
tenant_id
share_id
action_type
operator_type
operator_id
before_json
after_json
trace_id
created_at
```

不得 UPDATE。

---

# 55. iam_resource_share_basis

记录 Grant Basis：

```text
id
tenant_id
share_id
basis_source_type
basis_source_id
basis_permission_version
parent_share_id
created_at
```

用于：

```text
Reshare
Owner Transfer
Explain
Audit
```

---

# 56. iam_resource_sharing_policy

资源级：

```text
id
tenant_id
resource_id
enabled
allowed_target_types
max_share_duration_seconds
max_reshare_depth
default_can_reshare
owner_transfer_policy
version
```

allowed_target_types 可 JSON：

因为：

```text
这是枚举配置，不是关系实体集合
```

---

# 57. Share Projection Epoch

V1 正式增加：

```text
iam_share_projection_epoch
```

Sharing DB：

```text
id
tenant_id
resource_id
epoch
version
```

每次 ACL 安全事实变化：

```text
epoch++
```

事件携带：

```text
projectionEpoch
```

用于解决：

```text
Projection stale revoke window
```

---

# 58. Business Local ACL Projection

此表不在 IAM 中央数据库。

每个需要 SHARED Data Scope 的业务数据库可拥有：

```text
iam_resource_acl_projection
```

字段：

```text
id
tenant_id
share_id
resource_id
resource_instance_key
subject_type
subject_id
operation_id
start_time
expire_time
status
share_version
projection_epoch
```

索引：

```text
tenant_id,resource_id,subject_type,subject_id,operation_id
```

以及：

```text
tenant_id,resource_id,resource_instance_key,operation_id
```

---

# 59. ACL Projection Checkpoint

业务库：

```text
iam_acl_projection_checkpoint
```

字段：

```text
tenant_id
resource_id
last_contiguous_epoch
status
updated_at
```

如果：

```text
checkpoint < expected epoch
```

SHARED branch：

```text
Fail Closed / Under-grant
```

不能继续使用旧 ACL ALLOW。

---

# 60. iam_auth 数据库

核心：

```text
iam_login_session
iam_refresh_token
iam_user_security_state
reset/invite token
```

---

# 61. iam_login_session

字段：

```text
id
tenant_id
session_id
user_id
status
device_id
device_type
user_agent_hash/details
login_ip
last_access_at
idle_expire_at
absolute_expire_at
last_strong_auth_at
session_version
created_at
updated_at
```

唯一：

```text
session_id
```

---

# 62. iam_refresh_token

字段：

```text
id
tenant_id
user_id
session_id
token_family_id
token_hash
parent_token_id
replaced_by_token_id
status
issued_at
expire_at
rotated_at
revoked_at
revoke_reason
version
```

唯一：

```text
token_hash
```

只存：

```text
SHA-256 hash
```

不存明文 Refresh Token。

---

# 63. iam_user_security_state

字段：

```text
id
tenant_id
user_id
token_version
password_version
login_failure_count
lock_until
last_password_change_at
version
```

唯一：

```text
tenant_id,user_id
```

---

# 64. One-Time Token

可以统一：

```text
iam_one_time_security_token
```

用途：

```text
PASSWORD_RESET
INVITE
EMAIL_VERIFY
```

字段：

```text
token_hash
token_type
user_id
status
expire_at
used_at
```

只能一次消费。

---

# 65. iam_audit 数据库

日志表：

```text
iam_login_audit_log
iam_admin_audit_log
iam_permission_change_log
iam_authorization_log
iam_resource_access_log
iam_sensitive_field_access_log
iam_security_event
iam_infrastructure_operation_log
```

---

# 66. Audit 主键

仍可使用：

```text
BIGINT Snowflake
```

查询核心：

```text
tenant_id + created_at + id
```

支持 Seek Pagination。

---

# 67. iam_admin_audit_log

字段：

```text
id
tenant_id
operator_user_id
operation_type
target_type
target_id
before_data
after_data
risk_level
trace_id
created_at
```

索引：

```text
tenant_id,operator_user_id,created_at
tenant_id,target_type,target_id,created_at
```

---

# 68. iam_permission_change_log

字段：

```text
subject_type
subject_id
resource_id
operation_id
change_type
before_data
after_data
permission_version_before
permission_version_after
operator_user_id
trace_id
created_at
```

---

# 69. iam_authorization_log

高吞吐表。

字段：

```text
tenant_id
user_id
resource_id
operation_id
resource_instance_key
decision
decision_code
source_type
source_id
permission_version
cache_level
elapsed_ms
decision_id
trace_id
created_at
```

重点索引：

```text
tenant_id,user_id,created_at,id
tenant_id,resource_id,operation_id,created_at,id
tenant_id,decision,created_at,id
decision_id
trace_id
```

---

# 70. iam_security_event

字段：

```text
event_id
tenant_id
user_id
event_type
severity
resource_type
resource_id
event_data
event_status
trace_id
created_at
acknowledged_at
resolved_at
version
```

---

# 71. iam_job 数据库

IAM Job 自身业务追踪：

```text
sys_job_business_record
```

PowerJob Server：

```text
使用自己的 powerjob database
```

不能与 IAM Job Business Record 混为一张表。

---

# 72. sys_job_business_record

字段：

```text
id
job_type
business_key
business_version
execution_status
retry_count
lease_owner
lease_until
last_error
first_execute_at
last_execute_at
completed_at
version
```

唯一：

```text
job_type,business_key
```

---

# 73. Infrastructure Table — sys_outbox_event

每个产生事件的服务 Schema 本地存在。

字段：

```text
id
event_id
tenant_id
aggregate_type
aggregate_id
aggregate_version
event_type
schema_version
exchange_name
routing_key
payload
event_status
retry_count
next_retry_at
claim_owner
claim_until
last_error
created_at
published_at
version
```

唯一：

```text
event_id
```

核心索引：

```text
event_status,next_retry_at,id
```

---

# 74. Infrastructure Table — sys_idempotency_record

每个需要 HTTP 幂等的服务本地存在。

建议字段：

```text
id
tenant_id
actor_type
actor_id
api_definition_key
request_method
request_path
key_hash
request_hash
process_status
owner_token
lease_until
attempt_count
response_status
response_body
response_reference
response_replayable
retryable
error_code
expire_at
created_at
updated_at
version
```

不建议存原始：

```text
Idempotency-Key
```

可以仅存：

```text
key_hash
```

---

# 75. Idempotency Unique

正式推荐：

```text
tenant_id,
actor_type,
actor_id,
api_definition_key,
key_hash
```

比单纯 path 更稳定。

`api_definition_key` 可以是：

```text
apiDefinitionId
```

或服务内 stable API key。

---

# 76. Infrastructure Table — sys_message_consume_record

每个 Consumer Service 本地存在。

简单本地事务 Consumer 推荐只保存成功记录：

```text
id
event_id
consumer_group
event_type
aggregate_type
aggregate_id
aggregate_version
consumed_at
```

唯一：

```text
event_id,consumer_group
```

失败事务 rollback：

```text
consume record 一起 rollback
```

Broker Retry 即可。

---

# 77. 长处理 Consumer

如果 Consumer 不能在短事务中完成：

可扩展：

```text
PROCESSING
SUCCESS
FAILED
lease
```

但 V1 普通 Projection Consumer：

```text
短本地事务
```

优先。

---

# 78. Infrastructure Table Migration Ownership

每个服务的 Flyway：

```text
自己创建自己的 sys_* tables
```

不能建立：

```text
shared_infrastructure_db
```

---

# 79. 索引总体原则

索引优先服务：

```text
Tenant Isolation
Permission Lookup
Data Scope
ACL
Expiry Scan
Outbox Dispatch
Audit Seek Pagination
```

不追求：

```text
每列一个索引
```

---

# 80. Data Scope 业务表索引要求

业务 Resource 如果支持 TEAM：

至少有：

```text
tenant_id
owner_team_id
```

联合索引。

SELF：

```text
tenant_id
owner_user_id
```

SHARED：

通过：

```text
ACL Projection
```

而不是业务表存 share user ids。

---

# 81. Share Expiry Index

Sharing：

```text
status
expire_time
id
```

支持 PowerJob Seek：

```text
WHERE status IN (...)
AND expire_time <= ?
AND id > ?
ORDER BY id
LIMIT ?
```

---

# 82. Temporary Grant Expiry

Authorization：

```text
status
expire_time
id
```

同样。

---

# 83. Session Cleanup Index

Auth：

```text
status
absolute_expire_at
id
```

Refresh：

```text
status
expire_at
id
```

---

# 84. Audit Pagination Index

统一：

```text
tenant_id
created_at
id
```

不要深分页 OFFSET。

---

# 85. Foreign Key 策略

微服务数据库：

```text
不使用跨服务 FK
```

同一服务内部：

V1 仍推荐：

```text
逻辑 FK + Index
```

而不是大量物理 FK。

理由：

```text
迁移灵活
批处理性能
分库扩展
```

但应用层/测试必须保证完整性。

---

# 86. Database Naming

表：

```text
iam_*
sys_*
```

`iam_`：

```text
领域事实
```

`sys_`：

```text
技术基础设施
```

---

# 87. Flyway 总原则

每个服务：

```text
src/main/resources/db/migration
```

是唯一可执行 Schema Source。

禁止：

```text
手工改生产表
然后忘记 migration
```

---

# 88. Migration Version 命名

推荐：

```text
V1__baseline.sql
V2__xxx.sql
```

因为每服务独立数据库：

```text
每个服务可以从 V1 自己开始
```

无需全平台统一数字。

---

# 89. Identity Flyway Plan

```text
V1__identity_baseline.sql
  iam_tenant
  iam_user
  iam_user_identity
  iam_role
  iam_user_role

V2__identity_infrastructure.sql
  sys_outbox_event
  sys_idempotency_record
  sys_message_consume_record

V3__identity_indexes.sql
```

---

# 90. Organization Flyway Plan

```text
V1__organization_baseline.sql
  iam_organization
  iam_team
  iam_team_member
  iam_team_role
  iam_team_member_role

V2__organization_infrastructure.sql

V3__organization_indexes.sql
```

---

# 91. Authorization Flyway Plan

建议拆得更细：

```text
V1__authorization_resource_model.sql

V2__authorization_grant_model.sql

V3__authorization_data_scope.sql

V4__authorization_field_permission.sql

V5__authorization_api_mapping.sql

V6__authorization_direct_temporary_grant.sql

V7__authorization_permission_version.sql

V8__authorization_snapshot.sql

V9__authorization_infrastructure.sql

V10__authorization_indexes.sql
```

---

# 92. Sharing Flyway Plan

```text
V1__sharing_baseline.sql

V2__sharing_policy_basis_history.sql

V3__sharing_projection_epoch.sql

V4__sharing_infrastructure.sql

V5__sharing_indexes.sql
```

---

# 93. Auth Flyway Plan

```text
V1__auth_session.sql

V2__auth_refresh_token.sql

V3__auth_user_security_state.sql

V4__auth_one_time_token.sql

V5__auth_infrastructure.sql

V6__auth_indexes.sql
```

---

# 94. Audit Flyway Plan

```text
V1__audit_login_admin.sql

V2__audit_permission_authorization.sql

V3__audit_resource_sensitive.sql

V4__audit_security_infrastructure.sql

V5__audit_infrastructure_tables.sql

V6__audit_indexes.sql
```

---

# 95. Job Flyway Plan

```text
V1__job_business_record.sql

V2__job_infrastructure.sql

V3__job_indexes.sql
```

---

# 96. Business Service Local Projection Flyway

任何接入 SHARED Data Permission 的业务服务：

```text
Vx__iam_acl_projection.sql
```

创建：

```text
iam_resource_acl_projection
iam_acl_projection_checkpoint
sys_message_consume_record
```

属于：

```text
Business Service 自己的数据库
```

---

# 97. Migration 执行顺序

由于各服务数据库无物理 FK：

技术上可以并行初始化。

但完整 Demo：

```text
Identity
Organization
Authorization
Sharing
Auth
Audit
Job
```

均完成 migration 后再执行：

```text
Bootstrap Seed
```

---

# 98. Seed Data 与 Migration 分离

DDL Migration：

```text
只负责 Schema
```

业务初始数据：

```text
Bootstrap Application Service
```

负责。

不要在 Flyway 写大量：

```text
INSERT admin permission...
```

导致不同环境语义僵化。

---

# 99. 必要基础枚举 Seed

如果存在技术级固定枚举：

优先：

```text
代码 Enum + DB value contract
```

不要求作为独立字典表。

Operation：

```text
不是固定枚举
```

必须业务动态创建。

---

# 100. Bootstrap Data

系统首次启动创建：

```text
Platform internal application
Infrastructure internal resource metadata
```

可由：

```text
Bootstrap Runner
```

以幂等方式执行。

必须可重复运行。

---

# 101. Flyway Production Strategy

生产：

```text
Migration Job / Pipeline
```

优先执行。

应用：

```text
Flyway validate
```

或小规模场景自动 migration。

大型 migration 不允许所有实例启动时抢执行。

---

# 102. Expand / Contract

字段变更：

```text
Expand
→ compatible app
→ migrate data
→ switch
→ Contract
```

禁止直接：

```text
rename/drop column
```

破坏滚动发布。

---

# 103. Migration Compatibility

新旧服务实例短时共存。

Migration 必须保证：

```text
old app works
new app works
```

至少一个发布窗口。

---

# 104. Large Table Migration

Audit / ACL 大表未来修改：

避免：

```text
blocking ALTER
```

根据数据库能力选择：

```text
Online DDL
Shadow Table
Batch Backfill
```

V1 小规模可直接 migration，但架构预留。

---

# 105. Data Retention

核心 Metadata：

```text
长期保留
```

Share / Grant History：

```text
长期或合规周期
```

Session / Refresh：

```text
按安全策略清理
```

Authorization Log：

```text
热数据30~90天
```

最终由 Audit Retention Policy 配置。

---

# 106. Sensitive Data

User Email/Phone：

数据库：

```text
根据项目合规需求
```

可：

```text
application-level encryption
```

至少日志不得输出原值。

Credential：

```text
Auth DB / credential domain
```

密码：

```text
Argon2id/BCrypt hash
```

绝不加密后可逆存储。

---

# 107. Table Ownership Documentation

必须生成：

```text
docs/database/TABLE-OWNERSHIP.md
```

任何新增表 PR：

```text
必须指定 owner service
```

---

# 108. Migration Review Checklist

每个 Migration Review：

```text
Owner Service?
Tenant index?
Unique key includes tenant?
Version needed?
Status or logical delete?
Seek pagination index?
Expire scan index?
Cross-service FK?
JSON abused?
Rollback/compatibility?
```

---

# 109. SQL 安全

Migration 禁止：

```text
DROP DATABASE
TRUNCATE core production data
```

普通版本。

破坏性操作：

```text
单独人工审批
```

---

# 110. SQL Mode

生产开发应尽量保持一致：

```text
strict SQL mode
```

避免开发可插：

```text
非法日期
超长数据
```

生产却失败。

---

# 111. VARCHAR 长度原则

Code：

```text
64~128
```

Display Name：

```text
128~255
```

Resource Instance Key：

```text
128
```

Trace/Event IDs：

```text
64~128
```

不要所有字段：

```text
VARCHAR(255)
```

无脑统一。

---

# 112. TEXT / JSON

大 Audit Before/After：

```text
JSON
```

Outbox payload：

```text
JSON
```

Idempotency response：

```text
MEDIUMTEXT / BLOB
```

但限制最大大小。

---

# 113. Money / Decimal

IAM 核心无金额。

如果业务扩展字段：

```text
禁止 FLOAT/DOUBLE 表示金额
```

使用：

```text
DECIMAL
```

属于业务服务自身规范。

---

# 114. Boolean

MySQL：

```text
TINYINT(1)
```

Java：

```text
Boolean
```

状态机仍使用：

```text
VARCHAR enum code
```

不要大量：

```text
is_deleted/is_revoked/is_expired
```

同时存在导致冲突。

---

# 115. Status Column

推荐：

```text
VARCHAR(32)
```

而不是难读：

```text
0/1/2/3
```

IAM 状态审计/诊断场景：

```text
可读性优先
```

---

# 116. Enum 兼容

数据库状态值：

```text
只能新增
不要随意重命名
```

若重命名：

```text
migration + code compatible window
```

---

# 117. Table Comment

最终 DDL：

```text
所有业务表
关键字段
```

应有 COMMENT。

提高 DBA / 运维可读性。

---

# 118. Index Naming

统一：

```text
pk_*
uk_*
idx_*
```

MySQL 主键一般：

```text
PRIMARY KEY
```

唯一：

```text
uk_<table>_<meaning>
```

普通：

```text
idx_<table>_<meaning>
```

---

# 119. Projection Schema Rule

Projection 表可以：

```text
冗余字段
```

因为它是：

```text
读模型
```

但必须：

```text
可从事实源重建
```

不能反向写 Source of Truth。

---

# 120. Audit Schema Rule

Audit：

```text
Append Only
```

安全管理员的：

```text
ACK/RESOLVE
```

如果更新 Security Event 状态：

这是 Security Event workflow，不代表修改原 Audit Fact。

---

# 121. Database Backup Priority

最高：

```text
iam_identity
iam_organization
iam_authorization
iam_sharing
iam_auth
```

Audit：

```text
独立归档+备份
```

Job：

```text
可以从业务状态恢复较多信息
```

但仍备份。

---

# 122. Restore Test

每个 Release 前至少测试：

```text
Backup
→ New MySQL
→ Restore
→ Flyway Validate
→ Application Smoke
```

---

# 123. Physical Schema Acceptance

正式代码阶段生成 SQL 后必须：

```text
docker MySQL empty start
 ↓
Flyway migrate all
 ↓
no error
```

然后：

```text
previous version
→ latest
```

升级测试。

---

# 124. Query Plan Acceptance

以下必须跑 EXPLAIN：

```text
UserRole lookup
Team membership lookup
RolePermission resolve
TeamRolePermission resolve
Data Scope team lookup
Share instance lookup
Share expiry scan
ACL list filter
Authorization audit query
Outbox dispatch
```

---

# 125. Multi-Tenant Acceptance

所有 Tenant 表测试：

```text
tenant A / tenant B
相同 code
```

允许：

```text
各自存在
```

跨 tenant 查询：

```text
不得命中
```

---

# 126. Uniqueness Acceptance

并发 100 次：

```text
Create same active relation
```

最终：

```text
1 active relation
```

依赖：

```text
DB unique constraint
```

不是 Java 先查后插。

---

# 127. Share Uniqueness Acceptance

相同：

```text
resource + instance + target
```

并发创建 50 次：

```text
1 active Share
```

其它：

```text
idempotent response / business conflict
```

---

# 128. Permission Version Acceptance

并发多个权限变更：

```text
version monotonic
```

不可：

```text
lost update
```

通过：

```text
atomic increment / CAS
```

---

# 129. Flyway 文件实际目录

项目内：

```text
backend/
  iam-auth-service/
    src/main/resources/db/migration/

  iam-identity-service/
    src/main/resources/db/migration/

  iam-organization-service/
    src/main/resources/db/migration/

  iam-authorization-service/
    src/main/resources/db/migration/

  iam-sharing-service/
    src/main/resources/db/migration/

  iam-audit-service/
    src/main/resources/db/migration/

  iam-job-service/
    src/main/resources/db/migration/
```

---

# 130. CODE PHASE SQL 生成顺序

正式建议：

```text
1 identity DDL
2 organization DDL
3 authorization resource/grant DDL
4 auth DDL
5 sharing DDL
6 authorization data/field DDL
7 audit DDL
8 infrastructure sys tables
9 indexes
10 demo business ACL projection
```

---

# 131. 为什么不一次生成所有 SQL

因为：

```text
表关系复杂
索引需要结合真实查询
```

正确：

```text
一个闭环
→ 一组 DDL
→ Repository
→ Integration Test
→ EXPLAIN
```

然后继续下一组。

---

# 132. V1.0 Physical Schema Freeze

正式冻结以下事实：

```text
Database ownership
Table ownership
tenant_id strategy
version strategy
delete_marker strategy
active_slot strategy
resource_instance_key
role_permission binding
team_role_permission binding
share projection epoch
local infrastructure tables
Flyway per service
```

后续代码可以优化字段长度和非关键索引，但不得随意破坏上述语义。

---

# 133. SPEC 28 结论

本项目物理数据库设计必须服务于四个核心目标：

```text
Isolation
Correctness
Immediate Revocation
Operational Recoverability
```

不是单纯：

```text
“表设计规范”
```

最终 SQL 生成时必须以：

```text
业务闭环查询
安全边界
并发唯一性
事件一致性
恢复能力
```

反向验证每一张表和每一个索引。
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
