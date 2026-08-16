# Enterprise IAM & Dynamic Authorization Platform
## 23 — Solo Developer + AI Development Plan SPEC 1.0

> 目标：在“一名开发者 + AI”条件下，把前述 IAM / 动态授权平台从 SPEC 推进到可运行、可测试、可部署、可演示、可继续迭代的 V1.0。
>
> 本计划不是理想化排期，而是按真实企业项目的依赖关系、返工成本、测试成本和安全风险设计。

---

# 1. 项目交付目标

V1.0 必须至少交付：

- Java 微服务后端
- React + TypeScript 管理端
- MySQL / Redis / RabbitMQ / Nacos / PowerJob / MinIO 开发环境
- Seata 能力接入，但不滥用
- Gateway 动态 API 映射
- Authentication + Session + Refresh Token
- RBAC
- Team / Team Role
- Data Scope
- Field Permission
- Cross-Team Resource ACL / Share
- Authorization Explain
- Audit
- HTTP / MQ / Job 幂等
- Outbox
- Permission Version
- Projection
- Flyway
- Testcontainers
- Playwright 核心 E2E
- Docker Compose
- Windows PowerShell 启动脚本
- README
- 核心监控指标
- 核心安全测试

V1.0 的目标不是一次实现所有高级能力。

以下功能建议 V1.1 / V2.0 再做：

- WebAuthn / Passkey 完整实现
- Flowable 高风险权限审批
- ClickHouse 审计冷热分层
- Kubernetes Helm Chart
- 多地域容灾
- 完整 ABAC 可视化编辑器
- Resource Collection Share
- Folder ACL Inheritance
- 动态 Map Field Schema
- 全平台 Low-Code
- 高级 SoD
- 完整 SIEM 集成

---

# 2. 项目周期基线

## 2.1 全职一人 + AI

假设：

```text
每周 5 天
每天有效开发 6~8 小时
AI 用于：
架构检查
代码骨架
测试生成
DDL
重构
Code Review
文档
脚本
```

推荐正式工期：

```text
24 周
```

其中：

```text
核心开发       16 周
集成完善        4 周
测试与安全      2 周
RC / 缓冲       2 周
```

即：

```text
约 5.5 ~ 6 个月
```

这是较合理的 V1.0 企业级排期。

## 2.2 如果只做 MVP

可以压缩到：

```text
10 ~ 12 周
```

但 MVP 不包含全部高级审计、复杂 Field Engine、完整投影重建、安全中心等能力。

## 2.3 如果兼职开发

每天只有：

```text
2~4 小时
```

建议：

```text
36 ~ 44 周
```

不要简单按全职工期乘比例，因为上下文切换会增加额外成本。

---

# 3. 项目里程碑

| 里程碑 | 周期 | 结果 |
|---|---:|---|
| M0 SPEC Freeze | Week 1 | 项目结构、DDL、API、核心规则冻结 |
| M1 Foundation | Week 2-4 | 微服务骨架、Gateway、基础设施可运行 |
| M2 Auth MVP | Week 5-6 | 登录、JWT、Session、Refresh 可用 |
| M3 Authorization MVP | Week 7-9 | RBAC + Team + 动态 API 授权 |
| M4 Data / Field Security | Week 10-12 | 数据权限、字段权限闭环 |
| M5 Cross-Team ACL | Week 13-14 | 分享、撤销、过期、Projection |
| M6 Consistency | Week 15-16 | 幂等、Outbox、MQ、Job |
| M7 React Admin Beta | Week 17-19 | 管理后台主流程可操作 |
| M8 Audit / Explain | Week 20 | 审计、Explain、安全诊断 |
| M9 Release Candidate | Week 21-22 | E2E、安全、性能、故障测试 |
| M10 V1.0 | Week 23-24 | Docker、文档、修复、发布 |

---

# 4. 开发方法

每一个核心模块采用：

```text
SPEC
 ↓
Domain Model
 ↓
Test Case
 ↓
Implementation
 ↓
Integration Test
 ↓
Documentation
```

核心领域采用：

```text
SDD + TDD
```

普通 CRUD 不强制全部 TDD。

重点 TDD：

- PermissionMergeEngine
- DataScopeMergeEngine
- FieldPermissionMergeEngine
- ResourceShare Aggregate
- RefreshToken State Machine
- Idempotency Core
- Permission Version
- Projection Version

---

# 5. AI 协作模型

AI 不应承担：

```text
“直接一次生成整个项目”
```

正确模式：

```text
Developer = Architect + Reviewer + Integrator
AI        = Pair Programmer + Test Generator + Reviewer
```

每个任务控制在：

```text
0.5 ~ 1.5 天
```

以内。

错误任务：

```text
“帮我把整个 IAM 系统写完”
```

正确任务：

```text
“根据 Authorization SPEC，
实现 PermissionMergeEngine，
先生成 25 个 JUnit 参数化测试，
测试通过后再生成实现。”
```

---

# 6. 每日开发工作流

每天建议：

## 上午

```text
08:30
Review 昨日代码

09:00
确定今天 1~2 个具体任务

09:15
让 AI 根据 SPEC 生成测试设计

10:00
人工检查测试

10:30
编码
```

## 下午

```text
13:30
继续实现

15:30
Integration Test

16:30
AI Code Review

17:00
修复问题

17:30
更新 SPEC / ADR / TODO
```

每日目标：

```text
完成 1 个可验收的 Vertical Slice
```

而不是：

```text
同时修改 8 个服务
```

---

# 7. 每周工作节奏

## 周一

- 本周目标
- 拆 Story
- 定义验收条件

## 周二~周四

- 主开发
- 单测
- 集成测试

## 周五

- E2E / Regression
- Code Review
- 重构
- 文档
- Docker 验证
- 下周风险检查

每周五必须保证：

```text
main branch 可构建
核心测试全绿
```

---

# 8. Week 1 — SPEC Freeze & Repository Bootstrap

目标：

```text
把“设计”正式转成“代码项目”
```

任务：

- 最终 Maven Module 划分
- Service Ownership
- Database Ownership
- Flyway Directory
- API Error Model
- Event Envelope
- Permission / Share / Auth Enum Freeze
- React 目录
- Docker Compose 目录
- CI 基础目录
- Coding Standards
- ADR 模板

交付：

```text
enterprise-iam/
```

空骨架可以：

```text
mvn clean verify
```

成功。

React：

```text
pnpm build
```

成功。

---

# 9. Week 2 — Common Framework

实现：

```text
iam-common-core
iam-common-web
iam-common-tenant
iam-common-security
iam-common-mybatis
iam-common-redis
iam-common-mq
```

完成：

- ApiResult
- ErrorCode
- Global Exception Handler
- TraceId
- TenantContext
- ActorContext
- MyBatis 基础配置
- Redis Template
- MQ Event Envelope

测试：

```text
Common Unit Test
ArchUnit
```

---

# 10. Week 3 — Infrastructure

完成 Docker Compose：

```text
MySQL
Redis
RabbitMQ
Nacos
PowerJob
MinIO
```

Seata：

```text
先接入环境
不立即使用
```

完成：

- `.env.example`
- Windows PowerShell Scripts
- Health Check
- Flyway baseline
- Nacos registration

验收：

```text
start-infra.ps1
```

能够启动全部核心基础设施。

---

# 11. Week 4 — Gateway + API Discovery

实现：

```text
iam-gateway
```

重点：

- JWT Verification Framework
- Dynamic API Security Policy
- API Mapping Cache
- Strip spoof headers
- Signed Delegation Context
- Rate Limit Framework
- PUBLIC / AUTH_REQUIRED / INTERNAL_ONLY
- API Discovery Skeleton

此阶段 Authorization Engine 可以先 Mock。

目标：

```text
Gateway → Demo Business Service
```

完整跑通。

---

# 12. Week 5 — Authentication

实现：

```text
iam-auth-service
```

完成：

- User credential login
- Password hash
- Session
- JWT Access Token
- Redis Session
- User Security State
- Login Audit Event

必须完成测试：

```text
wrong password
disabled user
invalid JWT
session expired
```

---

# 13. Week 6 — Refresh Token & Session Security

完成：

- Refresh Token
- Token Rotation
- Token Family
- Concurrent Refresh
- Logout
- Logout All
- Force Logout
- Password Change
- Token Version

React 同步完成：

- Login Page
- Auth Store
- Axios
- Refresh Single Flight

MVP 认证链完成。

---

# 14. Week 7 — Identity / Role

实现：

```text
iam-identity-service
```

完成：

- Tenant
- User
- Role
- UserRole
- Application
- Service
- Resource
- Operation

先完成：

```text
RBAC metadata
```

不立即做 Data / Field。

---

# 15. Week 8 — Organization / Team

实现：

```text
iam-organization-service
```

完成：

- Organization
- Team
- Team Tree
- Team Member
- Team Role
- Team Member Role

事件：

```text
TeamMemberChanged
TeamRoleChanged
```

Authorization Read Model 开始消费。

---

# 16. Week 9 — Authorization Engine MVP

实现：

```text
iam-authorization-service
```

完成：

- AuthorizationRequest
- SubjectResolver
- GrantResolver
- UserRole
- TeamRole
- Direct Grant
- PermissionMergeEngine
- Hard Guard
- AuthorizationResult
- Batch Authorization
- PermissionVersion

必须建立：

```text
Golden Authorization Scenarios
```

到这里必须实现：

```text
No Permission String In Business Code
```

---

# 17. MVP Milestone

Week 9 结束必须能够演示：

```text
Admin 创建 User
↓
创建 Role
↓
创建 Resource
↓
配置 Operation
↓
Role 授权
↓
User 登录
↓
调用 Gateway
↓
Authorization ALLOW / DENY
```

如果这个 Vertical Slice 未稳定：

```text
不要继续 Data Permission
```

先修稳。

---

# 18. Week 10 — Data Permission Core

实现：

```text
iam-data-permission-spring-boot-starter
```

第一阶段仅支持：

```text
SELECT
COUNT
Pagination
SELF
TEAM
ALL
```

实现：

- ResourceDataSchema
- DataPredicate
- JSqlParser
- MyBatis Interceptor
- Request Cache
- Fail Closed

---

# 19. Week 11 — Advanced Data Permission

继续：

```text
TEAM_AND_CHILDREN
SPECIFIED_TEAM
SHARED
UPDATE
DELETE
JOIN
```

ACL Projection 暂用 Mock 数据。

加入：

```text
Million-row Integration Test
```

检查 SQL EXPLAIN。

---

# 20. Week 12 — Field Permission

实现：

```text
iam-field-permission-spring-boot-starter
```

完成：

- READ
- WRITE
- MASK
- HIDDEN
- Field Metadata
- Request Field Presence
- Explicit Null
- Nested Path
- Jackson Response Filter
- MyBatis Update Column Guard

React：

```text
Dynamic Form Field Rendering
```

---

# 21. Beta Security Milestone

Week 12 结束必须通过：

```text
Cross Tenant Denied

Data Scope Denied

Field Read Hidden

Field Write Denied

Manual API Attack Denied
```

如果不能通过：

```text
禁止进入 Share 开发
```

---

# 22. Week 13 — Sharing Service

实现：

```text
iam-sharing-service
```

完成：

- USER
- TEAM
- ROLE
- TEAM_ROLE
- Share Operation
- Share Field
- Start / Expire
- Permission Convergence
- Field Convergence
- canReshare
- maxDepth

---

# 23. Week 14 — ACL Projection

完成：

- Share Outbox
- RabbitMQ Event
- Local ACL Projection
- Version
- Revoke
- Expire
- PowerJob Reconcile
- Projection Rebuild

必须通过：

```text
share allows access
revoke immediately denies
expired share denies without PowerJob
```

---

# 24. Week 15 — HTTP Idempotency

实现：

```text
iam-idempotent-spring-boot-starter
```

完成：

- Idempotency-Key
- Canonical Hash
- PROCESSING
- SUCCESS
- FAILED
- Replay
- Lease
- Conflict

优先接入：

```text
Create Share
Grant Role
Create User
```

---

# 25. Week 16 — MQ / Outbox / Job Consistency

完成：

- Outbox
- Publisher Confirm
- Mandatory Return
- Consumer Idempotency
- Retry Queue
- DLQ
- Event Version
- Gap Detection
- Job Business State
- Seek Pagination

完成故障场景：

```text
DB commit before ACK crash
MQ down
duplicate message
out-of-order event
```

---

# 26. Beta Backend Milestone

Week 16 后：

后端核心能力必须完整：

```text
Auth
RBAC
Team
Data
Field
Share
Idempotency
MQ
Job
Audit Event Skeleton
```

从此阶段开始：

```text
禁止大规模改 Domain Model
```

否则工期会失控。

---

# 27. Week 17 — React Admin Foundation

完成：

- Layout
- Dynamic Navigation
- Dynamic Routes
- Auth Store
- Permission Store
- API Client
- Error Boundary
- Page Schema
- Common Table
- Common Drawer
- Common Form

目标：

```text
管理端基础框架稳定
```

---

# 28. Week 18 — Identity / Role / Team UI

完成：

```text
User Management
Role Management
Team Management
Team Member
Team Role
```

重点：

- Permission Matrix
- Dynamic Operation
- Optimistic Lock
- Diff Preview

---

# 29. Week 19 — Authorization UI

完成：

- Resource
- Operation
- API Mapping
- Data Scope UI
- Field Policy UI
- Mask Strategy UI
- Resource Share Drawer

完成核心管理闭环。

---

# 30. Week 20 — Audit & Explain

实现：

```text
iam-audit-service
```

完成：

- Login Audit
- Admin Audit
- Permission Change Audit
- Authorization Decision Audit
- Security Event

React：

```text
Audit Center
Authorization Explain
Security Center Basic
```

此周不做 ClickHouse。

---

# 31. Week 21 — End-to-End Tests

集中完成：

```text
Playwright
```

Golden E2E：

1. Login
2. Role grant
3. Immediate revoke
4. Team role
5. Data scope
6. Field mask
7. Field write attack
8. Cross-team share
9. Share revoke
10. Share expire
11. Idempotency
12. Authorization Explain

---

# 32. Week 22 — Security & Failure Testing

执行：

```text
Tenant spoofing

User spoofing

Resource spoofing

IDOR

Mass Assignment

JWT attack

Refresh reuse

ACL escalation

MQ duplicate

MQ ordering

Redis failure

RabbitMQ failure

PowerJob failure

Authorization failure
```

修复优先级：

```text
Security
>
Correctness
>
Performance
>
UI polish
```

---

# 33. Week 23 — Docker / Deployment RC

完成：

```text
docker-compose.full.yml
```

所有服务容器化。

完成：

- Dockerfile
- Health Check
- Startup Script
- Build Script
- Flyway
- Full Demo
- Backup / Restore Documentation

发布：

```text
RC1
```

---

# 34. Week 24 — V1.0 Stabilization

此周原则：

```text
不增加新 Feature
```

只允许：

```text
Bug Fix
Performance Fix
Security Fix
Documentation
Deployment Fix
```

完成：

- Final Regression
- Release Checklist
- Architecture Diagram
- README
- API Guide
- Demo Data
- V1.0 Tag

---

# 35. 工期缓冲

24 周已经包含一定缓冲。

但以下模块风险最大：

| 模块 | 风险 |
|---|---|
| DataPermission SQL AST | 高 |
| Field Permission Serialization | 高 |
| Cross-Team ACL | 高 |
| Refresh Token 并发 | 中高 |
| Projection Consistency | 高 |
| React Permission Matrix | 中 |
| Docker Full Integration | 中 |

如果发生延期：

```text
优先砍非核心 UI
```

不要砍：

```text
安全测试
幂等
权限校验
数据隔离
```

---

# 36. MVP 范围

如果希望先快速获得可演示版本：

## MVP 10~12 周

包含：

```text
Auth

User

Role

Team

Resource

Operation

Dynamic API Mapping

RBAC

Basic Data Scope

Basic Field Permission

React Admin Basic

Docker Infrastructure
```

暂不包含：

```text
Reshare

Advanced ACL Graph

Security Center

Advanced Audit

DLQ UI

Complex Simulator

ClickHouse
```

---

# 37. Beta 范围

建议：

```text
Week 16
```

达到 Beta。

Beta 必须：

```text
Backend Core Complete

Core Security Tests Green

Sharing Works

Idempotency Works

MQ / Projection Works
```

---

# 38. RC 范围

建议：

```text
Week 23
```

要求：

```text
Full Docker Demo

Playwright Green

Security Regression Green

Migration Green

No Critical Bugs

No Critical CVE
```

---

# 39. Definition of Done — Backend Story

一个后端 Story 完成必须：

```text
SPEC clear

Domain/API implemented

Unit Test

Integration Test

Error Code

Audit if needed

Metrics if needed

No permission hardcoding

README/API docs updated
```

---

# 40. Definition of Done — React Story

必须：

```text
API integrated

Loading

Empty

Error

Permission state

Optimistic conflict

TypeScript strict

Unit test for core logic

No role hardcoding

No permission string
```

---

# 41. Definition of Done — Security Story

必须：

```text
Positive test

Negative test

Bypass test

Cross-tenant test where relevant

Audit

Fail-closed behavior
```

---

# 42. AI Task Template

每次给 AI 的任务建议固定：

```text
Context:
模块 + 当前 SPEC

Goal:
一个明确能力

Constraints:
不得修改哪些模块
不得硬编码权限
Fail Closed
DDD boundary

Deliverables:
代码
测试
SQL
README

Acceptance:
具体测试场景
```

示例：

```text
Context:
Authorization Engine SPEC 13

Goal:
实现 PermissionMergeEngine

Constraints:
纯 Domain
不能依赖 Spring
高 Priority 优先
同 Priority DENY 优先

Deliverables:
Java classes
JUnit5 ParameterizedTest

Acceptance:
20 个冲突场景全部通过
```

---

# 43. AI Code Review Template

每个重要 PR 让 AI Review：

```text
1. 是否存在权限绕过
2. 是否存在跨租户风险
3. 是否 Fail Open
4. 是否破坏幂等
5. 是否存在并发竞态
6. 是否存在长事务
7. 是否跨服务直查 DB
8. 是否出现 permission string
9. 是否缺测试
10. 是否破坏 DDD dependency
```

---

# 44. AI 不应该自动决定的事项

以下必须由开发者最终决定：

```text
Domain Boundary

Security Semantics

Permission Priority

Database Ownership

Distributed Transaction Boundary

Breaking API Change

Sensitive Data Handling

Production Secret

Production Deployment
```

AI 可以建议，但不能无审查接受。

---

# 45. Git Branch Strategy

一人项目不建议复杂 Git Flow。

推荐：

```text
main
feature/*
fix/*
```

每个 Story：

```text
feature/auth-refresh-token
feature/data-permission
```

完成后：

```text
PR → CI → main
```

即使一个人，也建议走 PR。

可以让 AI Review Diff。

---

# 46. Commit Strategy

每个 Commit 尽量：

```text
单一目的
可编译
测试通过
```

例如：

```text
feat(auth): add rotating refresh token
test(auth): add refresh reuse detection tests
```

不要：

```text
update project
```

---

# 47. ADR

关键架构决策写：

```text
docs/adr/
```

例如：

```text
0001-no-permission-string.md
0002-local-outbox.md
0003-share-projection.md
0004-short-lived-jwt-session.md
0005-fail-closed-data-permission.md
```

避免数月后忘记：

```text
为什么这样设计
```

---

# 48. 项目管理看板

建议只使用四列：

```text
BACKLOG
READY
DOING
DONE
```

一个人同时：

```text
DOING <= 2
```

避免 AI 让你“同时开 20 个模块”。

---

# 49. Story 粒度

优秀 Story：

```text
0.5~2 天
```

例如：

```text
实现 Refresh Token Rotation
```

过大：

```text
实现 Auth Service
```

应该继续拆。

---

# 50. 优先级规则

始终：

```text
Security Critical
>
Core Vertical Slice
>
Integration
>
Admin UX
>
Advanced Feature
```

---

# 51. 不允许提前优化的内容

在没有真实压测前，不要提前：

```text
全表分库分表

Redis Cluster 本地开发

Kafka 替代 RabbitMQ

Kubernetes

ClickHouse

复杂 ABAC

Service Mesh
```

这些都会拖延核心授权闭环。

---

# 52. 一人项目避免过度微服务

虽然 V1 架构按微服务设计，但开发阶段允许：

```text
统一仓库
统一 Maven Reactor
一个 MySQL 实例多个 Schema
统一 Docker Compose
```

降低维护成本。

不建议：

```text
每个服务独立仓库
```

一个人会被版本管理拖死。

---

# 53. Monorepo

V1 正式采用：

```text
Monorepo
```

包含：

```text
Java backend
React admin
deploy
docs
SQL
tests
```

---

# 54. 后端优先还是前端优先

正式顺序：

```text
Backend Domain
↓
Backend API
↓
Integration Test
↓
React
```

不要先做漂亮 UI 再发现：

```text
权限模型根本不成立
```

---

# 55. Mock 策略

允许 Mock：

```text
Week 4 Gateway → Authorization

Week 10 DataPermission → Shared ACL
```

但是 Mock 必须有明确：

```text
替换截止周
```

不能长期存在。

---

# 56. 真实组件优先

以下必须尽早真实：

```text
MySQL
Redis
RabbitMQ
```

不要长期：

```text
H2
fake Redis
fake MQ
```

因为很多关键 Bug 只在真实基础设施出现。

---

# 57. 每周集成夜

建议每周五：

```text
Full Infrastructure
+
all current services
+
React
```

跑一次：

```text
Smoke + Core E2E
```

避免到 Week 23 才发现服务无法一起启动。

---

# 58. 每四周架构复盘

Week：

```text
4
8
12
16
20
```

进行：

```text
Architecture Review
```

检查：

```text
模块依赖
表归属
同步 RPC 数量
事件数量
权限硬编码
技术债
```

---

# 59. 技术债预算

每周预留：

```text
10%~15%
```

用于：

```text
重构
测试修复
依赖升级
文档
```

如果全部时间只开发新 Feature：

最终会在 RC 阶段一次性爆炸。

---

# 60. Bug 优先级

P0：

```text
Cross Tenant Leakage
Authorization Bypass
Sensitive Field Leakage
Privilege Escalation
Token Compromise
```

立即停止 Feature 开发修复。

P1：

```text
Idempotency Failure
Projection Incorrect
Refresh Broken
Critical API Broken
```

本 Sprint 修复。

---

# 61. Weekly Exit Criteria

每周结束必须：

```text
main build green
unit tests green
integration tests green for touched module
no known P0
README/spec updated
```

否则：

```text
下周不直接开始新模块
```

先补齐。

---

# 62. 项目总风险

## 风险 1：SPEC 太大

解决：

```text
严格 V1 范围
Advanced Feature 延后
```

## 风险 2：AI 生成过多不可维护代码

解决：

```text
小 Story
人工 Review
Architecture Test
```

## 风险 3：跨服务调用过多

解决：

```text
Read Model / Projection
Batch API
```

## 风险 4：权限逻辑散落

解决：

```text
Authorization Engine
Starter
CI Hardcoding Scan
```

## 风险 5：测试拖到最后

解决：

```text
每周集成
TDD Core Domain
```

---

# 63. 一人 + AI 最重要原则

不要追求：

```text
每天写最多代码
```

而是追求：

```text
每天关闭一个风险
```

例如：

```text
今天证明 Refresh 并发安全

明天证明跨 Tenant 不泄漏

后天证明 Share 过期不依赖 Job
```

这种进度才真正接近可上线系统。

---

# 64. 推荐实际工作量

24 周约：

```text
120 工作日
```

预估：

```text
Architecture / SPEC       10~15 days

Backend Core              50~55 days

React                     20~25 days

Integration / Testing     20~25 days

Deployment / Docs         10 days
```

合计约：

```text
110~130 人日
```

使用 AI 后能显著降低：

```text
Boilerplate
CRUD
Test skeleton
Documentation
```

但不会把：

```text
安全建模
联调
故障排查
验收
```

压缩到零。

---

# 65. 推荐最终排期

如果目标是：

```text
真正可以放到简历
可以演示
可以进行技术面试
代码架构完整
核心安全可信
```

正式建议：

# 24 周 V1.0

不要硬压到：

```text
4~6 周
```

4~6 周最多只能得到：

```text
Demo
```

而不是这个 SPEC 定义的企业级平台。

---

# 66. 项目完成标志

项目不是因为：

```text
所有页面能点
```

就完成。

正式 V1.0 Completion Criteria：

```text
Authentication Complete

Authorization Complete

Data Permission Complete

Field Permission Complete

Cross-Team ACL Complete

Immediate Revocation Complete

Idempotency Complete

MQ / Outbox Complete

Job Idempotency Complete

React Admin Complete

Audit / Explain Complete

Core E2E Green

Security Regression Green

Docker Full Environment Green

Backup/Restore Verified

Documentation Complete
```

---

# 67. V1.0 最终交付目录

最终交付应该包含：

```text
enterprise-iam/
├── backend/
├── frontend/
├── deploy/
├── docs/
├── scripts/
├── tests/
├── README.md
├── CHANGELOG.md
└── LICENSE
```

SPEC：

```text
docs/spec/
01-overview.md
02-domain-model.md
...
22-deployment.md
23-solo-developer-ai-plan.md
24-final-repository-freeze.md
```

---

# 68. 下一阶段

下一阶段：

# 24 — Final Repository & SPEC Freeze

最终完成：

```text
完整 Maven Module

完整 Java Package

完整 React Directory

所有 Database Ownership

所有 Flyway Migration Directory

所有 Docker Directory

所有 Test Directory

所有 SPEC 文档编号

所有 ADR

开发顺序

Module Dependency Graph

Service Dependency Graph

最终 V1 / V1.1 范围

Code Generation Checklist
```

`24` 完成之后：

```text
SPEC 阶段正式冻结
```

下一步直接进入：

```text
Phase 1
生成 Maven 多模块工程

Phase 2
生成 Flyway DDL

Phase 3
生成 Framework Starter

Phase 4
生成 Auth / Authorization Vertical Slice

Phase 5
生成 React Admin

Phase 6
生成 Docker Compose

Phase 7
最终 ZIP
```

---

# 69. 最终结论

对于本项目：

```text
一人
+
AI
```

是可执行的。

但必须遵守：

```text
Monorepo

小步 Vertical Slice

Backend First

Security First

TDD Core Rules

Weekly Integration

No Permission Hardcoding

No Big-Bang Code Generation

No Feature Expansion After Beta
```

推荐周期：

```text
MVP       10~12 周
Beta      16 周
RC        23 周
V1.0      24 周
```

这是本项目正式的一人 + AI 实施基线。
