# Enterprise IAM 应急运维脚本集

## 超级系统管理员（superadmin）应急密码重置脚本

当超级系统管理员忘记登录密码、无法登录后台，或触发账户安全风控锁定时，可直接在服务端主机运行本脚本进行**离线一键重置与解锁**。

### 核心功能
1. **密码重置**：重置目标用户 `superadmin`（`tenant_id: 10001, user_id: 100001`）。
2. **默认密码**：默认一键重置为 **`admin123`**（也可通过参数指定任意自定义密码）。
3. **合规加密**：严格遵循 Enterprise IAM 安全基线（`SECURITY-PARAMETERS.yaml`），采用 Argon2id（`m=19456 KiB, t=2, p=1, hash_len=32, salt_len=16`）生成单向密文。
4. **解锁防爆**：清空连续登录失败计数（`login_failure_count = 0`），清除账户锁定期（`lock_until = NULL`），递增凭据与安全状态版本号。
5. **会话清退**：原子性强制吊销该账号名下当前所有的在线登录会话（`iam_login_session`）与刷新凭据（`iam_refresh_token`），保障账户重置后的安全性。

---

### 使用方法

#### 方式一：Windows PowerShell（推荐）
```powershell
# 1. 默认一键重置为 admin123 并自动解锁
powershell -File backend/scripts/reset-superadmin-password.ps1

# 2. 或指定自定义密码
powershell -File backend/scripts/reset-superadmin-password.ps1 -Password "YourCustomPassword123!"
```

#### 方式二：Windows 双击批处理
直接双击运行 `backend\scripts\reset-superadmin-password.bat`。

#### 方式三：纯 SQL 客户端直接执行（无需 Python/PowerShell）
使用任意 MySQL 客户端（Navicat / DBeaver / Docker）打开并执行：
`backend/scripts/reset-superadmin-password.sql`

#### 方式四：Linux / macOS 环境 (Bash)
```bash
chmod +x backend/scripts/reset-superadmin-password.sh
./backend/scripts/reset-superadmin-password.sh
```

---

### 登录凭据确认
- **租户编码**：`system`
- **登录方式**：`邮箱` 或 `用户名`
- **登录账号**：`superadmin@enterprise.com` 或 `superadmin`
- **登录密码**：`admin123`
