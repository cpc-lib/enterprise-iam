#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Enterprise IAM - 超级系统管理员 (superadmin) 应急密码重置脚本

使用说明:
  python reset-superadmin-password.py [--password <新密码>] [--host <DB主机>] [--port <端口>] [--user <用户>] [--db-password <DB密码>]

参数示例:
  python reset-superadmin-password.py
  python reset-superadmin-password.py --password "admin123"
"""

import argparse
import sys
import subprocess
import unicodedata
from datetime import datetime

# 解决 Windows 控制台输出编码问题
if sys.platform == "win32":
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")
    except Exception:
        pass

try:
    import argon2
except ImportError:
    print("[ERROR] Missing argon2 dependency. Please install via: pip install argon2-cffi")
    sys.exit(1)

# Enterprise IAM 架构规范 (SECURITY-PARAMETERS.yaml & SPEC47) Argon2id 冻结参数:
ARGON2_TIME_COST = 2
ARGON2_MEMORY_COST = 19456  # KiB (19 MiB)
ARGON2_PARALLELISM = 1
ARGON2_HASH_LEN = 32
ARGON2_SALT_LEN = 16

SUPERADMIN_TENANT_ID = 10001
SUPERADMIN_USER_ID = 100001


def generate_argon2id_hash(raw_password: str) -> str:
    """生成与后端 Argon2idPasswordVerifier 严格一致的 PHC 密文"""
    normalized = unicodedata.normalize("NFC", raw_password)
    hasher = argon2.PasswordHasher(
        time_cost=ARGON2_TIME_COST,
        memory_cost=ARGON2_MEMORY_COST,
        parallelism=ARGON2_PARALLELISM,
        hash_len=ARGON2_HASH_LEN,
        salt_len=ARGON2_SALT_LEN,
        type=argon2.Type.ID,
    )
    return hasher.hash(normalized)


def reset_via_pymysql(host, port, user, password, phc_hash) -> bool:
    """优先使用 pymysql 直连重置"""
    try:
        import pymysql
    except ImportError:
        return False

    try:
        conn = pymysql.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            database="iam_auth",
            charset="utf8mb4",
            autocommit=False,
        )
        with conn.cursor() as cur:
            now = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

            # 1. 更新凭据表
            cur.execute(
                """
                INSERT INTO iam_password_credential
                    (id, tenant_id, user_id, password_phc, password_version, status, changed_at, version, created_at, updated_at)
                VALUES
                    (%s, %s, %s, %s, 1, 'ACTIVE', %s, 0, %s, %s)
                ON DUPLICATE KEY UPDATE
                    password_phc = VALUES(password_phc),
                    password_version = password_version + 1,
                    status = 'ACTIVE',
                    changed_at = %s,
                    version = version + 1,
                    updated_at = %s
                """,
                (
                    SUPERADMIN_USER_ID,
                    SUPERADMIN_TENANT_ID,
                    SUPERADMIN_USER_ID,
                    phc_hash,
                    now,
                    now,
                    now,
                    now,
                    now,
                ),
            )

            # 2. 解锁并重置安全状态
            cur.execute(
                """
                INSERT INTO iam_user_security_state
                    (id, tenant_id, user_id, token_version, password_version, login_failure_count, failure_window_start_at, lock_until, last_password_change_at, version, created_at, updated_at)
                VALUES
                    (%s, %s, %s, 1, 1, 0, NULL, NULL, %s, 0, %s, %s)
                ON DUPLICATE KEY UPDATE
                    token_version = token_version + 1,
                    password_version = password_version + 1,
                    login_failure_count = 0,
                    failure_window_start_at = NULL,
                    lock_until = NULL,
                    last_password_change_at = %s,
                    version = version + 1,
                    updated_at = %s
                """,
                (
                    SUPERADMIN_USER_ID,
                    SUPERADMIN_TENANT_ID,
                    SUPERADMIN_USER_ID,
                    now,
                    now,
                    now,
                    now,
                    now,
                ),
            )

            # 3. 强制失效当前所有活跃会话与 Refresh Token
            cur.execute(
                """
                UPDATE iam_login_session
                SET status = 'REVOKED', revoked_at = %s, revoke_reason = 'SUPERADMIN_EMERGENCY_RESET',
                    session_version = session_version + 1, updated_at = %s
                WHERE tenant_id = %s AND user_id = %s AND status = 'ACTIVE'
                """,
                (now, now, SUPERADMIN_TENANT_ID, SUPERADMIN_USER_ID),
            )
            cur.execute(
                """
                UPDATE iam_refresh_token
                SET status = 'REVOKED', revoked_at = %s, revoke_reason = 'SUPERADMIN_EMERGENCY_RESET',
                    updated_at = %s
                WHERE tenant_id = %s AND user_id = %s AND status = 'ACTIVE'
                """,
                (now, now, SUPERADMIN_TENANT_ID, SUPERADMIN_USER_ID),
            )

        conn.commit()
        conn.close()
        return True
    except Exception as e:
        print(f"[WARN] MySQL direct connection failed: {e}. Trying Docker fallback...")
        return False


def reset_via_docker(phc_hash) -> bool:
    """回退通道：通过 Docker 容器 iam-mysql 执行 SQL"""
    sql = f"""
    SET @now = NOW(3);
    INSERT INTO iam_password_credential
        (id, tenant_id, user_id, password_phc, password_version, status, changed_at, version, created_at, updated_at)
    VALUES
        ({SUPERADMIN_USER_ID}, {SUPERADMIN_TENANT_ID}, {SUPERADMIN_USER_ID}, '{phc_hash}', 1, 'ACTIVE', @now, 0, @now, @now)
    ON DUPLICATE KEY UPDATE
        password_phc = VALUES(password_phc),
        password_version = password_version + 1,
        status = 'ACTIVE',
        changed_at = @now,
        version = version + 1,
        updated_at = @now;

    INSERT INTO iam_user_security_state
        (id, tenant_id, user_id, token_version, password_version, login_failure_count, failure_window_start_at, lock_until, last_password_change_at, version, created_at, updated_at)
    VALUES
        ({SUPERADMIN_USER_ID}, {SUPERADMIN_TENANT_ID}, {SUPERADMIN_USER_ID}, 1, 1, 0, NULL, NULL, @now, 0, @now, @now)
    ON DUPLICATE KEY UPDATE
        token_version = token_version + 1,
        password_version = password_version + 1,
        login_failure_count = 0,
        failure_window_start_at = NULL,
        lock_until = NULL,
        last_password_change_at = @now,
        version = version + 1,
        updated_at = @now;

    UPDATE iam_login_session
    SET status = 'REVOKED', revoked_at = @now, revoke_reason = 'SUPERADMIN_EMERGENCY_RESET',
        session_version = session_version + 1, updated_at = @now
    WHERE tenant_id = {SUPERADMIN_TENANT_ID} AND user_id = {SUPERADMIN_USER_ID} AND status = 'ACTIVE';

    UPDATE iam_refresh_token
    SET status = 'REVOKED', revoked_at = @now, revoke_reason = 'SUPERADMIN_EMERGENCY_RESET', updated_at = @now
    WHERE tenant_id = {SUPERADMIN_TENANT_ID} AND user_id = {SUPERADMIN_USER_ID} AND status = 'ACTIVE';
    """
    try:
        subprocess.run(
            ["docker", "exec", "-i", "iam-mysql", "mysql", "-uroot", "-proot", "iam_auth"],
            input=sql,
            text=True,
            capture_output=True,
            check=True,
        )
        return True
    except Exception as e:
        print(f"[ERROR] Docker execution failed: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Enterprise IAM - Superadmin Emergency Password Reset Tool")
    parser.add_argument(
        "--password",
        "-p",
        default="admin123",
        help="Specify new password for superadmin (default: admin123)",
    )
    parser.add_argument("--host", default="localhost", help="MySQL host (default: localhost)")
    parser.add_argument("--port", type=int, default=3306, help="MySQL port (default: 3306)")
    parser.add_argument("--user", default="root", help="MySQL username (default: root)")
    parser.add_argument("--db-password", default="root", help="MySQL password (default: root)")

    args = parser.parse_args()
    new_password = args.password.strip()

    if len(new_password) < 6 or len(new_password) > 128:
        print(f"[ERROR] Password length must be between 6 and 128 characters (current: {len(new_password)})")
        sys.exit(1)

    print("==================================================================")
    print(" Enterprise IAM - 超级系统管理员应急密码重置工具")
    print(" Superadmin Emergency Password Reset Tool")
    print("==================================================================")
    print(f" 目标账号 (Target): superadmin (User ID: {SUPERADMIN_USER_ID}, Tenant ID: {SUPERADMIN_TENANT_ID})")
    print(f" 设定密码 (Password): {new_password}")
    print(" 正在计算 Argon2id (m=19456, t=2, p=1) 安全哈希...")

    phc_hash = generate_argon2id_hash(new_password)

    success = reset_via_pymysql(args.host, args.port, args.user, args.db_password, phc_hash)
    if not success:
        print(" [INFO] 正在尝试使用 Docker 容器回退通道...")
        success = reset_via_docker(phc_hash)

    if success:
        print("------------------------------------------------------------------")
        print(" [SUCCESS] 超级管理员密码重置成功！")
        print(" 【登录页面凭据】")
        print("   - 租户编码 (Tenant Code): system")
        print("   - 登录方式 (Login Type) : 邮箱 或 用户名")
        print("   - 登录账号 (Identity)   : superadmin@enterprise.com 或 superadmin")
        print(f"   - 登录密码 (Password)   : {new_password}")
        print("   - 安全状态 (Status)     : 已解除账户锁定，重试计数已归零")
        print("   - 会话状态 (Sessions)   : 历史所有 Token 与 Session 已强制失效")
        print("==================================================================")
    else:
        print("------------------------------------------------------------------")
        print(" [FAILED] 无法连接到 MySQL 数据库，请检查数据库服务或 Docker 容器状态。")
        print("==================================================================")
        sys.exit(1)


if __name__ == "__main__":
    main()
