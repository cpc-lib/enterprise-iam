# Enterprise IAM & Dynamic Authorization Platform — CODE-READY 1.10.1 CONTRACT HARDENING

企业级 IAM + 动态授权平台 Monorepo 项目骨架。

## 目录

- `backend/`：Java / Spring Cloud Alibaba 微服务
- `frontend/iam-admin-ui/`：React + TypeScript 管理端
- `docs/spec/`：SPEC 01~46
- `docs/architecture/`：项目架构、运行时架构、服务依赖
- `docs/adr/`：关键架构决策
- `deploy/`：Docker Compose / 环境变量 / Windows PowerShell
- `tests/`：E2E / Security / Performance / Chaos
- `scripts/`：项目级脚本
- `tools/`：开发辅助工具

> 当前包是“CODE-READY SPEC + 后端工程底座 + Phase-01 安全核心”交付，
> 不是完整业务代码实现。V1.10 在 1.9 Outbox 基础上实现真实 JDBC 登录会话
> 事务：安全版本锁、并发会话上限、会话/刷新 HMAC/投影事件原子提交，以及
> access 签名失败全回滚。V1.10.1 修复响应方向、ID 值域、Cookie、错误码和
> 登录重试合同，并补充真正的同时签发测试源及内容哈希门禁。生产 KMS/HSM、
> 登录 HTTP/Cookie 适配器、刷新轮换、
> 撤销/禁用/过期业务事务与真实端到端链路仍未完成。

## Business Closed Loop

V1.0 业务闭环定义见 `docs/spec/25-business-closed-loop-end-to-end-workflow.md`。

## Final authority

SPEC 38 is the Core V1 authorization machine-contract authority, SPEC 39 is the
build/toolchain/Reactor authority, SPEC 40 governs the initial Phase-01 security
core, SPEC 41 governs authentication/delegation crypto, and SPEC 42 governs key
rotation/service wiring. SPEC 43 governs Gateway access authentication and the
authoritative session fence. SPEC 44 governs the HTTPS JWKS and Redis session
projection adapters. SPEC 45 governs durable session-projection delivery.
SPEC 46 governs transactional login session and token issuance.
Conflict precedence:

```text
SPEC 46 / V1.10.1 transactional login session and contract hardening
> SPEC 45 / V1.9 session projection transactional outbox
> SPEC 44 / V1.8 HTTPS JWKS and Redis session projection adapters
> SPEC 43 / V1.7 Gateway access authentication and session fence
> SPEC 42 / V1.6 delegation key rotation and service wiring
> SPEC 41 / V1.5 authentication and delegation crypto
> SPEC 40 / V1.4 implemented Phase-01 security core
> SPEC 39 / V1.3 build foundation (build concerns)
> SPEC 38 / V1.2 machine-readable contracts (business behavior)
> SPEC 37 / CODE PHASE 01 contracts
> SPEC 36
> SPEC 24~35
> SPEC 01~23
```

## CODE-READY 1.10.1 快速入口

- `docs/spec/37-code-ready-implementation-contract-freeze.md`
- `docs/spec/38-core-v1-authorization-machine-contract-freeze.md`
- `docs/spec/39-backend-build-foundation-freeze.md`
- `docs/spec/40-code-phase-01-security-core-implementation-freeze.md`
- `docs/spec/41-authentication-and-delegation-crypto-implementation-freeze.md`
- `docs/spec/42-delegation-key-rotation-and-service-wiring-freeze.md`
- `docs/spec/43-gateway-access-authentication-and-session-fence-freeze.md`
- `docs/spec/44-https-jwks-and-redis-session-projection-freeze.md`
- `docs/spec/45-session-projection-transactional-outbox-freeze.md`
- `docs/spec/46-transactional-login-session-and-token-issuance-freeze.md`
- `backend/pom.xml`
- `tools/validate_build_foundation.py`
- `tools/validate_phase01_core.py`
- `tools/validate_auth_crypto.py`
- `tools/validate_delegation_wiring.py`
- `tools/validate_access_authentication.py`
- `tools/validate_trust_adapters.py`
- `tools/validate_session_projection_outbox.py`
- `tools/validate_session_issuance.py`
- `.github/workflows/backend-build.yml`
- `docs/api/openapi-code-phase-01.yaml`
- `docs/api/openapi-code-phase-02-policy.yaml`
- `docs/api/openapi-code-phase-03-sharing-file.yaml`
- `docs/events/asyncapi-code-phase-01.yaml`
- `docs/events/asyncapi-code-phase-02-v1.yaml`
- `docs/security/SECURITY-PARAMETERS.yaml`
- `docs/database/code-phase-01/`
- `docs/database/code-phase-02/`
- `docs/architecture/REQUIREMENTS-TRACEABILITY.csv`
- `docs/architecture/QUALITY-ASSESSMENT-1.10.1.md`
- `docs/testing/CODE-PHASE-01-IMPLEMENTATION-EVIDENCE.md`
- `docs/testing/LOCAL-VALIDATION-1.10.1.md`
- `docs/planning/MACHINE-CONTRACT-COVERAGE.md`
- `docs/planning/CODE-PHASE-ENTRY-GATE.md`
- `CODE-READY-1.10.1-CHANGELOG.md`

## 本地验证

```bash
python tools/validate_code_ready_spec.py
python tools/validate_build_foundation.py
python tools/validate_phase01_core.py
python tools/validate_auth_crypto.py
python tools/validate_delegation_wiring.py
python tools/validate_access_authentication.py
python tools/validate_trust_adapters.py
python tools/validate_session_projection_outbox.py
python tools/validate_session_issuance.py
cd backend && mvn -B -ntp verify
```

本地已使用 JDK 21.0.12 和 Maven 3.9.11 完成全部 31 个 Reactor 模块编译
及 160 项 Surefire 测试，全部通过；Gateway 与其余八个运行服务的应用上下文
均已加载。完整 Reactor 的 `mvn -DskipITs verify` 也已通过并生成九个可执行
Spring Boot JAR。三套 Flyway 测试现统一使用服务隔离的双模式 MySQL 夹具；既可
配置服务专属 JDBC URL 或数据库 URL 模板，也可回退到固定版本的 Testcontainers。
此前六项 auth Failsafe 测试已被发现并执行到数据库连接边界；当前夹具增量已用
JDK 21 API 边界桩独立编译并通过解析用例，但因本次环境无法恢复外部 Maven
依赖，新增的五项夹具单测及三服务 Failsafe 尚待 Maven/CI 复跑。
MySQL/Flyway、Redis、容器及端到端 Gate B 证据仍保持开放，详见
`docs/planning/CODE-PHASE-ENTRY-GATE.md`。
