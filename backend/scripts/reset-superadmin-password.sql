-- ====================================================================
-- Enterprise IAM - 超级系统管理员 (superadmin) 应急密码重置 SQL
-- 适用场景: 运维人员直接在 MySQL 客户端 / Navicat / Docker 中执行
-- 默认重置密码: admin123
-- 对应 Argon2id (m=19456, t=2, p=1) PHC 密文:
-- $argon2id$v=19$m=19456,t=2,p=1$3HS7jcf06tffhMkOqJ9ZXQ$BSLENc6lno681aIk7nMzTyX4fmpRVMJ2U+Xs4rHwjU4
-- ====================================================================

USE iam_auth;

SET @tenant_id = 10001;
SET @user_id = 100001;
SET @now = NOW(3);
-- admin123 的标准 Argon2id 密文
SET @admin123_phc = '$argon2id$v=19$m=19456,t=2,p=1$3HS7jcf06tffhMkOqJ9ZXQ$BSLENc6lno681aIk7nMzTyX4fmpRVMJ2U+Xs4rHwjU4';

-- 1. 更新凭据表为 admin123 并激活
INSERT INTO iam_password_credential
    (id, tenant_id, user_id, password_phc, password_version, status, changed_at, version, created_at, updated_at)
VALUES
    (@user_id, @tenant_id, @user_id, @admin123_phc, 1, 'ACTIVE', @now, 0, @now, @now)
ON DUPLICATE KEY UPDATE
    password_phc = @admin123_phc,
    password_version = password_version + 1,
    status = 'ACTIVE',
    changed_at = @now,
    version = version + 1,
    updated_at = @now;

-- 2. 解除防爆锁定，失败次数清零，推进版本号
INSERT INTO iam_user_security_state
    (id, tenant_id, user_id, token_version, password_version, login_failure_count, failure_window_start_at, lock_until, last_password_change_at, version, created_at, updated_at)
VALUES
    (@user_id, @tenant_id, @user_id, 1, 1, 0, NULL, NULL, @now, 0, @now, @now)
ON DUPLICATE KEY UPDATE
    token_version = token_version + 1,
    password_version = password_version + 1,
    login_failure_count = 0,
    failure_window_start_at = NULL,
    lock_until = NULL,
    last_password_change_at = @now,
    version = version + 1,
    updated_at = @now;

-- 3. 强制吊销活跃会话与历史 Refresh Token
UPDATE iam_login_session
SET status = 'REVOKED', revoked_at = @now, revoke_reason = 'SUPERADMIN_EMERGENCY_RESET',
    session_version = session_version + 1, updated_at = @now
WHERE tenant_id = @tenant_id AND user_id = @user_id AND status = 'ACTIVE';

UPDATE iam_refresh_token
SET status = 'REVOKED', revoked_at = @now, revoke_reason = 'SUPERADMIN_EMERGENCY_RESET', updated_at = @now
WHERE tenant_id = @tenant_id AND user_id = @user_id AND status = 'ACTIVE';

SELECT 'SUCCESS: superadmin password has been reset to [admin123]' AS result;
