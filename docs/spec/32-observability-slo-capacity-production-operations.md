# Enterprise IAM & Dynamic Authorization Platform
## 32 — Observability, SLO, Capacity & Production Operations SPEC 1.0

> 本文冻结 Enterprise IAM V1.0 的可观测性、SLO、容量规划、告警策略、故障分级、生产运行手册与恢复机制。
>
> 目标：生产环境发生“授权慢、登录失败、Redis 抖动、MQ 堆积、ACL Projection 断层、Outbox 堆积、数据库变慢”等问题时，团队能够快速回答：
>
> ```text
> 发生了什么？
> 影响了谁？
> 是安全失败还是可用性失败？
> 当前是否存在越权风险？
> 哪个依赖导致？
> 是否正在扩大？
> 如何止损？
> 如何恢复？
> 如何验证恢复完成？
> ```

---

# 1. 可观测性总原则

正式冻结：

```text
Logs
+
Metrics
+
Traces
+
Audit
+
Security Events
+
Business Health
```

六个维度。

其中：

```text
Trace
```

回答：

```text
请求经过了哪里
```

Audit：

```text
谁做了什么
```

Security Event：

```text
是否发生攻击/高风险行为
```

三者不能混为一体。

---

# 2. Observability 技术基线

Java：

```text
Micrometer
OpenTelemetry
Structured Logging
Spring Boot Actuator
```

Collector：

```text
OpenTelemetry Collector
```

Metrics：

```text
Prometheus-compatible
```

Dashboard：

```text
Grafana-compatible
```

Logging：

```text
Loki / ELK / OpenSearch
```

可替换。

V1 不绑定单一商业平台。

---

# 3. Trace 标识

统一：

```text
traceId
spanId
```

授权决策：

```text
decisionId
```

领域事件：

```text
eventId
```

幂等请求：

```text
idempotencyKeyHash / idempotencyRecordId
```

这些需要可关联。

---

# 4. Trace 传播

链路：

```text
Browser
 ↓
Gateway
 ↓
Authorization
 ↓
Business Service
 ↓
MySQL
```

以及：

```text
Business
 ↓
Outbox
 ↓
RabbitMQ
 ↓
Consumer
```

MQ 事件必须继续携带：

```text
traceId
```

并在 Consumer 创建新 Span。

---

# 5. Structured Log 字段

最少：

```text
timestamp
level
service
environment
traceId
spanId
tenantId
actorType
userId
sessionIdHash
resourceId
operationId
decisionId
eventId
message
errorCode
```

不适用字段可为空。

---

# 6. 禁止日志字段

禁止：

```text
password
access token
refresh token
JWT private key
full credential
raw sensitive field values
secret
cookie
authorization header
```

---

# 7. Session 日志

Session：

```text
不直接打印完整 sessionId
```

可：

```text
hash / short fingerprint
```

用于排查。

---

# 8. 授权日志

Authorization Service 每次决策至少记录：

```text
decision
decisionCode
resourceId
operationId
cacheLevel
permissionVersion
latency
```

普通 ALLOW：

```text
可采样
```

DENY：

```text
100%
```

---

# 9. High-Risk Operation Logging

以下必须 100%：

```text
PUBLIC policy change
Role permission expansion
Field raw-read expansion
Share create/revoke
Force logout
DLQ replay
Projection rebuild
Security event resolution
```

---

# 10. Metric 命名规范

建议：

```text
iam_<domain>_<metric>
```

例如：

```text
iam_auth_login_total
iam_auth_refresh_total
iam_auth_refresh_reuse_total
iam_authz_decision_total
iam_authz_latency_seconds
iam_share_active_total
iam_outbox_pending_total
iam_projection_gap_total
```

---

# 11. 标签原则

标签允许：

```text
service
decision
decisionCode
cacheLevel
operationClass
result
```

禁止高基数：

```text
userId
resourceInstanceKey
traceId
shareId
```

作为 Metric Label。

---

# 12. Authentication Metrics

至少：

```text
iam_auth_login_total
iam_auth_login_failure_total
iam_auth_login_locked_total
iam_auth_session_active
iam_auth_refresh_total
iam_auth_refresh_failure_total
iam_auth_refresh_reuse_total
iam_auth_force_logout_total
```

---

# 13. Authorization Metrics

至少：

```text
iam_authz_decision_total
iam_authz_allow_total
iam_authz_deny_total
iam_authz_latency_seconds
iam_authz_cache_hit_total
iam_authz_cache_miss_total
iam_authz_fail_closed_total
iam_authz_explain_total
```

---

# 14. Data Permission Metrics

```text
iam_data_permission_plan_total
iam_data_permission_rewrite_total
iam_data_permission_rewrite_failure_total
iam_data_permission_fail_closed_total
iam_data_permission_sql_latency_seconds
```

---

# 15. Field Permission Metrics

```text
iam_field_write_denied_total
iam_field_hidden_total
iam_field_mask_total
iam_field_metadata_miss_total
iam_field_fail_closed_total
```

---

# 16. Sharing Metrics

```text
iam_share_create_total
iam_share_revoke_total
iam_share_expire_total
iam_share_escalation_denied_total
iam_share_active_total
iam_share_projection_lag
```

---

# 17. Projection Metrics

```text
iam_projection_event_total
iam_projection_duplicate_total
iam_projection_stale_event_total
iam_projection_gap_total
iam_projection_checkpoint_lag
iam_projection_reconcile_total
iam_projection_rebuild_total
```

---

# 18. Outbox Metrics

```text
iam_outbox_pending_total
iam_outbox_failed_total
iam_outbox_dead_total
iam_outbox_oldest_pending_seconds
iam_outbox_publish_latency_seconds
iam_outbox_retry_total
```

---

# 19. MQ Metrics

从 RabbitMQ 采集：

```text
queue depth
consumer count
publish rate
ack rate
redelivery
unacked
DLQ depth
```

---

# 20. Idempotency Metrics

```text
iam_idempotency_acquire_total
iam_idempotency_replay_total
iam_idempotency_conflict_total
iam_idempotency_processing_timeout_total
iam_idempotency_takeover_total
```

---

# 21. Job Metrics

```text
iam_job_execution_total
iam_job_failure_total
iam_job_retry_total
iam_job_duration_seconds
iam_job_stuck_total
iam_job_expired_share_scan_lag
```

---

# 22. Security Metrics

至少：

```text
iam_security_cross_tenant_attempt_total
iam_security_refresh_reuse_total
iam_security_internal_auth_failure_total
iam_security_share_escalation_total
iam_security_field_write_denied_total
iam_security_replay_detected_total
```

---

# 23. Infrastructure Metrics

MySQL：

```text
connections
active connections
slow query
lock wait
deadlock
buffer pool
replication lag if used
```

Redis：

```text
memory
evictions
ops/sec
latency
connected clients
keyspace hit ratio
```

RabbitMQ：

```text
queue depth
unacked
consumer utilization
disk alarm
memory alarm
```

Nacos：

```text
instance count
config failures
client reconnect
```

---

# 24. SLI

IAM 核心 SLI：

```text
Authentication Success Rate
Authorization Availability
Authorization Latency
Immediate Revocation Correctness
Projection Freshness
Outbox Delivery Latency
Audit Delivery Reliability
```

---

# 25. SLO 原则

SLO 是：

```text
服务目标
```

不是：

```text
代码常量
```

V1 提供推荐初始值，生产需通过压测校准。

---

# 26. Authorization Availability SLO

初始建议：

```text
99.95%
```

统计：

```text
可成功产生安全决策的受保护请求
```

不包含业务自身 4xx。

---

# 27. Authorization Latency SLO

初始目标：

```text
L1 Cache P99 < 10ms
Redis/ReadModel P99 < 30ms
DB fallback P99 < 150ms
```

这是平台目标，不是上线前绝对保证。

必须压测后修正。

---

# 28. Login SLO

初始目标：

```text
P95 < 500ms
P99 < 1000ms
```

不含：

```text
外部 MFA provider
```

慢调用。

---

# 29. Immediate Revocation SLO

这不是普通延迟 SLO。

安全要求：

```text
Commit 完成后
下一次新的受保护请求
不得使用旧 ALLOW
```

属于：

```text
Correctness SLO
```

不是“最终几秒内同步”。

---

# 30. Projection Freshness SLO

普通新增授权：

```text
P95 < 2s
P99 < 5s
```

可以作为初始目标。

撤权：

```text
不依赖 Projection Freshness
```

保证安全。

---

# 31. Outbox Delivery SLO

正常情况：

```text
P95 < 2s
P99 < 10s
```

告警：

```text
oldest pending > 30s
```

可作为初始 Warning。

---

# 32. Audit Delivery SLO

高风险审计：

```text
99.99% durable delivery
```

正常延迟：

```text
P95 < 5s
```

实时业务不依赖 Audit Service。

---

# 33. Error Budget

Authorization：

如果目标：

```text
99.95%
```

每月允许不可用预算约：

```text
21.6 分钟
```

具体计算由监控平台实现。

Security Correctness：

```text
没有“越权预算”
```

Cross-Tenant / Privilege Escalation：

```text
0 tolerance
```

---

# 34. Severity 分类

## SEV-0

```text
Confirmed cross-tenant data leak
Confirmed privilege escalation
JWT signing key compromise
Widespread unauthorized ALLOW
```

## SEV-1

```text
Authorization widespread outage
Login widespread outage
Redis session outage
Permission revocation unsafe
Critical MQ/projection inconsistency
```

## SEV-2

```text
Partial tenant impact
Audit delay
Outbox backlog
High latency
Security event spike
```

## SEV-3

```text
Minor degradation
non-critical admin page issue
single job failure
```

---

# 35. SEV-0 操作原则

第一目标：

```text
Stop Unauthorized Access
```

可以接受：

```text
Fail Closed / Partial Shutdown
```

不能为了 Availability：

```text
继续可疑 ALLOW
```

---

# 36. SEV-1 操作原则

目标：

```text
Restore Safe Service
```

优先：

```text
Gateway protection
Auth/session correctness
Authorization correctness
```

---

# 37. Alert 分类

必须避免：

```text
所有异常都发 Pager
```

分：

```text
Page
Ticket
Dashboard
```

---

# 38. Page Alerts

建议：

```text
cross tenant attempt spike
refresh reuse critical
authorization fail-open impossible invariant
authorization 5xx SLO burn
Redis session unavailable
projection checkpoint behind expected on critical resource
RabbitMQ disk alarm
MySQL unavailable
```

---

# 39. Ticket Alerts

```text
outbox oldest pending > threshold
audit backlog
non-critical DLQ
job retry increase
cache hit ratio degradation
```

---

# 40. Dashboard Only

```text
normal deny rate
cache size
routine login failures
ordinary share counts
```

---

# 41. Burn Rate Alerting

Authorization SLO：

推荐：

```text
fast burn
+
slow burn
```

两类。

避免只看：

```text
单分钟 error rate
```

造成抖动告警。

---

# 42. Capacity Planning

需要分别规划：

```text
Gateway
Auth
Authorization
Redis
MySQL
RabbitMQ
Audit
ACL Projection
```

---

# 43. Capacity 基准维度

至少：

```text
active users
concurrent sessions
requests/sec
authorization checks/sec
roles per user
teams per user
permissions per role
shares
audit events/sec
```

---

# 44. 初始容量基线

建议第一版压测目标：

```text
100k users
10k concurrent sessions
1k authz checks/sec
100 writes/sec IAM admin mutations
1M active shares
10M audit rows
```

这是工程压测基线，不代表业务承诺。

---

# 45. Authorization Scale Test

至少测试：

```text
L1 hit
Redis hit
DB fallback
batch authorization
50 roles/user
50 teams/user
100 operations/resource
```

---

# 46. Share Scale Test

至少：

```text
1M ACL Projection rows
```

测试：

```text
instance access
list shared query
revoke
checkpoint lag
```

---

# 47. Audit Scale Test

至少：

```text
10M rows
```

验证：

```text
cursor pagination
trace query
user query
resource query
```

---

# 48. MySQL Capacity

必须根据：

```text
service instance count
Hikari pool
max_connections
```

计算。

不能每个实例默认：

```text
100 connections
```

---

# 49. Redis Capacity

估算：

```text
session count
authorization cache
API mapping
replay protection
rate limit
```

必须设置：

```text
maxmemory policy
```

并验证不会因为业务缓存逐出安全关键 Session。

---

# 50. Redis Key Class

建议区分：

```text
security-critical
cache
rate-limit
replay
```

生产可：

```text
不同 Redis Cluster/Instance
```

隔离。

V1 开发环境可以同实例。

---

# 51. Session Redis

如果共享 Redis：

必须避免：

```text
allkeys-lru
```

无意逐出 Session。

生产建议：

```text
session/security store
```

单独规划。

---

# 52. RabbitMQ Capacity

考虑：

```text
peak publish rate
consumer rate
backlog recovery rate
message size
DLQ
```

要求恢复吞吐：

```text
明显高于正常生产速率
```

否则 backlog 永远追不上。

---

# 53. Outbox Backlog Recovery

例如正常：

```text
100 events/s
```

恢复 Consumer/Publisher 至少：

```text
300~500 events/s
```

才有收敛能力。

---

# 54. Audit Backlog Recovery

Audit Consumer 同理。

可以：

```text
临时水平扩容
```

不影响 Authorization。

---

# 55. Gateway Scaling

Gateway：

```text
无状态
```

扩容指标：

```text
CPU
request rate
P99 latency
connection count
```

---

# 56. Authorization Scaling

主要：

```text
CPU
authz QPS
cache miss
DB fallback rate
P99 latency
```

如果 P99 上升伴随：

```text
cache miss spike
```

优先检查：

```text
version churn/cache invalidation
```

---

# 57. Auth Scaling

指标：

```text
login rate
refresh rate
password hash CPU
Redis latency
DB lock contention
```

密码 Hash 属于 CPU 重操作。

---

# 58. Horizontal Scaling

核心服务：

```text
Gateway >=2
Authorization >=2
Auth >=2
```

生产最低推荐。

其它：

```text
根据流量
```

---

# 59. Deployment Health

每实例：

```text
liveness
readiness
startup
```

Readiness 失败：

```text
移出流量
```

不是：

```text
立即重启
```

---

# 60. Graceful Shutdown Observability

监控：

```text
shutdown start
in-flight requests
consumer in-flight
shutdown completed
```

---

# 61. Runbook — Authorization Latency High

排查顺序：

```text
1 Gateway latency
2 Authorization P99
3 cache hit ratio
4 Redis latency
5 DB fallback
6 MySQL slow query
7 permission version churn
8 batch auth usage
```

止损：

```text
scale authz
reduce heavy Explain/Simulator
protect DB
```

不能：

```text
fallback allow
```

---

# 62. Runbook — Authorization 5xx Spike

检查：

```text
Redis
MySQL
Nacos
Authorization instances
client timeout
```

安全策略：

```text
Fail Closed
```

必要时：

```text
temporarily reject protected traffic
```

---

# 63. Runbook — Redis Session Failure

现象：

```text
large AUTH_REQUIRED 503
```

检查：

```text
Redis connectivity
memory
eviction
cluster failover
```

禁止：

```text
disable session validation
```

恢复后：

```text
session integrity smoke test
```

---

# 64. Runbook — RabbitMQ Down

预期：

```text
business local tx works
outbox accumulates
```

检查：

```text
broker availability
disk/memory alarm
network
publisher confirms
```

恢复：

```text
observe backlog decreasing
projection checkpoint catch up
```

---

# 65. Runbook — Outbox Backlog

检查：

```text
oldest pending
retry reason
claim stuck
publisher rate
RabbitMQ confirm
```

必要：

```text
release expired claims
scale relay
```

---

# 66. Runbook — Projection Gap

发现：

```text
incoming version gap
checkpoint lag
```

操作：

```text
mark degraded
fail closed affected SHARED branch
trigger reconcile
verify source version
resume after checkpoint contiguous
```

---

# 67. Runbook — Share Revoke Not Reflected

第一检查：

```text
permission/share epoch
authorization expected epoch
local checkpoint
```

如果旧 Projection 仍 ACTIVE：

```text
checkpoint guard
```

应该已经阻止 ALLOW。

若仍 ALLOW：

```text
SEV-0
```

---

# 68. Runbook — Login Failure Spike

检查：

```text
credential attack?
identity DB?
Redis?
password hash saturation?
tenant status?
```

如果攻击：

```text
rate limit
risk rules
security event
```

---

# 69. Runbook — Refresh Reuse Spike

可能：

```text
client bug
multi-tab race
real token theft
```

先区分：

```text
grace-window concurrent retry
vs
true reuse
```

真实复用：

```text
security incident
```

---

# 70. Runbook — MySQL Slow

检查：

```text
slow log
EXPLAIN
locks
connections
buffer pool
IO
```

Authorization safety：

```text
无安全 cache → fail closed
```

不能 bypass DB guard。

---

# 71. Runbook — Nacos Down

已运行实例：

```text
可以短时间依赖本地缓存
```

检查：

```text
service discovery reconnect
config snapshot
```

禁止在故障期间进行大规模配置变更。

---

# 72. Runbook — Audit Backlog

Authorization：

```text
继续
```

Audit：

```text
scale consumers
check DB
check MQ lag
```

高价值审计：

```text
确认 Outbox/MQ 未丢
```

---

# 73. Runbook — DLQ Growth

分类：

```text
schema incompatibility
business conflict
DB failure
poison event
```

禁止：

```text
one-click replay all
```

先：

```text
sample
root cause
fix
controlled replay
```

---

# 74. Runbook — Security Event Spike

例如：

```text
cross-tenant
share escalation
internal signature failure
```

必须关联：

```text
source IP
user
tenant
trace
resource
```

决定：

```text
attack
misconfiguration
bug
```

---

# 75. Runbook — Permission Version Storm

大量管理员批量改权限可能：

```text
cache churn
DB pressure
```

优化：

```text
batch logical version increment
state-carrying event
debounced cache invalidation
```

安全不能降低。

---

# 76. Runbook — CPU High in Auth

常见：

```text
Argon2/BCrypt load
login attack
```

策略：

```text
rate limit
scale auth
protect hash worker pool
```

---

# 77. Runbook — Caffeine Cache Explosion

检查：

```text
instance-key cardinality
negative cache policy
TTL
maximumSize
```

立即：

```text
cap cache
```

而不是无限扩容。

---

# 78. Dashboard 1 — Executive Health

展示：

```text
Auth success
Authz availability
Authz P99
active sessions
critical security events
projection health
outbox backlog
```

---

# 79. Dashboard 2 — Authorization

```text
allow/deny
latency by cache level
cache hit
DB fallback
fail closed
top decision codes
```

---

# 80. Dashboard 3 — Authentication

```text
login success/failure
refresh success
reuse detection
active sessions
lockouts
```

---

# 81. Dashboard 4 — Consistency

```text
outbox pending
oldest pending
MQ lag
projection gaps
checkpoint lag
reconcile
DLQ
```

---

# 82. Dashboard 5 — Security

```text
cross tenant
share escalation
field deny
internal auth failure
replay
critical open events
```

---

# 83. Dashboard 6 — Database

按服务：

```text
connections
query latency
deadlock
slow query
rows
storage
```

---

# 84. Synthetic Monitoring

外部持续探测：

```text
Gateway health
Login synthetic user
Authorization deny scenario
Authorization allow scenario
```

不要使用真实用户凭证。

---

# 85. Security Synthetic Test

可周期验证：

```text
unknown API deny
spoofed header ignored
expired test grant deny
```

在专用测试 Tenant。

---

# 86. Production Smoke

每次部署后：

```text
health
login
authz allow
authz deny
role revoke test tenant
share revoke test tenant
MQ event flow
audit event
```

---

# 87. Canary / Rolling Verification

新实例进入流量后：

比较：

```text
error rate
authz latency
deny rate
security event
```

异常：

```text
rollback
```

---

# 88. Configuration Observability

记录：

```text
service version
git commit
config version
flyway version
```

方便判断：

```text
只有某一版本实例出错
```

---

# 89. Database Migration Monitoring

发布期间：

```text
migration start
migration success/failure
duration
schema version
```

失败：

```text
block deploy
```

---

# 90. Backup Monitoring

至少：

```text
last successful backup
backup size
checksum
restore drill date
```

---

# 91. Restore Drill SLO

建议：

```text
quarterly
```

至少执行一次真实恢复演练。

更高合规场景按组织要求调整。

---

# 92. RPO/RTO

建议初始目标：

核心 IAM Metadata：

```text
RPO <= 5 min
RTO <= 60 min
```

Audit：

```text
RPO <= 15 min
RTO <= 4h
```

这些是建议基线，实际必须由业务确认。

---

# 93. DR 原则

Redis：

```text
不是唯一事实源
```

Projection：

```text
可重建
```

Audit Archive：

```text
可校验
```

MySQL：

```text
Backup + Binlog/PITR
```

---

# 94. Capacity Review 周期

建议：

```text
每月
```

检查：

```text
user growth
session growth
authz QPS
share growth
audit storage
Redis usage
DB growth
```

---

# 95. Index Review

当：

```text
P95 query latency
```

持续超过目标：

先：

```text
EXPLAIN
slow query
index
query shape
```

而不是立刻加缓存。

---

# 96. Performance Regression Gate

Release 前：

```text
Authorization performance smoke
DataPermission query plan
ACL query
Audit cursor
```

若比基线退化：

```text
>20%
```

需要说明或阻断。

---

# 97. Load Test Profiles

至少：

```text
steady
spike
soak
failure recovery
```

---

# 98. Spike Test

模拟：

```text
5x login burst
5x authz QPS
```

观察：

```text
rate limit
thread pool
DB
Redis
```

---

# 99. Soak Test

至少：

```text
2~4h
```

观察：

```text
memory leak
connection leak
cache growth
queue growth
```

---

# 100. Failure Recovery Test

```text
RabbitMQ down 10 min
Redis failover
Authz instance kill
PowerJob stop
Audit stop
```

恢复后检查：

```text
backlog converges
no stale allow
```

---

# 101. On-Call 信息要求

每个告警必须包含：

```text
service
environment
severity
symptom
dashboard
runbook
recent deploy
trace/sample
```

避免：

```text
“CPU high”
```

无上下文告警。

---

# 102. Runbook 存放

```text
docs/operations/runbooks/
```

每个 SEV-1+ 告警：

```text
必须有 Runbook
```

---

# 103. Incident Timeline

事故中记录：

```text
detected
acknowledged
mitigated
resolved
verified
```

---

# 104. Postmortem

SEV-0/SEV-1：

```text
必须复盘
```

至少：

```text
impact
timeline
root cause
why controls failed
why detection worked/failed
corrective actions
test added
```

---

# 105. 安全事故复盘

必须回答：

```text
是否存在 unauthorized ALLOW？
是否影响跨 Tenant？
是否需要 revoke sessions/keys？
是否需要 rotate credentials？
是否需要通知/合规流程？
```

---

# 106. Operational Change Management

高风险生产操作：

```text
DLQ replay
Projection rebuild
Public API change
Key rotation
Bulk permission change
```

都应：

```text
audited
```

---

# 107. Readiness 规则

Authorization readiness：

```text
cannot safely authorize
→ NOT READY
```

Audit：

```text
ES unavailable
```

如果 MySQL ingest 正常：

```text
仍可 Ready
```

根据关键依赖区分。

---

# 108. Dependency Health Classification

```text
CRITICAL
DEGRADED
OPTIONAL
```

例如 Authorization：

```text
MySQL = CRITICAL
Redis = CRITICAL/DEGRADED depending safe path
Audit = OPTIONAL
```

---

# 109. Fail Closed Metric

所有因安全依赖不可用而拒绝：

```text
iam_authz_fail_closed_total
```

必须单独统计。

这样能区分：

```text
权限本来 DENY
vs
系统无法安全判断
```

---

# 110. Error Code Observability

所有错误码：

```text
metric count
```

但注意高基数：

```text
errorCode
```

是有限枚举，可做 Label。

---

# 111. Tenant-Level Health

不要给每个 Tenant 创建 metric label。

如果需要单 Tenant 排障：

```text
logs/audit/query
```

而不是 Prometheus 高基数标签。

---

# 112. Data Retention — Metrics

Metrics：

```text
15~30d high resolution
```

长期：

```text
downsample
```

按平台能力。

---

# 113. Data Retention — Logs

应用日志：

```text
7~30d
```

安全/合规日志：

```text
按策略更长
```

Audit 不等于普通应用日志。

---

# 114. Cost Control

Audit ALLOW 采样：

```text
普通低风险可采样
```

DENY/高风险：

```text
100%
```

减少成本但不牺牲安全。

---

# 115. Production Checklist

上线前确认：

```text
dashboards ready
alerts ready
runbooks ready
SLO queries ready
backup ready
restore tested
load baseline exists
security metrics visible
```

---

# 116. V1 Dashboard Minimum Set

必须至少：

```text
IAM Overview
Authentication
Authorization
Consistency
Security
Infrastructure
```

---

# 117. V1 Alert Minimum Set

必须至少：

```text
Authz availability burn
Authz P99 high
Redis session unavailable
MySQL unavailable
RabbitMQ alarm
Outbox oldest pending high
Projection gap/checkpoint lag
Critical security event
Refresh reuse
Cross-tenant attempt
```

---

# 118. V1 Runbook Minimum Set

必须至少：

```text
authorization-unavailable.md
authorization-latency.md
redis-session-failure.md
rabbitmq-outage.md
outbox-backlog.md
projection-gap.md
share-revoke-inconsistency.md
login-failure-spike.md
refresh-reuse-spike.md
mysql-slow.md
dlq-growth.md
security-event-spike.md
```

---

# 119. CODE PHASE Observability Gate

每个核心新能力开发时，必须同时补：

```text
metric
structured log
trace span
audit/security event if needed
runbook impact
```

不要等最后统一“加监控”。

---

# 120. SPEC 32 冻结结论

Enterprise IAM V1.0 生产运行正式采用：

```text
Measure
Detect
Protect
Recover
Verify
Learn
```

六阶段运行模型。

最终上线标准不是：

```text
服务启动成功
```

而是：

```text
能够发现异常
能够判断安全影响
能够快速止损
能够恢复一致性
能够验证恢复
能够通过复盘减少再次发生
```
