# Enterprise IAM & Dynamic Authorization Platform
## 35 — File Preview, Download Security, Scan, Lifecycle & Storage Disaster Recovery SPEC 1.0

> 本文在 SPEC 34 的企业文件上传基础上，继续冻结文件预览、下载安全、恶意文件扫描、生命周期管理、对象存储故障恢复、备份与灾难恢复。
>
> 目标：
>
> ```text
> Upload
> → Verify
> → Scan
> → Available
> → Preview / Download
> → Permission Revoke
> → Lifecycle
> → Purge / Archive
> → Disaster Recovery
> ```
>
> 文件系统必须满足：
>
> ```text
> 不泄露
> 不绕权
> 不误执行
> 可恢复
> 可追踪
> ```

---

# 1. 文件安全分层

文件安全至少分五层：

```text
1 Tenant Isolation
2 IAM Authorization
3 File State
4 Scan / Risk Policy
5 Storage Access Policy
```

即使 IAM ALLOW：

如果：

```text
FileStatus != AVAILABLE
```

仍可拒绝下载。

---

# 2. 预览与下载不是同一个 Operation

正式区分：

```text
PREVIEW
DOWNLOAD
```

原因：

```text
允许查看
不一定允许保存原始文件
```

尤其适合：

```text
合同
证件
财务附件
敏感报表
```

---

# 3. Preview Mode

V1 支持：

```text
DIRECT_PREVIEW
SAFE_RENDER
NO_PREVIEW
```

DIRECT_PREVIEW：

```text
浏览器直接展示原始内容
```

SAFE_RENDER：

```text
服务端/转换服务生成安全预览产物
```

NO_PREVIEW：

```text
只能下载或完全禁止
```

---

# 4. 默认预览策略

安全默认：

```text
图片/PDF
→ SAFE_RENDER or controlled direct preview

Office
→ SAFE_RENDER

HTML/SVG/XML/可执行脚本型内容
→ NO_PREVIEW by default

EXE/JAR/BAT/PS1/SH
→ NO_PREVIEW
```

具体 MIME/扩展名策略动态配置。

---

# 5. HTML/SVG 风险

即使 Content-Type 看起来是：

```text
image/svg+xml
text/html
```

也可能含：

```text
script
external resource
event handler
```

因此不能直接以内联：

```text
same-origin
```

方式展示。

推荐：

```text
sandboxed isolated origin
```

或转安全图片/PDF。

---

# 6. Content-Disposition

下载：

```text
attachment
```

预览：

```text
inline
```

但只有：

```text
PreviewPolicy allow
```

时才 inline。

文件名必须安全编码。

---

# 7. Content-Type Trust

客户端声明：

```text
contentType
```

只能作为提示。

实际：

```text
magic bytes
MIME sniff
scanner
```

共同决定。

---

# 8. MIME Mismatch

例如：

```text
filename = report.pdf
actual content = executable
```

结果：

```text
QUARANTINED
```

并生成：

```text
FILE_CONTENT_TYPE_MISMATCH
```

安全事件。

---

# 9. Scan Pipeline

正式流水线：

```text
Upload Completed
 ↓
Integrity Verified
 ↓
Scan Pending
 ↓
Scanner
 ↓
CLEAN
or
INFECTED
or
SCAN_FAILED
```

文件：

```text
PENDING_SCAN
```

期间默认不可下载。

---

# 10. Scanner Abstraction

Application 依赖：

```text
FileScanPort
```

Infrastructure：

```text
ClamAVFileScanAdapter
CloudScannerAdapter
NoopScanner(for dev only)
```

生产禁止：

```text
scan_required=true
却使用 Noop
```

---

# 11. 扫描策略

`iam_file_access_policy` 增加：

```text
scan_required
block_on_scan_failure
allowed_mime_types
blocked_mime_types
max_archive_depth
max_uncompressed_ratio
```

---

# 12. Scan Result

```text
CLEAN
INFECTED
SUSPICIOUS
FAILED
SKIPPED
```

SKIPPED：

只能在：

```text
policy explicitly allows
```

时出现。

---

# 13. INFECTED 处理

发现：

```text
INFECTED
```

必须：

```text
FileStatus = QUARANTINED
```

并：

```text
禁止 PREVIEW
禁止 DOWNLOAD
禁止新 REFERENCE
```

是否保留物理对象：

```text
由取证策略决定
```

---

# 14. QUARANTINED 访问

普通用户：

```text
完全不可下载
```

Security Admin：

如需取证：

```text
独立 FORENSIC_DOWNLOAD Operation
+
step-up
+
proxy download only
+
100% audit
```

---

# 15. Scan Failure

如果扫描服务故障：

当：

```text
scan_required=true
block_on_scan_failure=true
```

文件：

```text
不可用
```

不能：

```text
为了可用性直接放行
```

---

# 16. Scan Retry

Job：

```text
ScanRetryJob
```

重试：

```text
bounded
backoff
```

长期失败：

```text
security/infrastructure alert
```

---

# 17. Archive Bomb

对于：

```text
zip
rar
7z
tar.gz
```

扫描/预览前必须限制：

```text
nested depth
entry count
uncompressed size
compression ratio
```

避免：

```text
zip bomb
```

---

# 18. Preview Rendering

未来预览服务建议独立：

```text
iam-file-preview-worker
```

或外部 sandbox worker。

不要在：

```text
iam-file-service 主 JVM
```

内直接解析不可信 Office/PDF。

---

# 19. Preview Sandbox

转换 Worker：

```text
container isolation
resource limit
no host mount
no unrestricted network
timeout
memory limit
cpu limit
```

---

# 20. Preview Artifact

生成：

```text
preview object
```

不能覆盖原始文件。

建议：

```text
iam_file_preview_artifact
```

字段：

```text
file_id
source_object_version
preview_type
object_key
status
created_at
expire_at
```

---

# 21. Preview Cache Invalidation

原始 Logical File/Object 变化：

```text
source object version changes
```

旧 Preview：

```text
invalid
```

---

# 22. Watermark

敏感文件预览可配置：

```text
user display name
user id partial
timestamp
tenant
trace
```

水印属于：

```text
deterrence / traceability
```

不是权限控制替代。

---

# 23. Dynamic Watermark

如果每用户水印：

不要永久存一份每用户预览文件。

优先：

```text
runtime overlay
```

或短时派生产物。

---

# 24. Download Modes 回顾

支持：

```text
PRESIGNED
PROXY
```

---

# 25. PRESIGNED 下载

优点：

```text
性能
大文件直出
```

缺点：

```text
URL 在 TTL 内难即时撤销
```

因此策略必须指定：

```text
presigned_ttl_seconds
```

---

# 26. PROXY 下载

流程：

```text
Client
→ File Service / Download Gateway
→ Authorization
→ Object Storage
→ Stream
```

优点：

```text
每请求实时授权
撤权立即生效
```

缺点：

```text
带宽和连接成本更高
```

---

# 27. 高安全文件

以下建议强制：

```text
PROXY
```

例如：

```text
证件
薪资
财务凭证
密钥备份
高敏合同
```

---

# 28. Download Token

如果要减少 File Service 带宽，可设计：

```text
short-lived download token
```

绑定：

```text
tenant
user/session
file
operation
expiry
jti
```

下游下载网关验证。

不能是：

```text
永久 bearer URL
```

---

# 29. Download Token Replay

高敏资源可：

```text
one-time jti
```

Redis SETNX 防重放。

普通大文件可以：

```text
short-lived multi-use
```

由 policy 决定。

---

# 30. Range Request

大文件下载/视频预览需要：

```text
HTTP Range
```

PROXY 模式必须支持：

```text
206 Partial Content
```

但每个新请求：

```text
仍校验授权/token
```

---

# 31. Presigned Range

MinIO/S3 GET 本身支持 Range。

但 URL TTL 必须足够完成合理下载。

---

# 32. Download Audit

记录：

```text
fileId
logical resource reference
operation
mode
result
bytes(optional)
trace
```

不要记录：

```text
presigned raw URL
```

---

# 33. Preview Audit

敏感资源预览：

```text
100%
```

普通低风险：

可按策略采样。

---

# 34. Access Decision Order

下载/预览：

```text
Tenant
↓
Logical File Status
↓
Reference/Resource Authorization
↓
Field/Attachment Policy
↓
Scan/Risk Policy
↓
Download Mode
↓
Storage Access
```

---

# 35. 逻辑删除

Logical File：

```text
DELETED
```

普通查询：

```text
不可见
```

但 Audit/Recovery：

```text
仍可查 metadata
```

---

# 36. 删除 Reference

删除业务附件：

优先：

```text
remove reference
```

不自动删除 Logical File。

---

# 37. Logical File Delete

只有：

```text
用户明确删除文件
```

且权限允许时：

```text
AVAILABLE → DELETED
```

---

# 38. Purge

真正物理清理：

```text
PURGED
```

必须满足：

```text
no active references
retention reached
legal hold absent
```

---

# 39. Legal Hold

预留：

```text
legal_hold
```

或：

```text
iam_file_retention_policy
```

被 Legal Hold：

```text
禁止 purge
```

即使用户删除。

---

# 40. Retention Policy

可按：

```text
resource
file type
tenant
risk level
```

配置：

```text
retention_days
delete_grace_days
archive_after_days
purge_after_days
```

---

# 41. Lifecycle States

Logical File：

```text
PENDING_VERIFY
PENDING_SCAN
AVAILABLE
QUARANTINED
DELETED
ARCHIVED
PURGED
FAILED
```

---

# 42. ARCHIVED

低频文件：

```text
ARCHIVED
```

可迁移到：

```text
低成本 storage class / bucket
```

下载前：

```text
restore
```

---

# 43. Storage Tier Abstraction

ObjectStoragePort 预留：

```text
STANDARD
ARCHIVE
```

但 V1 可以只实现 STANDARD。

---

# 44. Object Version

物理对象一旦 canonical 完成：

建议：

```text
immutable
```

不要覆盖 object bytes。

需要新内容：

```text
new FileObject
```

---

# 45. Object Key Immutability

禁止：

```text
same objectKey
overwrite bytes
```

这会破坏：

```text
hash
scan
audit
reference
```

一致性。

---

# 46. Encryption At Rest

MinIO：

建议启用：

```text
SSE
```

生产密钥策略：

```text
KMS / external secret
```

---

# 47. Encryption In Transit

全部：

```text
TLS
```

Browser ↔ Gateway/MinIO
Service ↔ MinIO
Service ↔ IAM Internal API

---

# 48. Bucket Policy

Bucket：

```text
private
```

禁止：

```text
public read
public write
```

Presigned URL 仅作为临时能力。

---

# 49. MinIO Credentials

File Service 使用：

```text
least privilege service account
```

权限只覆盖指定 bucket/prefix。

---

# 50. Storage Namespace

建议：

```text
raw/
preview/
quarantine/
archive/
```

或独立 Bucket。

生产可按：

```text
security domain
```

隔离。

---

# 51. Quarantine Namespace

感染/可疑文件：

```text
quarantine bucket/prefix
```

普通 File Service 下载路径：

```text
无读取权限
```

可进一步降低误放行风险。

---

# 52. Backup Scope

必须备份：

```text
iam_file DB
MinIO object metadata/data
bucket policies/config
```

---

# 53. DB 与 Object Storage 一致性

两边无法一个本地事务。

因此必须：

```text
state machine
reconcile
recovery jobs
```

而不是假设永远一致。

---

# 54. Disaster Scenario A

DB 有：

```text
AVAILABLE logical file
```

Object Storage 对象不存在。

必须：

```text
download deny
mark object/file DEGRADED/FAILED
security/infrastructure event
```

---

# 55. Disaster Scenario B

Object 存在：

```text
DB metadata missing
```

处理：

```text
orphan inventory
verify hash/object metadata
recover if linked to known upload session
otherwise quarantine/delete by policy
```

---

# 56. Disaster Scenario C

Preview Artifact 存在：

```text
source object missing
```

Preview：

```text
不可作为原文件替代事实源
```

禁止继续提供。

---

# 57. Disaster Scenario D

扫描记录丢失。

当：

```text
scan_required
```

则：

```text
file falls back to PENDING_SCAN
```

重新扫描。

---

# 58. Inventory Reconcile

Job：

```text
FileStorageInventoryReconcileJob
```

扫描：

```text
DB object metadata
vs
MinIO inventory/list
```

按批处理。

---

# 59. Inventory 不做热路径

不允许每次下载：

```text
list entire bucket
```

只：

```text
Head Object
```

必要时校验。

---

# 60. Object Health Check

下载前是否 Head：

策略化。

高安全/高一致性：

```text
HEAD
```

普通：

```text
metadata/cache
```

---

# 61. Restore Strategy

恢复顺序：

```text
1 MySQL metadata
2 MinIO objects
3 Bucket/security policy
4 File Service
5 Reconcile
6 Scan status verification
7 Preview rebuild as needed
```

---

# 62. RPO/RTO 初始建议

File metadata：

```text
RPO <= 5 min
RTO <= 60 min
```

File object：

```text
RPO depends object replication
RTO <= 4h
```

高价值附件可更严格。

---

# 63. MinIO Replication

生产建议至少：

```text
erasure coding
multiple disks/nodes
```

跨站复制：

```text
根据 RPO/RTO 决定
```

---

# 64. Backup Integrity

文件备份：

```text
checksum
```

抽样恢复时：

```text
recalculate hash
```

对比 `iam_file_object.sha256`。

---

# 65. Restore Drill

至少季度：

```text
restore iam_file DB
restore sample objects
verify hash
authorize download
verify audit
```

---

# 66. File DR Smoke

恢复后：

```text
instant upload
multipart complete
preview
download
reference
delete
reconcile
```

必须通过。

---

# 67. Object Corruption

Hash 校验发现对象内容损坏：

```text
QUARANTINED
```

如果有副本：

```text
repair from replica
```

否则：

```text
restore backup
```

---

# 68. SHA-256 Revalidation

不要求每次下载全文件重算。

建议：

```text
upload complete verify
backup restore verify
periodic sample
incident verify
```

---

# 69. Storage Full

MinIO 磁盘逼近阈值：

```text
new upload session denied/degraded
```

不要等到 Complete 才失败。

---

# 70. Storage Quota vs Physical Dedup

Tenant quota 推荐按：

```text
logical usage
```

计费/限额。

物理 dedup 节省：

```text
平台成本
```

但不应该让 Tenant B 因 Tenant A 已上传同一文件就免费绕过配额，除非产品明确如此。

---

# 71. Logical Usage

Quota 使用：

```text
sum logical file size
```

或：

```text
active references usage
```

产品需要固定一种。

V1 推荐：

```text
logical file ownership usage
```

---

# 72. Purge Reconcile

如果 `reference_count=0`：

不能立刻信任缓存字段。

Purge Job：

```text
recalculate active references
```

后再删除对象。

---

# 73. Purge Transaction

```text
mark PURGING
↓
delete object remote
↓
mark PURGED
```

若 remote delete 成功、DB 失败：

```text
reconcile state
```

---

# 74. Purge Failure

Object delete 失败：

```text
retry
```

但 Logical File：

```text
仍不可见
```

---

# 75. Presigned URL Leakage

防护：

```text
short TTL
no logs
HTTPS
referer policy
no analytics collection of full URL
```

---

# 76. Browser Cache

敏感 Preview/Download 响应：

```text
Cache-Control: no-store
```

普通静态预览可策略化缓存。

---

# 77. CDN

V1 默认：

```text
不对高敏文件启用公共 CDN
```

未来 CDN 必须：

```text
signed URL/cookie
IAM-aware origin auth
```

---

# 78. Preview XSS

Safe Preview 页面：

```text
isolated origin
CSP
sandbox iframe
```

禁止与主 React 管理后台同 origin 直接执行不可信内容。

---

# 79. PDF 风险

PDF 也可能包含：

```text
JS
embedded file
external link
```

高安全预览：

```text
rasterized / sanitized preview
```

优于浏览器直接打开原 PDF。

---

# 80. Office 风险

不要在后端主进程直接：

```text
Apache POI 解析任意复杂宏文档
```

用于在线预览。

使用隔离 Worker。

---

# 81. Macro Files

例如：

```text
docm
xlsm
pptm
```

默认：

```text
no inline preview
```

可下载但需策略/扫描通过。

---

# 82. Executable Files

默认：

```text
UPLOAD 可配置
PREVIEW 禁止
DOWNLOAD 可高风险受限
```

企业内某些场景确实需要上传安装包，因此不要一刀切禁止所有 executable，而应策略化。

---

# 83. Filename Spoofing

例如：

```text
invoice.pdf.exe
```

UI：

```text
展示完整文件名
```

不能只显示第一个扩展名。

---

# 84. Unicode Filename

防：

```text
RTL override
homoglyph confusion
```

可在 UI 显示：

```text
normalized filename
```

并对危险 Unicode 给警告。

---

# 85. Download Rate Limit

按：

```text
user/session
file
tenant
```

进行合理限速。

大文件下载不要因一次请求超时触发无限重试。

---

# 86. Hotlink Protection

Presigned URL 天然有 TTL。

Proxy 模式：

```text
session/token required
```

---

# 87. Range Abuse

攻击者大量随机 Range：

可能造成：

```text
IO amplification
```

控制：

```text
rate limit
range count limit
minimum span policy optional
```

---

# 88. Preview Conversion DoS

限制：

```text
max pages
max dimensions
max render duration
max memory
```

超限：

```text
NO_PREVIEW / DOWNLOAD ONLY
```

---

# 89. Huge Image

解码超大尺寸图片可能耗尽内存。

预览 Worker 必须：

```text
pixel count limit
```

---

# 90. Video Preview

V1 可：

```text
direct controlled streaming
```

复杂转码：

```text
V1.1
```

---

# 91. Scan / Preview Queue

不要共用核心授权 RabbitMQ Queue。

建议：

```text
file.scan
file.preview
```

独立交换机/队列。

---

# 92. File Event Reliability

高价值：

```text
FileAvailable
FileQuarantined
FileDeleted
FilePurged
```

都通过：

```text
Outbox
```

---

# 93. Observability

新增指标：

```text
iam_file_download_total
iam_file_download_denied_total
iam_file_preview_total
iam_file_scan_total
iam_file_scan_failure_total
iam_file_quarantine_total
iam_file_purge_total
iam_file_storage_reconcile_mismatch_total
iam_file_presigned_issued_total
```

---

# 94. Alert

Page：

```text
quarantined file downloaded > 0
cross-tenant file access success > 0
storage object missing for AVAILABLE file spike
scan bypass detected
```

Ticket：

```text
scan backlog
preview backlog
purge backlog
orphan object count
```

---

# 95. Runbook — Scan Backlog

检查：

```text
scanner availability
queue lag
worker count
file size/type distribution
```

止损：

```text
files remain unavailable
```

而不是关闭 scan_required。

---

# 96. Runbook — Infected File

```text
quarantine
block refs/download
identify uploader
audit accesses
security event
retain or purge by policy
```

---

# 97. Runbook — Missing Object

```text
disable access
check replica/backup
restore
verify hash
re-enable
```

---

# 98. Runbook — Presigned URL Leak

```text
assess TTL remaining
revoke related file/session if possible
rotate object key only if high severity and necessary
switch policy to proxy
security audit
```

注意对象 key 变更代价大，不作为常规手段。

---

# 99. Runbook — Storage Capacity Critical

```text
block new large sessions
accelerate purge
add storage
protect existing reads
```

---

# 100. Security Release Gates

Blocking：

```text
INFECTED/QUARANTINED download succeeds
cross-tenant logical file access
unsafe inline HTML/SVG preview
presigned wildcard write/read
deleted file remains normally downloadable
reference permission bypass
proxy download does not honor immediate revoke
```

---

# 101. E2E — Scan Clean

```text
upload
→ scan CLEAN
→ AVAILABLE
→ preview/download
```

---

# 102. E2E — Scan Infected

```text
upload
→ INFECTED
→ QUARANTINED
→ preview denied
→ download denied
→ security event
```

---

# 103. E2E — Scan Service Down

当策略要求扫描：

```text
upload complete
→ SCAN_FAILED/PENDING
→ download denied
```

恢复：

```text
retry scan
→ CLEAN
→ available
```

---

# 104. E2E — Preview Policy

```text
PDF safe-render allowed
HTML no-preview
EXE no-preview
```

---

# 105. E2E — Immediate Revoke Proxy

```text
download allowed
↓
permission revoked
↓
next range/download request denied
```

---

# 106. E2E — Presigned TTL

```text
URL issued
permission revoked
new URL denied
old URL only survives bounded TTL
```

此行为必须被测试并文档化。

---

# 107. E2E — Deleted File

```text
file delete
→ metadata retained
→ normal access denied
→ purge later
```

---

# 108. E2E — Legal Hold

```text
delete requested
→ hidden from normal user
→ purge job skips due legal hold
```

---

# 109. E2E — Object Missing

```text
DB AVAILABLE
object manually removed
→ download fails safe
→ file marked degraded/quarantine
→ alert
```

---

# 110. E2E — Restore

```text
restore DB + MinIO
→ reconcile
→ hash verify
→ authorized download passes
```

---

# 111. Database Extension

新增：

```text
iam_file_preview_artifact
iam_file_retention_policy
```

可选：

```text
iam_file_storage_reconcile_record
```

---

# 112. Flyway Extension

建议：

```text
V8__file_preview_artifact.sql
V9__file_retention_policy.sql
V10__file_storage_reconcile.sql
```

---

# 113. Backlog Extension

E15 增加：

```text
S198 Preview Policy
S199 Safe Preview Worker SPI
S200 Download Security Modes
S201 File Scan Pipeline
S202 Lifecycle / Retention
S203 Storage Reconcile
S204 File DR / Restore Drill
```

---

# 114. V1 / V1.1 Boundary

V1 建议必须：

```text
download authorization
presigned/proxy policy
scan state machine
quarantine
retention/delete/purge
storage reconcile
```

V1.1：

```text
Office conversion
advanced PDF sanitization
video transcode
CDN
dynamic watermark rendering
full DLP
```

---

# 115. SPEC 35 冻结结论

文件模块的生产安全最终冻结为：

```text
Upload != Available
Available != Previewable
Previewable != Downloadable
Logical File != Physical Object
Physical Object Exists != Authorized
Presigned URL != Permanent Permission
Deleted != Purged
Object Storage != Source of Authorization
```

整个文件生命周期必须始终受：

```text
IAM
+
State Machine
+
Scan Policy
+
Storage Policy
+
Audit
+
Recovery
```

共同约束。
