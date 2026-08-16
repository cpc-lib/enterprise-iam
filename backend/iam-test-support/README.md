# iam-test-support

跨服务测试夹具。`MySqlIntegrationDatabase` 为 auth、identity 和
authorization 的 Flyway/Failsafe 套件提供隔离的 MySQL 数据库。

外部数据库配置按以下优先级解析：

1. `IAM_TEST_<SERVICE>_MYSQL_JDBC_URL`
2. `IAM_TEST_MYSQL_JDBC_URL_TEMPLATE`，且必须恰好包含一个 `{database}`
3. 仅 auth 兼容旧变量 `IAM_TEST_MYSQL_JDBC_URL`
4. 未配置时启动固定镜像 `mysql:8.4.9`

服务专属凭据为 `IAM_TEST_<SERVICE>_MYSQL_USERNAME` / `PASSWORD`，通用回退
为 `IAM_TEST_MYSQL_USERNAME` / `PASSWORD`。支持的 `<SERVICE>` 为当前调用方
传入的安全服务键；现有套件使用 `AUTH`、`IDENTITY` 和 `AUTHORIZATION`。

外部 CI 应为三个服务提供独立 schema，或使用 URL 模板自动替换为
`iam_auth`、`iam_identity` 和 `iam_authorization`，避免 Flyway 历史表互相污染。
