-- ==============================================================================
-- Enterprise IAM Platform 基础环境初始化脚本
-- 自动初始化 8 大微服务专用隔离数据库及权限配置
-- ==============================================================================

-- 1. 认证中心核心库 (iam-auth-service)
CREATE DATABASE IF NOT EXISTS `iam_auth` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 2. 身份主数据与租户治理库 (iam-identity-service)
CREATE DATABASE IF NOT EXISTS `iam_identity` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 3. 组织机构与岗位协作库 (iam-organization-service)
CREATE DATABASE IF NOT EXISTS `iam_organization` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 4. 统一授权决策引擎库 (iam-authorization-service)
CREATE DATABASE IF NOT EXISTS `iam_authorization` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 5. 跨团队细粒度资源共享库 (iam-sharing-service)
CREATE DATABASE IF NOT EXISTS `iam_sharing` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 6. 分块断点续传文件管控库 (iam-file-service)
CREATE DATABASE IF NOT EXISTS `iam_file` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 7. 全链路操作与安全审计库 (iam-audit-service)
CREATE DATABASE IF NOT EXISTS `iam_audit` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 8. 分布式任务调度中心库 (iam-job-service)
CREATE DATABASE IF NOT EXISTS `iam_job` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 9. Nacos 配置中心库 (可选 standalone)
CREATE DATABASE IF NOT EXISTS `nacos_config` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 10. Seata 分布式事务协调库 (可选插件)
CREATE DATABASE IF NOT EXISTS `seata` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 授权 root 用户远程连接与全量库权限
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
