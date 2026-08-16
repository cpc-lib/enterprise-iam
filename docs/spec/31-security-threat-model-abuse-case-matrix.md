# Enterprise IAM & Dynamic Authorization Platform
## 31 — Security Threat Model & Abuse Case Matrix SPEC 1.0

> 本文对 Enterprise IAM V1.0 进行系统化威胁建模。
>
> 目标不是“列安全建议”，而是建立：
>
> ```text
> Asset
> → Trust Boundary
> → Threat
> → Abuse Case
> → Security Control
> → Detection
> → Test
> → Release Gate
> ```
>
> 所有高风险授权路径必须有对应自动化安全测试。

---

# 1. 安全目标

V1.0 必须保证：

```text
Confidentiality
Integrity
Availability
Authorization Correctness
Tenant Isolation
Non-Repudiation
Immediate Revocation
```

IAM 最核心安全目标不是“接口不报错”，而是：

```text
错误的 ALLOW 不能发生
```

---

# 2. 核心资产

需要保护：

```text
User Identity
Credentials
Session
Refresh Token
JWT Signing Key
Tenant Boundary
Role / Permission
Team / Membership
Data Scope
Field Policy
Resource Share
Authorization Decision
Permission Version
ACL Projection
Audit Trail
Security Event
Service Identity
Internal Delegation Token
```

---

# 3. Trust Boundaries

主要边界：

```text
Browser
→ Gateway

Gateway
→ Internal Services

Service
→ Authorization

Service
→ MySQL

Service
→ Redis

Service
→ RabbitMQ

Service
→ Nacos

Job
→ Domain Service

Admin UI
→ Admin APIs

Business Service
→ IAM Platform
```

跨越 Trust Boundary 的数据：

```text
默认不可信
```

除非经过验证。

---

# 4. 威胁建模方法

采用 STRIDE 思路：

```text
Spoofing
Tampering
Repudiation
Information Disclosure
Denial of Service
Elevation of Privilege
```

并结合 IAM 特有：

```text
Cross-Tenant Escalation
Stale Authorization
Permission Confusion
ACL Escalation
Policy Drift
Replay
```

---

# 5. Threat 01 — Tenant Header Spoofing

攻击：

```text
X-Tenant-Id: victim-tenant
```

防护：

```text
Gateway strip external tenant headers
Tenant from verified JWT/session
Signed Delegation Token
Downstream re-validates
```

结果：

```text
client header ignored
```

测试：

```text
spoof-tenant-header.spec
```

Release Gate：

```text
FAIL = BLOCK RELEASE
```

---

# 6. Threat 02 — User Header Spoofing

攻击：

```text
X-User-Id: administrator
```

防护：

```text
Gateway strips user headers
subject derived from trusted token
internal context signed
```

---

# 7. Threat 03 — Operation Spoofing

攻击：

客户端传：

```text
X-Operation-Id
X-Resource-Id
```

试图让系统选择低权限 Operation。

防护：

```text
Gateway independently resolves API Mapping
client operation/resource headers discarded
```

---

# 8. Threat 04 — JWT Signature Forgery

攻击：

```text
self-signed JWT
modified claims
alg=none
wrong algorithm
```

防护：

```text
asymmetric signature
algorithm allowlist
issuer
audience
exp
kid
token type
```

---

# 9. Threat 05 — Stolen Access Token

Access Token 被窃取。

控制：

```text
short expiry
Redis session validation
tokenVersion
sessionVersion
force logout
risk monitoring
```

不把：

```text
roles/permissions
```

塞进 JWT，避免权限长期固化。

---

# 10. Threat 06 — Refresh Token Theft

Refresh Token：

```text
HttpOnly
Secure
SameSite
rotation
token family
reuse detection
```

DB：

```text
only hash
```

不存明文。

---

# 11. Threat 07 — Refresh Replay

攻击者复用已旋转 RT。

防护：

```text
ROTATED state
reuse detection
family revoke
session compromise
security event
```

---

# 12. Threat 08 — Session Fixation

登录后：

```text
必须生成新 session id
```

不能沿用：

```text
pre-auth session
```

---

# 13. Threat 09 — Session Resurrection

用户：

```text
force logout
```

后 Redis key 丢失又从 DB 自动恢复 Session。

V1：

```text
不自动恢复 ACTIVE session
```

Redis missing：

```text
Fail Closed
```

---

# 14. Threat 10 — Disabled User Continues Access

攻击：

管理员已禁用 User，但 MQ 尚未传播。

控制：

```text
UserVersion / SecurityState
hard guard
session revoke
```

下一请求：

```text
必须拒绝
```

---

# 15. Threat 11 — Stale Role Permission Cache

Role permission 已撤销，但旧缓存存在。

控制：

```text
RoleVersion
Effective Version mismatch
```

旧 ALLOW：

```text
不可继续使用
```

---

# 16. Threat 12 — Stale ACL Projection

Share 已撤销，但业务本地 ACL Projection 仍 ACTIVE。

控制：

```text
Share Projection Epoch
Expected Epoch
Local Checkpoint
```

如果：

```text
checkpoint < expected
```

则：

```text
SHARED branch Fail Closed
```

---

# 17. Threat 13 — Cross-Tenant Resource IDOR

攻击：

```text
tenant A user
GET tenant B resource id
```

控制：

```text
Tenant Guard
Tenant SQL Predicate
Resource tenant validation
```

任何普通 Grant 不可覆盖。

---

# 18. Threat 14 — Same-Tenant IDOR

用户有 Resource QUERY，但无 Instance/Data Scope。

攻击：

```text
manually change id
```

控制：

```text
Instance Authorization
Data Permission
ACL
```

---

# 19. Threat 15 — Data Scope Bypass Via Count

攻击者无权看 Team B 数据，但：

```text
COUNT(*)
```

泄露数量。

控制：

```text
Data Permission before aggregation
```

---

# 20. Threat 16 — Data Scope Bypass Via Export

攻击：

普通列表有限制，导出接口全表查询。

控制：

```text
EXPORT operation
+
same Data Permission
+
same Field Permission
```

---

# 21. Threat 17 — Data Scope SQL OR Injection Bug

业务 SQL：

```sql
status=1 OR vip=1
```

权限条件错误拼接：

```sql
... OR vip=1 AND tenant=?
```

可能越权。

控制：

```text
AST rewrite
parentheses preservation
integration regression
```

---

# 22. Threat 18 — Unsupported SQL Fail Open

Parser 不支持复杂 SQL 后直接执行原 SQL。

禁止。

必须：

```text
Fail Closed
```

---

# 23. Threat 19 — Raw JDBC Bypass

开发者使用：

```text
JdbcTemplate
Connection
JPA native query
```

绕过 MyBatis DataPermission。

控制：

```text
ArchUnit
dependency scan
protected module rule
```

---

# 24. Threat 20 — Field Mass Assignment

前端不显示：

```text
adminFlag
salary
```

攻击者手工提交。

控制：

```text
submitted field capture
unknown field reject
field write permission
MyBatis SET guard
```

---

# 25. Threat 21 — Explicit Null Bypass

攻击：

```json
{"salary": null}
```

试图绕过字段存在检测。

控制：

```text
presence-based checking
null counts as submitted
```

---

# 26. Threat 22 — Nested Field Bypass

攻击：

```text
address.secret
contacts[0].phone
```

试图绕过父级策略。

控制：

```text
normalized property path
parent policy inheritance
array [] normalization
```

---

# 27. Threat 23 — Frontend-Only Masking

后端发送原始手机号，React 再 Mask。

风险：

```text
DevTools can reveal raw
```

控制：

```text
backend masks before serialization
frontend never receives raw
```

---

# 28. Threat 24 — Reveal Endpoint Abuse

攻击者高频调用：

```text
REVEAL_FIELD
```

控制：

```text
separate operation
step-up auth
rate limit
sensitive audit
short reveal lifetime
```

---

# 29. Threat 25 — Share Operation Escalation

Grantor 只有 READ，却分享 UPDATE。

控制：

```text
grantable capability calculation
server-side subset validation
```

---

# 30. Threat 26 — Share Field Escalation

Grantor：

```text
MASK phone
```

Share：

```text
RAW phone
```

必须拒绝。

---

# 31. Threat 27 — Reshare Escalation

Child Share：

```text
operations > parent
fields > parent
expire > parent
depth > max
```

全部拒绝。

---

# 32. Threat 28 — Revoked Parent, Active Child

Parent Share 被 revoke，但 Child Projection 尚未收敛。

控制：

```text
parent/root validity
version/epoch
derived grant dependency
```

---

# 33. Threat 29 — Expired Share Stays Active

PowerJob 故障导致：

```text
status=ACTIVE
```

但 expireTime 已到。

运行时：

```text
time guard
```

直接拒绝。

---

# 34. Threat 30 — Owner Transfer Keeps Unauthorized Share

Resource Owner 从 Team A → Team B。

旧 Owner 派生 Share 不一定继续有效。

控制：

```text
Grant Basis
Owner Transfer Policy
ResourceOwnershipChangedEvent
```

---

# 35. Threat 31 — API Discovery Race

新 API 被部署，但 IAM Mapping 尚未配置。

控制：

```text
DISCOVERED_UNMAPPED
→ DENY
```

禁止：

```text
temporary public
```

---

# 36. Threat 32 — Wrong API Mapping

管理员把 DELETE API 错映射 QUERY。

控制：

```text
Risk indicator
diff preview
audit
Explain
security test
```

高风险 Operation 应二次确认。

---

# 37. Threat 33 — PUBLIC Policy Misconfiguration

AUTH_REQUIRED → PUBLIC。

控制：

```text
explicit high-risk operation
step-up optional
audit
security event
```

---

# 38. Threat 34 — Internal API Exposure

攻击者直接访问：

```text
/internal/**
```

控制：

```text
external gateway reject
service identity required
network isolation
audience-bound token
```

---

# 39. Threat 35 — Service Identity Forgery

攻击者伪造内部 Service JWT。

控制：

```text
asymmetric signing
issuer/audience
short expiry
key rotation
optional mTLS
```

---

# 40. Threat 36 — Delegation Token Replay

高风险 internal request 重放。

控制：

```text
short expiry
jti
risk-based replay protection
```

---

# 41. Threat 37 — Cross-Service Confused Deputy

Service A 代表 User 调用 Service B，但扩大了权限。

控制：

```text
delegated user context
service identity separate
B re-authorizes
```

Service identity 不自动继承：

```text
all business user rights
```

---

# 42. Threat 38 — Authorization Service Fail Open

Authorization RPC timeout。

错误实现：

```text
catch → allow
```

正式：

```text
safe valid cache
or
DENY/503
```

---

# 43. Threat 39 — Redis Fail Open

Redis session unavailable。

错误：

```text
JWT signature valid → allow
```

正式：

```text
503 / fail closed
```

---

# 44. Threat 40 — Cache Poisoning

客户端不能控制：

```text
tenant/user/resource/op cache key
```

Cache Key：

```text
trusted normalized IDs
```

Cache Value：

```text
version bound
```

---

# 45. Threat 41 — Cache Key Collision

多 Tenant 使用相同 userId/resourceId。

Key 必须：

```text
tenant-aware
```

---

# 46. Threat 42 — HTTP Replay / Duplicate Mutation

网络重试造成：

```text
two shares
two users
two grants
```

控制：

```text
Idempotency-Key
request hash
DB uniqueness
```

---

# 47. Threat 43 — Idempotency Key Collision Abuse

同 Key 发送不同请求。

结果：

```text
409 conflict
```

不能执行第二个业务请求。

---

# 48. Threat 44 — Idempotency Lease Hijack

PROCESSING 记录无限卡住或被错误接管。

控制：

```text
owner token
lease_until
request hash
controlled takeover
```

---

# 49. Threat 45 — MQ Duplicate Effect

同 Event 投递多次。

控制：

```text
eventId + consumerGroup unique
business state/version
```

---

# 50. Threat 46 — MQ Out-of-Order Stale Overwrite

v3 已应用，收到 v2。

控制：

```text
aggregateVersion
stale event ignored
```

---

# 51. Threat 47 — Event Forgery

非授权 Producer 向关键 Queue 发布伪事件。

控制：

```text
RabbitMQ permissions
vhost isolation
producer identity
event schema validation
```

成熟阶段可：

```text
event signature
```

---

# 52. Threat 48 — Outbox Tampering

应用/运维手工修改：

```text
payload
event status
```

控制：

```text
DB account least privilege
infra admin restrictions
audit
```

---

# 53. Threat 49 — DLQ Replay Abuse

管理员 Replay 恶意/错误事件。

控制：

```text
specific operation permission
step-up for high-risk
idempotent consumer
infra audit
```

---

# 54. Threat 50 — Job Double Execution

PowerJob 重试或多 Worker。

控制：

```text
business status CAS
job business key
idempotency
```

---

# 55. Threat 51 — Job Becomes Security Authority

错误：

```text
Share expiration only updates by job
```

控制：

```text
runtime time checks
```

Job 只收敛状态。

---

# 56. Threat 52 — Nacos Config Tampering

攻击者修改：

```text
PUBLIC policy
security fallback
database endpoints
```

控制：

```text
Nacos authentication
namespace isolation
config audit
least privilege
critical security config not casually dynamic
```

业务授权主语义仍由 DB 元数据控制。

---

# 57. Threat 53 — Default Secret in Production

生产误用：

```text
123456
test JWT key
default MinIO secret
```

控制：

```text
prod startup safety check
secret scan
```

---

# 58. Threat 54 — Secret in Git

控制：

```text
git secret scan
.env ignored
private key externalized
```

---

# 59. Threat 55 — Sensitive Log Leakage

禁止日志输出：

```text
password
JWT
refresh token
full phone
ID card
private key
```

使用：

```text
sanitizer
structured logs
```

---

# 60. Threat 56 — Audit Log Tampering

Audit 原则：

```text
append-only
```

生产：

```text
separate DB account
restricted update/delete
```

Security Event workflow 状态与原始 Audit Fact 分开。

---

# 61. Threat 57 — Audit Repudiation

管理员声称：

```text
“我没改这个权限”
```

必须能追：

```text
operator
trace
before
after
time
source IP/device
```

---

# 62. Threat 58 — Explain Information Disclosure

Explain 可能泄露：

```text
team structure
roles
security rules
internal IDs
```

控制：

```text
BASIC
ADMIN
SECURITY
```

三档输出。

普通用户不得获取 SECURITY Explain。

---

# 63. Threat 59 — Audit Query Cross-Tenant

Audit 本身必须：

```text
Tenant Authorization
Data Scope
Field Permission
```

不能因为是“安全后台”就默认看所有 Tenant。

---

# 64. Threat 60 — Security Center Privilege Escalation

ACK/RESOLVE：

```text
是写操作
```

必须：

```text
dynamic authorization
audit
optimistic lock
```

---

# 65. Threat 61 — Admin Account Takeover

高风险管理：

```text
PUBLIC API
Role expansion
Field raw read
DLQ replay
Projection rebuild
```

建议：

```text
step-up MFA
```

V1 至少架构预留。

---

# 66. Threat 62 — Privilege Escalation Through Role Editing

用户可以编辑 Role，但不应该给自己新增更高权限。

控制：

```text
grantability policy
self-escalation check
high-risk audit
```

V1 至少禁止：

```text
grant more than actor can administer
```

---

# 67. Threat 63 — Self-Grant Via TeamRole

同理：

```text
创建 TeamRole
→ 给自己
→ 绑定高权限
```

必须通过：

```text
administer/grantability authorization
```

---

# 68. Threat 64 — Permission Graph Cycle / Confusion

Team hierarchy 或 Share parent：

```text
cycle
```

控制：

```text
Team move cycle detection
Share root/parent/depth invariant
```

---

# 69. Threat 65 — Time Boundary Attack

攻击：

```text
start/expire 精确边界
```

正式：

```text
start <= now < expire
```

系统统一：

```text
UTC
```

---

# 70. Threat 66 — Clock Drift

多服务时间漂移可能让临时权限延长。

控制：

```text
NTP
UTC
monitor clock skew
```

关键 token 验证允许极小 clock skew，但授权 expire 不应设置大宽限。

---

# 71. Threat 67 — Race: Revoke vs Use

撤权和业务修改同时发生。

控制：

```text
permission version
instance version
business optimistic lock
```

高风险操作可二次授权。

---

# 72. Threat 68 — Race: Share Create vs Owner Transfer

控制：

```text
resourceVersion
owner version
TOCTOU re-check
```

不只依赖首次 RPC。

---

# 73. Threat 69 — Race: Disable User vs Refresh

User Disable 与 Refresh 同时。

Refresh 必须检查：

```text
user security state/version
session state
```

不能只看 Refresh Token ACTIVE。

---

# 74. Threat 70 — Race: Force Logout vs Request

请求和 Force Logout 并发。

安全接受原则：

```text
已通过完整授权并进入短事务的请求可能完成
```

但 Force Logout 后新请求必须拒绝。

对极高风险操作可：

```text
commit 前 re-check
```

---

# 75. Threat 71 — Enumeration

Login / password reset：

不能暴露：

```text
user exists
email exists
```

统一响应语义。

---

# 76. Threat 72 — Brute Force

控制：

```text
IP rate limit
identity rate limit
progressive delay
temporary lock
CAPTCHA optional
```

---

# 77. Threat 73 — Credential Stuffing

控制：

```text
rate limit
risk engine
security events
password strength
MFA/step-up
```

---

# 78. Threat 74 — CSRF

Refresh/Logout 使用 HttpOnly Cookie。

必须：

```text
SameSite
Origin/Referer validation
CSRF token where needed
```

---

# 79. Threat 75 — XSS Steals Access Token

Access Token 在 JS memory。

控制：

```text
CSP
no unsafe html
sanitize dynamic content
short expiry
```

不能存在：

```text
localStorage refresh token
```

---

# 80. Threat 76 — Clickjacking

管理后台：

```text
frame-ancestors
X-Frame-Options where applicable
```

防止高风险操作被嵌入。

---

# 81. Threat 77 — CORS Misconfiguration

禁止：

```text
Access-Control-Allow-Origin: *
+
credentials=true
```

生产：

```text
explicit origins
```

---

# 82. Threat 78 — Open Redirect

Login callback/redirect：

只能：

```text
allowlisted relative paths/domains
```

---

# 83. Threat 79 — SSRF Through Metadata URL

如果未来 Resource/Service Metadata 支持 URL：

```text
必须 allowlist
```

V1 尽量避免用户配置任意后端 fetch URL。

---

# 84. Threat 80 — Unsafe Condition DSL

禁止：

```text
SpEL
Groovy
JS
raw SQL
```

仅：

```text
safe AST operators
```

---

# 85. Threat 81 — Unsafe Mask Strategy

Custom Mask 只允许：

```text
prefix/suffix/maskChar
```

不执行：

```text
script
regex with unbounded catastrophic patterns
```

---

# 86. Threat 82 — Query Sort Injection

前端：

```text
sortField
```

不能直接：

```sql
ORDER BY ${sortField}
```

使用：

```text
server-side allowlist
```

---

# 87. Threat 83 — Search Wildcard Abuse

超宽 LIKE：

```text
%
```

可能 DoS。

控制：

```text
min keyword length
escaped wildcard
rate limit
query timeout
```

---

# 88. Threat 84 — Large Export DoS

控制：

```text
async job
row limits
rate limit
authorization re-check
```

---

# 89. Threat 85 — Explain/Simulator DoS

Explain 很重。

控制：

```text
admin-only
rate limit
timeout
no unrestricted batch
```

---

# 90. Threat 86 — Authorization Cache Explosion

攻击构造大量随机 InstanceKey。

控制：

```text
bounded Caffeine
TTL
do not cache every negative instance forever
metrics
```

---

# 91. Threat 87 — Redis Memory Exhaustion

控制：

```text
TTL
key namespace
memory alerts
rate limits
```

---

# 92. Threat 88 — Audit Flood

攻击制造大量 DENY。

控制：

```text
async ingest
queue
rate limit
aggregation
```

但：

```text
security relevant DENY
```

不能简单丢弃。

---

# 93. Threat 89 — RabbitMQ Backlog

控制：

```text
queue lag metrics
DLQ
consumer scaling
outbox oldest pending alert
```

---

# 94. Threat 90 — Dependency Compromise

控制：

```text
dependency scan
SBOM
container scan
pin versions via BOM
```

---

# 95. Threat 91 — Build Artifact Tampering

控制：

```text
immutable image tags
checksum/signing future
CI provenance
```

---

# 96. Threat 92 — Production Debug Endpoint

禁止：

```text
/test-token
/debug-auth
/clear-cache
```

无保护存在。

运维能力必须：

```text
Infrastructure Admin APIs
+
dynamic authorization
+
audit
```

---

# 97. Threat 93 — Backup Data Exposure

Backup 包含：

```text
users
security state
audit
```

控制：

```text
encrypted storage
restricted access
retention
restore audit
```

---

# 98. Threat 94 — Restore Creates Stale Security State

恢复旧备份后：

```text
old sessions/tokens
```

可能复活。

恢复流程：

```text
invalidate sessions
rotate signing/security versions where needed
rebuild projections
```

---

# 99. Threat 95 — Projection Rebuild Window

重建 ACL 时：

禁止：

```text
TRUNCATE current
→ slow rebuild
```

导致安全/可用性混乱。

推荐：

```text
new namespace/table
build
verify
atomic switch
```

---

# 100. Threat 96 — Misuse of SYSTEM_INTERNAL

开发者为了“方便”把业务 Mapper 标为：

```text
SYSTEM_INTERNAL
```

控制：

```text
central metadata
high-risk audit
CI rule
no casual annotation bypass
```

---

# 101. Threat 97 — Super Admin Hardcoding

禁止：

```java
if (userId == 1L)
if (roleCode.equals("SUPER_ADMIN"))
```

生产权限仍应来自动态授权。

基础设施 bootstrap 特权：

```text
narrow
audited
isolated
```

---

# 102. Threat 98 — Bootstrap Backdoor

Tenant Bootstrap / Platform Bootstrap：

```text
只能初始化时使用
```

完成后：

```text
disable one-time path
idempotent state check
audit
```

不能变成永久万能后门。

---

# 103. Threat 99 — Audit Sanitizer Disabled in Debug

禁止：

```text
debug mode bypass sanitizer
```

Sanitizer：

```text
always-on
```

---

# 104. Threat 100 — Security Policy Drift

代码、DB、Gateway、Starter 之间的规则不一致。

控制：

```text
SPEC
contract tests
golden scenarios
versioned metadata
architecture tests
```

---

# 105. Abuse Case Matrix 结构

每个 Abuse Case 必须记录：

```text
ID
Actor
Precondition
Attack
Expected Control
Detection
Test
Severity
Release Blocking
```

---

# 106. Severity

采用：

```text
CRITICAL
HIGH
MEDIUM
LOW
```

CRITICAL 例：

```text
Cross-Tenant Read
Privilege Escalation
Refresh Token Replay
Internal Auth Forgery
Field Sensitive Leakage
```

---

# 107. Release Blocking

以下永远 Blocking：

```text
Cross Tenant
Privilege Escalation
Immediate Revoke Failure
Sensitive Field Leakage
JWT Forgery
Refresh Reuse Failure
ACL Revoke Failure
Fail-Open Authorization
```

---

# 108. Security Testing Structure

项目：

```text
tests/security/
├── auth/
├── tenant/
├── authorization/
├── data-permission/
├── field-permission/
├── sharing/
├── internal/
├── replay/
├── infrastructure/
└── abuse-cases/
```

---

# 109. Required Security E2E

至少：

```text
tenant-spoof
user-spoof
cross-tenant-idor
same-tenant-idor
jwt-forgery
refresh-reuse
role-escalation
team-role-escalation
field-mass-assignment
share-operation-escalation
share-field-escalation
stale-acl-revoke
unknown-api-deny
public-api-risk
internal-api-reject
```

---

# 110. Security Property Tests

对 Authorization Merge：

可以做：

```text
property-based testing
```

性质：

```text
hard deny never overridden
no grant => deny
tenant mismatch => deny
expired grant never allow
```

---

# 111. Fuzzing Candidates

适合：

```text
API path normalization
Condition DSL parser
Data SQL parser
Cursor parser
JWT parser
Field path normalization
```

---

# 112. Red-Team Demo Scenario

V1 Demo 可展示：

```text
1 spoof tenant header → denied
2 change resource id → denied
3 manually write hidden field → denied
4 over-share DELETE → denied
5 revoke share → immediate denied
6 reuse refresh token → session compromised
7 Explain/Audit show reason
```

---

# 113. Detection Strategy

安全控制之外必须有：

```text
Detection
```

例如：

```text
Cross Tenant Attempt
→ Security Event

Share Escalation
→ Security Event

Refresh Reuse
→ Critical Event

Internal Signature Failure
→ High/Critical Event
```

---

# 114. Alerting

建议：

```text
CRITICAL
→ immediate alert

HIGH
→ security dashboard + alert

MEDIUM
→ dashboard

LOW
→ audit
```

具体通知渠道后续配置。

---

# 115. Security Metrics

至少：

```text
auth_login_failure_total
auth_refresh_reuse_total
authz_deny_total
cross_tenant_attempt_total
field_write_denied_total
share_escalation_attempt_total
internal_auth_failure_total
projection_gap_total
authorization_fail_closed_total
```

---

# 116. Security Logging

日志可记录：

```text
tenantId
userId
eventType
decisionCode
traceId
resourceId
operationId
```

不能记录：

```text
credential
token
raw sensitive fields
```

---

# 117. Key Management

JWT signing：

```text
current key
previous key
kid
rotation
```

Auth：

```text
private key
```

Gateway/services：

```text
public keys
```

私钥不进入 Git/Image。

---

# 118. Key Rotation Failure Case

新 key 发布后部分服务未知 `kid`。

正确：

```text
refresh JWKS/key set
bounded retry
if unverifiable → deny
```

不能：

```text
accept unknown key
```

---

# 119. Authorization Cache TTL

安全缓存：

```text
version-bound
time-bound
```

包含 expire grant 时：

```text
validUntil <= nearest grant expiry
```

避免缓存越过授权到期时间。

---

# 120. Negative Cache

DENY 可以短缓存。

但要：

```text
version bound
```

否则新授权会被旧 DENY 长时间阻塞。

---

# 121. Security Review Gate

以下变更强制安全 Review：

```text
authentication
authorization merge
tenant interceptor
data permission SQL rewrite
field serializer
share
session/refresh
gateway mapping
internal auth
idempotency
outbox/consumer
```

---

# 122. Threat Model Update Rule

新增：

```text
new trust boundary
new public API class
new grant source
new bypass mode
new token
new storage
```

必须更新 Threat Model。

---

# 123. Security ADR

如果新增：

```text
SYSTEM bypass
super admin
cross-tenant admin
new public endpoint
```

必须：

```text
ADR
```

说明为什么安全。

---

# 124. Secure Defaults

正式冻结：

```text
New API = DENY
New Resource = no grants
New Field = not exposed until reviewed
New Share = no reshare
Missing Mapping = DENY
Unknown Policy = DENY
Parser Failure = DENY
Version Mismatch = recompute or DENY
```

---

# 125. Threat Model Completion Criteria

SPEC 31 完成后，V1 Security 必须做到：

```text
Prevent
Detect
Audit
Recover
Test
```

五个层面都有答案。

---

# 126. SPEC 31 冻结结论

Enterprise IAM V1.0 的安全哲学正式冻结为：

```text
Default Deny
Fail Closed
Least Privilege
Defense in Depth
Zero Trust Between Boundaries
Immediate Revocation
No Permission Hardcoding
Strong Tenant Isolation
Backend Enforcement
Reliable Audit
Recoverable Projections
```

任何功能如果只能回答：

```text
“正常情况下怎么工作”
```

却回答不了：

```text
“攻击者绕过 UI 后会怎样”
```

则该功能不算完成。
