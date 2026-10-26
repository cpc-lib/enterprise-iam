# Enterprise IAM 基础中间件与数据基础设施部署指南

本文档指导在本地开发与测试环境中，使用 Docker Compose 一键启动 Enterprise IAM 平台所需的全部基础设施服务（包括 MySQL、Redis、RabbitMQ、MinIO、Nacos 以及 Seata 分布式事务协调器）。

---

## 1. 基础环境架构组成（全量服务清单）

`deploy/docker-compose.yml` 编排了支撑 8 大微服务集群所需的全部 7 个核心基础容器服务：

| 服务名称 | 容器名 | 镜像版本 | 宿主机端口 | 默认认证 (User / Pass) | 说明与核心用途 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MySQL 8.4** | `iam-mysql` | `mysql:8.4` | `3306` | `root` / `root` | 核心关系数据库，首次启动自动初始化 8 大微服务及 Seata 隔离库与 `utf8mb4` 字符集 |
| **Redis 7** | `iam-redis` | `redis:7-alpine` | `6379` | *无密码 (开发默认)* | 开启 AOF 持久化，支撑网关 Access Token 投影、单调递增会话版本栅栏与分布式幂等锁 |
| **RabbitMQ** | `iam-rabbitmq` | `rabbitmq:3.13-management-alpine` | `5672`<br>`15672` | `guest` / `guest` | 领域事件总线（Exchange: `iam.domain.events`），提供 Web 管理控制台 |
| **MinIO** | `iam-minio` | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | `9000`<br>`9001` | `minioadmin` / `minioadmin` | S3 兼容对象存储，支撑大文件分块直传、秒传去重与审计 Parquet 冷归档 |
| **MinIO Init** | `iam-minio-init`| 复用 `minio/minio` (内置 `/usr/bin/mc`) | *(无外部端口)* | - | 伴随容器，自动创建 `iam-files` 与 `iam-audit-cold-archive` 存储桶并退出 |
| **Nacos** | `iam-nacos` | `nacos/nacos-server:v2.3.2` | `8848`<br>`9848` | `nacos` / `nacos` | Standalone 单机模式服务注册与配置发现中心 |
| **Seata Server** | `iam-seata-server` | `seataio/seata-server:1.7.0` | `8091`<br>`7091` | - | 分布式事务协调器（TC），支撑微服务跨库强一致事务与 AT 模式协调 |

---

## 2. 数据库自动初始化清单

MySQL 容器在首次启动时，会自动执行 `mysql/init/01-init-databases.sql` 脚本，预创建全部专用物理数据库：

```sql
CREATE DATABASE IF NOT EXISTS `iam_auth`;         -- 认证凭据与登录会话 (iam-auth-service)
CREATE DATABASE IF NOT EXISTS `iam_identity`;     -- 租户、用户、角色、菜单、数据字典 (iam-identity-service)
CREATE DATABASE IF NOT EXISTS `iam_organization`; -- 组织架构、部门树、岗位、团队 (iam-organization-service)
CREATE DATABASE IF NOT EXISTS `iam_authorization`;-- 统一授权规则、数据范围、字段权限 (iam-authorization-service)
CREATE DATABASE IF NOT EXISTS `iam_sharing`;      -- 跨团队细粒度资源共享与 ACL (iam-sharing-service)
CREATE DATABASE IF NOT EXISTS `iam_file`;         -- 文件元数据、断点分片直传 (iam-file-service)
CREATE DATABASE IF NOT EXISTS `iam_audit`;        -- 全链路审计日志与合规流水 (iam-audit-service)
CREATE DATABASE IF NOT EXISTS `iam_job`;          -- 分布式定时调度任务与心跳 (iam-job-service)
CREATE DATABASE IF NOT EXISTS `nacos_config`;     -- Nacos 配置持久化库
CREATE DATABASE IF NOT EXISTS `seata`;            -- Seata 分布式事务全局锁与分支事务库
```

> **提示**：各业务微服务在启动时，其内置的 Flyway 迁移模块会自动在各自数据库中执行 `V<N>__*.sql` 脚本，创建全部数据表与索引结构。

---

## 3. 一键启动所有基础服务

### 方式 A：使用 PowerShell 自动化脚本（推荐）

在项目根目录或 `deploy` 目录下打开 PowerShell 执行：

```powershell
# 1. 一键启动 docker-compose.yml 中的所有 service
powershell -ExecutionPolicy Bypass -File deploy/scripts/start-infra.ps1

# 2. 检查所有容器运行状态与 TCP 端口探测健康度
powershell -ExecutionPolicy Bypass -File deploy/scripts/check-infra.ps1

# 3. 停止所有容器服务
powershell -ExecutionPolicy Bypass -File deploy/scripts/stop-infra.ps1

# 4. 停止并永久清空数据卷（如需彻底重置环境到出厂状态）
powershell -ExecutionPolicy Bypass -File deploy/scripts/stop-infra.ps1 -RemoveVolumes
```

### 方式 B：使用标准 Docker Compose 原生命令

```powershell
# 进入部署目录
cd deploy

# 启动 docker-compose.yml 中的所有 service 并在后台运行
docker compose up -d

# 查看全部容器运行状态
docker compose ps

# 查看全部服务的实时日志
docker compose logs -f

# 查看指定服务的实时日志
docker compose logs -f mysql redis rabbitmq minio nacos seata-server

# 停止全部容器
docker compose down
```

---

## 4. 本地 Spring Boot 微服务连接参数参考

各微服务在本地开发时，只需按如下标准参数接入已启动的中间件集群：

```properties
# MySQL 连接（以 iam-identity-service 为例）
DB_URL=jdbc:mysql://localhost:3306/iam_identity?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=root

# Redis 缓存与分布式会话
spring.data.redis.host=localhost
spring.data.redis.port=6379

# RabbitMQ 消息总线
IAM_MQ_HOST=localhost
IAM_MQ_PORT=5672
IAM_MQ_USERNAME=guest
IAM_MQ_PASSWORD=guest

# MinIO 对象存储
IAM_FILE_PURGE_MINIO_ENDPOINT=http://localhost:9000
IAM_FILE_PURGE_MINIO_ACCESS_KEY=minioadmin
IAM_FILE_PURGE_MINIO_SECRET_KEY=minioadmin

# Nacos 配置与注册中心
spring.cloud.nacos.server-addr=localhost:8848
spring.cloud.nacos.username=nacos
spring.cloud.nacos.password=nacos

# Seata 分布式事务协调器 (当微服务启用 seata.enabled=true 时)
seata.service.grouplist.default_tx_group=127.0.0.1:8091
seata.tx-service-group=default_tx_group
```

---

## 5. Web 控制台访问入口全量清单

服务启动完毕后，可通过浏览器直接访问以下管理控制台：

- **RabbitMQ 管理控制台**：[http://localhost:15672](http://localhost:15672)（账号：`guest` / 密码：`guest`）
- **MinIO 对象存储控制台**：[http://localhost:9001](http://localhost:9001)（账号：`minioadmin` / 密码：`minioadmin`）
- **Nacos 服务治理控制台**：[http://localhost:8848/nacos](http://localhost:8848/nacos)（账号：`nacos` / 密码：`nacos`）
- **Seata Server HTTP 控制台**：[http://localhost:7091](http://localhost:7091)

---

## 6. 后端全量微服务与网关启动指引

基础中间件（MySQL、Redis、RabbitMQ、MinIO 等）就绪后，可通过以下命令一键拉起或停止后端全部 8 大微服务及网关（端口 8080 - 8088）：

### 启动全部微服务与网关
```powershell
powershell deploy/scripts/start-all-services.ps1
```

### 停止全部微服务与网关
```powershell
powershell deploy/scripts/stop-all-services.ps1
```
