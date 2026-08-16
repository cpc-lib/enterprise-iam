# Enterprise IAM & Dynamic Authorization Platform
## 34 — Enterprise File Management, Instant Upload & Resumable Multipart Upload SPEC 1.0

> 本文将企业文件管理纳入 Enterprise IAM V1.0/V1.x 平台能力。
>
> 目标不是简单实现“上传/下载”，而是形成：
>
> ```text
> File Metadata
> + Object Storage
> + Instant Upload
> + Multipart Upload
> + Resume Upload
> + Integrity Verification
> + Business Reference
> + IAM Authorization
> + Audit
> + Lifecycle
> + Security Scan
> + Failure Recovery
> ```
>
> 文件能力不得成为绕开 Resource/Operation/Data/Field Authorization 的安全旁路。

---

# 1. 服务定位

新增：

```text
iam-file-service
```

职责：

```text
文件元数据
物理对象元数据
上传会话
分片状态
秒传判定
MinIO Multipart 编排
断点续传
完整性校验
文件业务引用
下载/预览授权
文件生命周期
扫描状态
审计事件
```

不负责：

```text
业务资源自身生命周期
业务资源权限事实源
MinIO 底层对象存储实现
```

---

# 2. 总体架构

```text
React / Business Client
        |
        v
     Gateway
        |
        v
  iam-file-service
      |       \
      |        \--> iam-authorization-service
      |
      +--> MySQL iam_file
      |
      +--> Redis(optional session acceleration)
      |
      +--> RabbitMQ / Outbox
      |
      +--> MinIO
```

大文件字节流优先：

```text
Browser
  |
  +------ Presigned Upload Part ------> MinIO
```

不要：

```text
Browser → Java Service → MinIO
```

中转几十 GB 文件。

---

# 3. 文件领域核心对象

正式拆分：

```text
Physical Object
Logical File
File Reference
Upload Session
Upload Part
Scan Record
```

原因：

同一物理对象可以被多个逻辑文件/业务资源引用。

---

# 4. Physical Object

表：

```text
iam_file_object
```

代表真正存储在 MinIO/S3 的 bytes。

字段语义：

```text
id
storage_provider
bucket_name
object_key
sha256
file_size
content_type
etag
storage_status
reference_count
created_at
updated_at
version
```

`sha256`：

```text
whole-file SHA-256
```

不能把 Multipart ETag 当作 whole SHA-256。

---

# 5. Logical File

表：

```text
iam_file
```

代表租户可见的逻辑文件。

字段：

```text
id
tenant_id
object_id
file_name
extension
content_type
file_size
sha256
owner_user_id
status
scan_status
delete_marker
version
created_at
updated_at
```

逻辑文件与物理对象：

```text
N : 1
```

---

# 6. File Reference

表：

```text
iam_file_reference
```

负责：

```text
业务资源 → 文件
```

字段：

```text
id
tenant_id
file_id
resource_id
resource_instance_key
reference_type
field_id(optional)
sort_order
status
version
created_at
```

例如：

```text
CONTRACT resource
instance=CT20260001
field=attachments
→ fileId=9001
```

---

# 7. 上传会话

表：

```text
iam_file_upload_session
```

状态：

```text
INIT
UPLOADING
VERIFYING
MERGING
COMPLETED
FAILED
ABORTED
EXPIRED
```

字段：

```text
id
tenant_id
user_id
file_name
file_size
whole_sha256
content_type
storage_upload_id
bucket_name
object_key
part_size
part_count
uploaded_part_count
status
expire_at
completed_file_id
failure_code
version
created_at
updated_at
```

---

# 8. 上传分片

表：

```text
iam_file_upload_part
```

字段：

```text
id
tenant_id
upload_session_id
part_number
part_size
part_sha256
storage_etag
status
uploaded_at
version
```

状态：

```text
PENDING
UPLOADING
UPLOADED
VERIFIED
FAILED
```

唯一：

```text
tenant_id,upload_session_id,part_number
```

---

# 9. 秒传原则

秒传：

```text
Instant Upload
```

不是：

```text
返回别人文件的 URL
```

而是：

```text
命中已有 Physical Object
↓
创建当前 Tenant 的 Logical File
↓
建立当前业务 File Reference
```

客户端永远不需要知道：

```text
其它 Tenant 是否拥有同一物理对象
```

---

# 10. 秒传 Hash

首选：

```text
SHA-256
```

判定至少：

```text
sha256 + file_size
```

不能只用：

```text
fileName
MD5 only
```

作为安全/完整性依据。

---

# 11. 跨租户物理去重

V1 推荐默认：

```text
物理存储层可跨租户去重
业务语义完全隔离
```

但是否启用：

```text
StorageDedupPolicy
```

可配置。

高隔离场景：

```text
tenant scoped physical object
```

也应支持。

---

# 12. 秒传安全

攻击者提交已知 SHA-256：

服务端不能返回：

```text
“该文件已被 Tenant B 上传”
```

只返回当前请求语义：

```text
INSTANT_AVAILABLE
or
UPLOAD_REQUIRED
```

并避免暴露对象元数据。

---

# 13. Instant Check API

```http
POST /api/v1/files/instant-check
```

Request：

```json
{
  "fileName": "demo.iso",
  "fileSize": 5368709120,
  "sha256": "..."
}
```

Response：

命中：

```json
{
  "instantAvailable": true,
  "uploadRequired": false
}
```

不直接返回其它租户文件 ID。

---

# 14. Create File From Existing Object

秒传真正完成：

```http
POST /api/v1/files/instant-create
Idempotency-Key: ...
```

Request：

```json
{
  "fileName": "demo.iso",
  "fileSize": 5368709120,
  "sha256": "...",
  "resourceId": "500",
  "resourceInstanceKey": "C1001",
  "fieldId": "7001"
}
```

服务端：

```text
Authorization
→ physical object lookup
→ logical file create
→ file reference create
→ audit
```

---

# 15. Upload Session API

```http
POST /api/v1/file-upload-sessions
```

Request：

```json
{
  "fileName": "video.mp4",
  "fileSize": 21474836480,
  "sha256": "...",
  "contentType": "video/mp4"
}
```

Response：

```json
{
  "uploadSessionId": "U1001",
  "partSize": 16777216,
  "partCount": 1280,
  "expireAt": "...",
  "status": "UPLOADING"
}
```

---

# 16. 分片大小策略

默认建议：

```text
8MB ~ 64MB
```

平台通过：

```text
FileUploadPolicy
```

配置。

选择因素：

```text
文件大小
网络
浏览器内存
MinIO/S3 限制
并发
重试成本
```

禁止散落硬编码。

---

# 17. Chunk Plan

客户端依据：

```text
partSize
partCount
```

切片。

服务端：

```text
authoritative plan
```

客户端不能自行声明：

```text
999999 part count
```

绕过资源限制。

---

# 18. Sign Upload Part API

```http
POST /api/v1/file-upload-sessions/{sessionId}/parts/{partNumber}/sign
```

返回：

```text
short-lived presigned URL
```

必须验证：

```text
session owner
session active
partNumber range
part size policy
tenant
```

---

# 19. 直传 MinIO

浏览器：

```text
PUT presigned-url
```

上传：

```text
单个 Part
```

Java 不接收大文件 body。

---

# 20. Report Uploaded Part

```http
POST /api/v1/file-upload-sessions/{sessionId}/parts/{partNumber}/complete
```

Request：

```json
{
  "etag": "...",
  "partSize": 16777216,
  "partSha256": "..."
}
```

服务端：

```text
validate
persist
```

但不能完全相信客户端 Hash。

最终 Whole Hash 仍需服务端/可信扫描链确认。

---

# 21. Query Resume State

```http
GET /api/v1/file-upload-sessions/{sessionId}/parts
```

Response：

```json
{
  "uploadedParts": [1,2,4,5,8],
  "missingParts": [3,6,7]
}
```

客户端只补：

```text
missing
```

---

# 22. 断点续传

断网/刷新后：

```text
uploadSessionId
```

恢复。

步骤：

```text
select same file
↓
verify name/size/hash
↓
load session
↓
load uploaded parts
↓
upload missing parts
```

---

# 23. localStorage 边界

React 可保存：

```text
uploadSessionId
file fingerprint
```

仅用于 UX。

服务器：

```text
Upload Session
```

才是事实源。

---

# 24. Web Worker Hash

React 计算：

```text
whole SHA-256
chunk SHA-256
```

应优先：

```text
Web Worker
```

避免阻塞 UI 主线程。

---

# 25. 超大文件 Hash

不能一次：

```text
File.arrayBuffer() 读取整个 50GB
```

必须：

```text
stream/chunk incremental hashing
```

---

# 26. 上传并发

前端默认：

```text
3~6 parallel parts
```

由：

```text
client policy
```

调节。

不能默认开：

```text
100 concurrent
```

压爆网络和 MinIO。

---

# 27. Retry

Part 上传失败：

```text
bounded retry
+
exponential backoff
+
jitter
```

超过阈值：

```text
part FAILED
```

用户可：

```text
resume
```

---

# 28. Pause / Resume

React：

```text
pause
```

仅停止：

```text
new part scheduling
```

正在上传的 Part：

可选择取消或自然完成。

Session：

```text
仍 UPLOADING
```

---

# 29. Abort Upload

```http
POST /api/v1/file-upload-sessions/{id}/abort
```

效果：

```text
Session ABORTED
MinIO AbortMultipartUpload
temporary metadata cleanup
audit
```

重复：

```text
idempotent
```

---

# 30. Complete Multipart

```http
POST /api/v1/file-upload-sessions/{id}/complete
Idempotency-Key: ...
```

流程：

```text
Validate all parts
↓
Session MERGING
↓
MinIO CompleteMultipartUpload
↓
Verify object metadata
↓
Whole integrity verification
↓
Create FileObject
↓
Create Logical File
↓
Session COMPLETED
↓
Outbox FileUploaded
```

---

# 31. “合并”语义

使用 MinIO/S3 Multipart：

```text
CompleteMultipartUpload
```

完成逻辑合并。

不要 Java：

```text
下载所有 part
→ FileOutputStream 拼接
→ 再上传
```

这会产生：

```text
磁盘
内存
网络
```

三重浪费。

---

# 32. Complete 幂等

网络断开后客户端重试：

```text
same Idempotency-Key
```

必须返回：

```text
same fileId
```

不能生成两个 Logical File。

---

# 33. Complete Race

多个并发 Complete：

```text
session version/status CAS
```

最终：

```text
one transition to COMPLETED
```

---

# 34. Whole File Integrity

最终至少确认：

```text
expected file size
expected SHA-256
```

如果对象存储端不能直接提供可信 SHA-256：

可通过：

```text
server-side background verification
```

或上传过程中建立可信 Hash Pipeline。

---

# 35. 验证状态

大文件最终 Hash 校验可能耗时。

允许：

```text
VERIFYING
```

文件：

```text
PENDING_VERIFY
```

验证通过：

```text
AVAILABLE
```

失败：

```text
QUARANTINED / FAILED
```

---

# 36. File Status

逻辑文件：

```text
PENDING
AVAILABLE
QUARANTINED
DELETED
PURGED
```

---

# 37. Scan Status

```text
NOT_SCANNED
SCANNING
CLEAN
INFECTED
SCAN_FAILED
```

V1 可以实现：

```text
VirusScan SPI
```

实际扫描器可后接。

---

# 38. 下载限制

`AVAILABLE + CLEAN`

默认才允许：

```text
DOWNLOAD
PREVIEW
```

高风险文件类型可：

```text
force download
```

禁止在线执行。

---

# 39. Download API

```http
GET /api/v1/files/{fileId}/download
```

流程：

```text
Load logical file
↓
Resolve business reference
↓
IAM authorize DOWNLOAD
↓
Check file/scan status
↓
Generate short-lived MinIO URL
↓
Audit
```

---

# 40. Preview API

```http
GET /api/v1/files/{fileId}/preview
```

Operation：

```text
PREVIEW
```

与：

```text
DOWNLOAD
```

独立。

---

# 41. File Operations

建议动态 Operation：

```text
UPLOAD
PREVIEW
DOWNLOAD
DELETE
REFERENCE
UNREFERENCE
SHARE
MANAGE
```

仍然：

```text
DB metadata
```

不是 Java permission constant。

---

# 42. 业务资源继承权限

常见模式：

```text
Contract
 └─ attachments
```

文件访问可绑定：

```text
Parent Resource Authorization
```

例如：

```text
ContractAttachment.DOWNLOAD
```

或：

```text
Contract.READ + attachment field visible
```

具体通过动态配置。

---

# 43. Field Permission 联动

如果附件属于：

```text
resource field
```

则：

```text
field hidden
```

时文件列表：

```text
不返回
```

用户即使知道 fileId：

```text
download 仍需独立 authorization
```

---

# 44. File Reference 权限

建立引用：

```text
REFERENCE
```

移除引用：

```text
UNREFERENCE
```

不能因为拥有文件：

```text
就能绑定到任意业务资源
```

---

# 45. File Owner

`owner_user_id`：

用于：

```text
管理/审计
```

不自动意味着：

```text
永久所有权限
```

最终仍经 Authorization Engine。

---

# 46. 文件删除语义

删除 Logical File：

```text
DELETED
```

如果仍有其它 Reference：

```text
按策略阻止删除
```

或只删除某 Reference。

---

# 47. Physical Object Purge

只有：

```text
reference_count = 0
AND retention reached
```

才允许：

```text
PURGE
```

由 Job 执行。

---

# 48. Reference Count

`reference_count` 是：

```text
storage optimization
```

不能单独作为一致性事实。

应能通过：

```text
iam_file / iam_file_reference
```

Reconcile。

---

# 49. Orphan Cleanup

需要 Job：

```text
Expired Upload Session Cleanup
Aborted Multipart Cleanup
Orphan Object Reconcile
Deleted File Purge
Scan Retry
```

---

# 50. Upload Session Expiry

默认：

```text
24h ~ 7d
```

通过策略配置。

到期：

```text
Session EXPIRED
```

MinIO Multipart：

```text
Abort
```

---

# 51. Storage Object Key

不要直接：

```text
fileName
```

作为对象 Key。

推荐：

```text
content-addressed / random path
```

例如：

```text
objects/ab/cd/<sha256-or-ulid>
```

用户文件名只作为 Metadata。

---

# 52. 文件名安全

必须防：

```text
../
\
control chars
null byte
overlong name
```

下载时：

```text
Content-Disposition
```

安全编码。

---

# 53. Content Type

客户端：

```text
contentType
```

仅作为提示。

服务端应：

```text
MIME sniff / scanner
```

验证。

---

# 54. Extension Mismatch

例如：

```text
.exe renamed .jpg
```

扫描发现 mismatch：

```text
quarantine / risk policy
```

---

# 55. Zip Bomb

预览/解压功能未来必须防：

```text
zip bomb
nested archive
```

V1 不自动解压未知归档。

---

# 56. Large File DoS

限制：

```text
maxFileSize
maxParts
maxConcurrentSessionsPerUser
tenantQuota
rate limit
```

---

# 57. Tenant Quota

建议：

```text
iam_file_quota_policy
```

字段：

```text
tenant_id
max_total_bytes
max_single_file_bytes
max_active_upload_sessions
max_daily_upload_bytes
version
```

---

# 58. Quota Enforcement

创建 Upload Session 时：

```text
check quota
```

Complete 时：

```text
re-check
```

防并发超额。

---

# 59. File Storage Quota Metrics

至少：

```text
tenant used bytes
active sessions
upload throughput
failed parts
```

Tenant ID 不作为 Prometheus 高基数 Label 时，可存业务指标表/日志查询。

---

# 60. Upload Authorization

Create Session：

```text
UPLOAD
```

签 Part URL：

必须继续验证：

```text
session owner
session state
```

不能只靠知道 sessionId。

---

# 61. Download URL

Presigned URL：

```text
short TTL
```

建议：

```text
30s ~ 5min
```

根据文件大小/网络可调整。

不能：

```text
7 day public URL
```

作为授权替代。

---

# 62. Presigned URL Revocation Limitation

URL 生成后直到 TTL 结束：

```text
对象存储可能仍接受
```

因此高安全资源：

```text
TTL 必须短
```

必要时改为：

```text
authenticated proxy download
```

这属于 Resource Policy。

---

# 63. Secure Download Modes

支持：

```text
PRESIGNED
PROXY
```

PRESIGNED：

```text
性能优先
```

PROXY：

```text
实时撤权优先
```

由 Resource/File Policy 配置。

---

# 64. File Policy

表：

```text
iam_file_access_policy
```

可配置：

```text
download_mode
presigned_ttl
scan_required
max_file_size
allowed_mime_types
allowed_extensions
instant_upload_enabled
cross_tenant_physical_dedup
```

---

# 65. File Scan Record

表：

```text
iam_file_scan_record
```

字段：

```text
id
tenant_id
file_id
scanner
scanner_version
scan_status
result_code
risk_level
started_at
completed_at
version
```

不记录敏感文件内容。

---

# 66. File Operation Audit

表/事件：

```text
iam_file_operation_log
```

或者统一进入 Audit Service。

至少：

```text
UPLOAD
INSTANT_UPLOAD
DOWNLOAD
PREVIEW
DELETE
REFERENCE
UNREFERENCE
ABORT
SCAN_INFECTED
```

---

# 67. Domain Events

至少：

```text
FileUploaded
FileInstantCreated
FileAvailable
FileQuarantined
FileDeleted
FilePurged
FileReferenceCreated
FileReferenceRemoved
UploadSessionExpired
```

---

# 68. FileUploaded Event

Producer：

```text
File Service
```

Consumers：

```text
Audit
Scan Processor
Business optional
```

---

# 69. FileAvailable Event

扫描/校验通过：

```text
AVAILABLE
```

业务服务可据此：

```text
刷新附件状态
```

---

# 70. FileDeleted Event

业务引用：

```text
不能直接因为 logical file deleted 就删除 business resource
```

Consumer 只更新：

```text
reference/read model
```

---

# 71. Transaction — Create Session

本地：

```text
upload_session
+
idempotency(optional)
+
outbox(optional)
```

MinIO CreateMultipartUpload：

推荐：

```text
remote call before/after controlled state
```

需要失败补偿。

---

# 72. Create Session Saga-lite

推荐：

```text
DB INIT
↓
MinIO create multipart
↓
DB UPLOADING
```

若 MinIO 成功、DB 更新失败：

```text
cleanup job abort orphan multipart
```

---

# 73. Transaction — Part Complete

```text
part upsert
uploaded_part_count reconcile
session version
```

短本地事务。

---

# 74. Transaction — Complete

关键：

```text
session CAS MERGING
```

先占用完成权。

远程：

```text
MinIO CompleteMultipartUpload
```

然后：

```text
file_object
logical file
session COMPLETED
outbox
```

需要：

```text
recovery marker
```

处理 MinIO 已完成但 DB 未落地。

---

# 75. Merge Recovery

Job：

```text
MERGING session reconcile
```

检查 MinIO object：

如果存在且完整：

```text
repair DB metadata
```

否则：

```text
retry/failed
```

---

# 76. Instant Create Transaction

```text
lookup file_object
↓
create logical file
↓
create reference(optional)
↓
increment/ref reconcile metadata
↓
outbox
↓
idempotency success
```

同本地事务。

---

# 77. Duplicate Physical Object Race

两个不同上传同时完成相同 SHA-256。

DB：

```text
unique(sha256,file_size,storage_scope)
```

最终：

```text
one canonical object
```

多余对象：

```text
orphan cleanup
```

---

# 78. Physical Object Unique Scope

如果跨租户去重：

```text
storage_provider,bucket,sha256,file_size
```

如果 Tenant scoped：

```text
tenant_scope,sha256,file_size
```

策略在设计中固定。

---

# 79. Upload Resume Across Device

理论支持：

```text
same user
same tenant
same file fingerprint
```

重新选择文件后继续。

不能依赖：

```text
browser File handle persistent access
```

---

# 80. Multipart API 幂等

```text
create session
part complete
complete
abort
instant create
```

都必须：

```text
retry-safe
```

---

# 81. Part Number

必须：

```text
1..partCount
```

禁止：

```text
0
negative
>partCount
```

---

# 82. Part Size Validation

除最后一片外：

```text
必须与 planned partSize 匹配
```

最后一片：

```text
<= partSize
```

---

# 83. Uploaded Part Trust

不能只信客户端报告：

```text
etag
```

File Service 可以通过：

```text
ListParts / Head
```

和 MinIO 核对。

---

# 84. Complete 前校验

必须确认：

```text
所有 part numbers 完整
无重复
size total matches
session not expired
session owner authorized
```

---

# 85. Upload Progress

服务端：

```text
uploaded_part_count
```

只是缓存字段。

事实源：

```text
upload_part rows / MinIO parts
```

需要可 reconcile。

---

# 86. React Feature

新增：

```text
frontend/iam-admin-ui/src/features/file-management/
```

结构：

```text
api/
components/
hooks/
store/
workers/
types/
pages/
```

---

# 87. React Uploader

组件：

```text
FileUploader
UploadQueue
UploadProgress
ChunkProgress
ResumeUploadDialog
FilePreview
```

---

# 88. React Store

Zustand：

```text
upload queue
session mapping
pause state
retry state
progress
```

不保存：

```text
permission decision
```

作为安全事实。

---

# 89. Browser Hash Worker

Worker：

```text
incremental SHA-256
```

输出：

```text
fileHash
partHash
progress
```

---

# 90. Browser Network Recovery

监听：

```text
online/offline
```

offline：

```text
pause scheduling
```

online：

```text
refresh session state
resume missing parts
```

---

# 91. Browser Retry Key

同一个 Part retry：

```text
same session + partNo
```

不需要生成新 Upload Session。

---

# 92. UI 秒传

用户体验：

```text
Hashing
→ Checking
→ Instant Complete
```

但如果 Hash 计算本身很慢：

显示：

```text
hash progress
```

不能假装“瞬间”。

---

# 93. Upload Queue

支持：

```text
multiple files
per-file concurrency
global concurrency
```

必须有限制。

---

# 94. File Management Page

至少：

```text
My Files
Referenced Files
Uploading
Quarantined
Deleted
```

管理员可有：

```text
Storage/Scan diagnostics
```

需授权。

---

# 95. Business Attachment Component

提供通用：

```text
<ResourceAttachment
  resourceId
  instanceKey
  fieldId
/>
```

但它只负责 UI。

后端始终：

```text
re-authorize
```

---

# 96. API 总览

> 路径与参数命名的唯一权威是冻结机器契约 docs/api/openapi-code-phase-03-sharing-file.yaml（其采用 file-uploads、uploadId、partNumber、fileId 命名）。本节为概念总览，与机器契约冲突时以机器契约为准（裁决优先级见 docs/spec/README.md）。

```text
POST /api/v1/files/instant-check
POST /api/v1/files/instant-create

POST /api/v1/file-upload-sessions
GET  /api/v1/file-upload-sessions/{id}
GET  /api/v1/file-upload-sessions/{id}/parts
POST /api/v1/file-upload-sessions/{id}/parts/{partNo}/sign
POST /api/v1/file-upload-sessions/{id}/parts/{partNo}/complete
POST /api/v1/file-upload-sessions/{id}/complete
POST /api/v1/file-upload-sessions/{id}/abort

GET  /api/v1/files/{id}
GET  /api/v1/files/{id}/download
GET  /api/v1/files/{id}/preview
DELETE /api/v1/files/{id}

POST   /api/v1/files/{id}/references
DELETE /api/v1/files/{id}/references/{referenceId}
```

---

# 97. 管理 API

```text
/admin/v1/files
/admin/v1/file-policies
/admin/v1/file-scans
/admin/v1/file-storage
/admin/v1/file-upload-sessions
```

---

# 98. Internal API

```text
/internal/v1/files/reference-check
/internal/v1/files/resource-deleted
/internal/v1/files/reconcile
```

必须：

```text
service identity
```

---

# 99. Error Codes

至少：

```text
IAM_FILE_NOT_FOUND
IAM_FILE_NOT_AVAILABLE
IAM_FILE_QUARANTINED
IAM_FILE_UPLOAD_SESSION_NOT_FOUND
IAM_FILE_UPLOAD_SESSION_EXPIRED
IAM_FILE_UPLOAD_SESSION_INVALID_STATE
IAM_FILE_PART_INVALID
IAM_FILE_PART_HASH_MISMATCH
IAM_FILE_WHOLE_HASH_MISMATCH
IAM_FILE_SIZE_MISMATCH
IAM_FILE_QUOTA_EXCEEDED
IAM_FILE_MIME_NOT_ALLOWED
IAM_FILE_SCAN_REQUIRED
IAM_FILE_SCAN_INFECTED
IAM_FILE_INSTANT_NOT_AVAILABLE
IAM_FILE_DOWNLOAD_DENIED
```

---

# 100. Security Threats

新增重点威胁：

```text
Hash enumeration
Presigned URL leakage
Part overwrite
Part number injection
Cross-tenant instant-upload leak
Malicious MIME
Executable upload
Zip bomb
Quota exhaustion
Multipart orphan abuse
Huge upload DoS
Reference hijack
Download after permission revoke
```

---

# 101. Presigned Part URL Security

URL：

```text
short TTL
specific object/uploadId/partNo
```

不能：

```text
wildcard bucket write
```

---

# 102. Part URL Reuse

重复上传同一 Part：

允许：

```text
replace before completion
```

是否允许由存储协议决定。

Complete 前服务端必须以最终：

```text
ListParts
```

为准。

---

# 103. Cross-Tenant File Access

任何 fileId：

```text
tenant mismatch
→ DENY
```

即使物理 object 相同。

---

# 104. Reference Hijack

攻击者把自己的 File：

```text
reference 到别人的 Contract
```

必须检查：

```text
REFERENCE permission
+
target resource instance authorization
```

---

# 105. Delete Bypass

知道 fileId：

```text
DELETE
```

不能仅靠：

```text
owner_user_id
```

必须：

```text
DELETE operation
```

动态授权。

---

# 106. Download Immediate Revocation

如果采用：

```text
PROXY
```

可以做到每请求实时授权。

如果采用：

```text
PRESIGNED
```

撤权后旧 URL 在 TTL 内可能有效。

因此：

```text
高安全资源必须短 TTL / PROXY
```

这个限制必须写入 Security Policy。

---

# 107. File Scan Failure

扫描服务不可用：

默认：

```text
Fail Closed for DOWNLOAD
```

如果策略：

```text
scan_required=true
```

文件保持：

```text
PENDING/QUARANTINED
```

---

# 108. Metrics

至少：

```text
iam_file_upload_session_total
iam_file_upload_part_total
iam_file_upload_part_retry_total
iam_file_upload_bytes_total
iam_file_instant_upload_total
iam_file_complete_failure_total
iam_file_resume_total
iam_file_quarantined_total
iam_file_orphan_object_total
iam_file_presigned_url_total
```

---

# 109. SLO 初始目标

API：

```text
Create Session P95 < 300ms
Sign Part P95 < 150ms
List Resume Parts P95 < 300ms
Complete Metadata Phase P95 < 1s
```

大文件 Merge：

取决于：

```text
Object Storage Multipart Complete
```

不承诺固定秒数。

---

# 110. Upload Throughput

主要取决：

```text
Client ↔ MinIO
```

Java Service 不应成为字节流瓶颈。

---

# 111. Capacity Baseline

建议压测：

```text
100 concurrent upload sessions
6 parts/session client concurrency
10GB single file
100GB max-policy test
1M logical file rows
10M file references
```

根据环境调整。

---

# 112. Object Storage Health

监控：

```text
MinIO availability
PUT latency
GET latency
storage usage
disk health
multipart incomplete count
```

---

# 113. Runbook — Multipart Stuck

检查：

```text
session state
MinIO uploadId
uploaded parts
DB part rows
expire time
```

动作：

```text
reconcile
complete
or abort
```

---

# 114. Runbook — Object Exists But DB Missing

属于：

```text
merge recovery
```

流程：

```text
Head object
verify
rebuild file_object
complete logical metadata
audit recovery
```

---

# 115. Runbook — DB Complete But Object Missing

严重一致性错误。

文件：

```text
QUARANTINED/FAILED
```

禁止继续下载。

触发：

```text
Security/Infrastructure event
```

---

# 116. Runbook — Hash Mismatch

```text
abort completion
quarantine object
mark session FAILED
audit
```

不得：

```text
忽略 hash
```

---

# 117. File Job

建议：

```text
UploadSessionExpiryJob
MultipartOrphanCleanupJob
FileObjectReconcileJob
FilePurgeJob
ScanRetryJob
QuotaReconcileJob
```

---

# 118. Database

新增：

```text
iam_file
```

数据库。

Owner：

```text
iam-file-service
```

---

# 119. Flyway Plan

```text
V1__file_object_logical_file.sql
V2__file_reference.sql
V3__file_upload_session_part.sql
V4__file_policy_quota.sql
V5__file_scan.sql
V6__file_infrastructure.sql
V7__file_indexes.sql
```

---

# 120. Infrastructure Tables

iam_file 也本地拥有：

```text
sys_outbox_event
sys_idempotency_record
sys_message_consume_record
```

---

# 121. Critical Indexes

```text
iam_file:
tenant_id,status,created_at,id

iam_file_object:
sha256,file_size

iam_file_reference:
tenant_id,resource_id,resource_instance_key,field_id,status

iam_file_upload_session:
tenant_id,user_id,status,updated_at,id

iam_file_upload_part:
tenant_id,upload_session_id,part_number
```

---

# 122. E2E Closed Loop 1 — Normal Upload

```text
Create Session
→ Upload all parts
→ Complete
→ Scan CLEAN
→ File AVAILABLE
→ Reference resource
→ Download authorized
```

---

# 123. E2E Closed Loop 2 — Instant Upload

```text
File object already exists
→ instant check
→ instant create
→ new logical file
→ no byte upload
→ reference
→ download
```

---

# 124. E2E Closed Loop 3 — Resume

```text
Upload parts 1,2,4
→ network interrupted
→ new page/session
→ query parts
→ upload 3,5...
→ complete
```

---

# 125. E2E Closed Loop 4 — Permission

```text
User can read parent resource
→ download allowed
→ revoke parent permission
→ next proxy download denied
```

Presigned 模式：

验证：

```text
new URL cannot be generated
```

---

# 126. E2E Closed Loop 5 — Cross Tenant

```text
Tenant A upload
Tenant B knows hash/file name
→ cannot discover A metadata
→ instant availability does not disclose tenant identity
→ no access to A logical file
```

---

# 127. E2E Closed Loop 6 — Hash Failure

```text
wrong part/whole hash
→ complete denied
→ file unavailable
```

---

# 128. E2E Closed Loop 7 — Expired Session

```text
session expired
→ sign part denied
→ complete denied
→ cleanup aborts multipart
```

---

# 129. E2E Closed Loop 8 — Concurrent Complete

```text
20 concurrent complete
→ one physical/logical completion
→ same idempotent result
```

---

# 130. Security Release Gates

Blocking：

```text
cross-tenant logical file leak
hash enumeration leak
unauthorized reference
unauthorized download
quarantined file downloadable
expired/revoked file still downloadable in proxy mode
multipart complete creates duplicate business file
path traversal
presigned wildcard access
```

---

# 131. React Tests

至少：

```text
hash worker
pause/resume
offline/online resume
retry
instant upload
complete retry
multiple files
```

---

# 132. Backend Tests

至少：

```text
session state machine
part boundary
part duplicate
hash mismatch
complete concurrency
instant create concurrency
quota
reference permission
delete/ref count
orphan reconcile
```

---

# 133. MinIO Integration Tests

Testcontainers / real MinIO：

```text
CreateMultipartUpload
UploadPart
ListParts
Complete
Abort
Presigned PUT
Presigned GET
```

---

# 134. Story Mapping

新增 Epic：

```text
E15 Enterprise File Management
```

Stories：

```text
S180 File service/module
S181 File Flyway
S182 MinIO client abstraction
S183 File object/logical file
S184 Upload session
S185 Multipart part signing
S186 Resume state
S187 Complete/merge
S188 Instant upload
S189 File reference
S190 Download/preview auth
S191 React uploader
S192 Hash worker
S193 Pause/resume/retry
S194 File management pages
S195 Cleanup/reconcile jobs
S196 Scan SPI
S197 File E2E/security
```

---

# 135. 周期影响

一人 + AI：

建议：

```text
15~20 person-days
```

即：

```text
约 +3~4 周
```

如果纳入 V1.0 全功能。

如果保持原 24 周：

可将：

```text
File Management
```

设为：

```text
V1.1
```

或者压缩非核心 UI/Simulator/高级 Audit UI。

---

# 136. 推荐版本策略

如果文件能力是项目刚需：

正式建议：

```text
V1.0 纳入基础文件上传/断点/秒传/权限
```

高级：

```text
Virus scan
Preview conversion
Office/PDF rendering
Content extraction
CDN
```

放：

```text
V1.1
```

---

# 137. 项目目录

新增：

```text
backend/iam-file-service/
```

Java 包最终建议：

```text
com.enterprise.iam.file
```

而不是：

```text
com.enterprise.iam.iam_file_service
```

---

# 138. File Service DDD

```text
interfaces/
application/
domain/
infrastructure/
```

Domain：

```text
FileObject
LogicalFile
UploadSession
UploadPart
FileReference
FilePolicy
```

---

# 139. MinIO Adapter

Infrastructure：

```text
ObjectStoragePort
MinioObjectStorageAdapter
```

Domain/Application 不依赖：

```text
io.minio.MinioClient
```

---

# 140. ObjectStoragePort

至少：

```text
createMultipartUpload
signUploadPart
listParts
completeMultipartUpload
abortMultipartUpload
headObject
signDownload
deleteObject
```

---

# 141. Storage Provider Abstraction

虽然 V1 使用：

```text
MinIO
```

但 Domain：

```text
ObjectStoragePort
```

便于未来支持：

```text
AWS S3
OSS
COS
```

---

# 142. File Business Closed Loop

最终要求：

```text
权限允许上传
→ 上传
→ 中断
→ 恢复
→ 完成
→ 扫描
→ 建立业务引用
→ 权限允许下载
→ 撤权
→ 新下载被拒绝
→ 删除引用
→ 生命周期清理
→ 审计可追
```

这才算文件模块完成。

---

# 143. SPEC 34 冻结结论

企业文件能力正式采用：

```text
Logical File / Physical Object Separation
+
SHA-256 Instant Upload
+
S3/MinIO Multipart
+
Server-Side Upload Session
+
Resume by Missing Parts
+
Idempotent Complete
+
No Java Byte-Stream Merge
+
IAM Authorization on Reference/Download
+
Short-Lived Presigned URL / Proxy Option
+
Quarantine & Scan State
+
Lifecycle Cleanup
+
Outbox/Audit/Recovery
```

文件模块不能成为：

```text
“拿到 URL 就能访问”
```

的旁路系统。
